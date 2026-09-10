package com.parvaz.tunnel.core;

import java.io.*;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.regex.*;

/** A partial is only a cache, never an installable result. Caller serializes access,
 * authenticates release metadata and validates the final APK certificate/package.
 * No persisted digest state or server validator is trusted instead of a full hash. */
final class ResumableDownload {
    static final long MAX_BYTES=128L*1024*1024;
    interface Source {HttpURLConnection open(long offset)throws Exception;}
    interface Progress {void changed(int percent);}
    static final class InvalidTransfer extends IOException {
        InvalidTransfer(String code){super(code);}
    }
    private ResumableDownload(){}
    static void fetch(File partial,long expected,String hash,Source source,Progress progress)throws Exception {
        if(expected<=0||expected>MAX_BYTES||hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("INVALID_UPDATE_METADATA");
        try{
            if(partial.length()>expected)throw new InvalidTransfer("UPDATE_PARTIAL_TOO_LARGE");
            if(partial.isFile()&&partial.length()==expected){verifyHash(partial,hash);return;}
            long deadline=System.nanoTime()+300_000_000_000L;
            for(int attempt=0;attempt<2;attempt++){
                long offset=partial.isFile()?partial.length():0;
                HttpURLConnection connection=source.open(offset);
                try{
                    int status=connection.getResponseCode();
                    if(status==416&&offset>0&&attempt==0){erase(partial);continue;}
                    if(status!=200&&status!=206)throw new IOException("HTTP "+status);
                    String encoding=connection.getHeaderField("Content-Encoding");
                    if(encoding!=null&&!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))throw new InvalidTransfer("UPDATE_UNEXPECTED_ENCODING");
                    if(status==200)offset=0; // Server ignored Range: replace, never append a full body.
                    else validateRange(connection.getHeaderField("Content-Range"),offset,expected);
                    long remaining=expected-offset,advertised=connection.getContentLengthLong();
                    if(advertised>=0&&advertised!=remaining)throw new InvalidTransfer("UPDATE_CONTENT_LENGTH_MISMATCH");
                    long done=offset;int last=-1;
                    try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(partial,offset>0)){
                        byte[] buffer=new byte[65536];int n;
                        while(true){
                            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("UPDATE_CANCELLED");
                            if(System.nanoTime()>deadline)throw new InterruptedIOException("UPDATE_TIME_LIMIT");
                            n=input.read(buffer);if(n<0)break;if(n==0)continue;
                            if(n>expected-done)throw new InvalidTransfer("UPDATE_TOO_LARGE");
                            output.write(buffer,0,n);done+=n;
                            int percent=(int)Math.min(99,done*100/expected);
                            if(progress!=null&&percent!=last){last=percent;progress.changed(percent);}
                        }
                        output.flush();output.getFD().sync();
                    }
                    if(done!=expected)throw new EOFException("UPDATE_INCOMPLETE_RETRY");
                    verifyHash(partial,hash);return;
                }finally{connection.disconnect();}
            }
            throw new IOException("UPDATE_RANGE_RETRY_FAILED");
        }catch(InvalidTransfer invalid){erase(partial);throw invalid;}
        // Transport/storage exceptions retain the bounded prefix. Its whole hash is
        // checked at completion, even after a crash, external edit or an ABI change.
    }
    private static void validateRange(String header,long offset,long size)throws InvalidTransfer {
        if(header==null||header.length()>128)throw new InvalidTransfer("UPDATE_INVALID_RANGE");
        Matcher m=Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(header);
        try{
            if(!m.matches()||Long.parseLong(m.group(1))!=offset||Long.parseLong(m.group(2))!=size-1||Long.parseLong(m.group(3))!=size)
                throw new InvalidTransfer("UPDATE_INVALID_RANGE");
        }catch(NumberFormatException overflow){throw new InvalidTransfer("UPDATE_INVALID_RANGE");}
    }
    private static void verifyHash(File file,String expected)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)){
            byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))!=-1)hash.update(bytes,0,n);
        }
        StringBuilder hex=new StringBuilder();for(byte value:hash.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        if(!hex.toString().equals(expected))throw new InvalidTransfer("UPDATE_CHECKSUM_MISMATCH");
    }
    private static void erase(File file)throws IOException {
        if(file.exists()&&!file.delete())throw new IOException("UPDATE_STORAGE_UNAVAILABLE");
    }
}
