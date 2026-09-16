package com.parvaz.tunnel.core;
import android.content.*;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.parvaz.tunnel.config.LinkParser;

/** Content providers are read off the UI thread, with a strict byte/UTF-8 limit. Never opens file://. */
public final class SharedInput {
    private SharedInput(){}
    private static final java.util.concurrent.ThreadPoolExecutor READERS=pool("parvaz-content-read"),CLOSERS=pool("parvaz-content-cancel");
    private static java.util.concurrent.ThreadPoolExecutor pool(String name){return new java.util.concurrent.ThreadPoolExecutor(0,2,20,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<>(),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());}
    public static String read(Context context,Intent intent)throws IOException {
        if(intent==null)return "";
        if(Intent.ACTION_SEND.equals(intent.getAction())){
            CharSequence text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if(text!=null&&text.length()>0){if(text.length()>LinkParser.MAX_INPUT_CHARS)throw new IOException("Input limit");return text.toString();}
            Uri stream=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(stream!=null)return content(context,stream);
            if(intent.getClipData()!=null&&intent.getClipData().getItemCount()==1){Uri uri=intent.getClipData().getItemAt(0).getUri();if(uri!=null)return content(context,uri);}
            return "";
        }
        Uri uri=intent.getData();if(uri==null)return "";
        if("content".equalsIgnoreCase(uri.getScheme()))return content(context,uri);
        if("file".equalsIgnoreCase(uri.getScheme()))throw new IOException("Use document picker");
        String value=uri.toString();if(value.length()>LinkParser.MAX_INPUT_CHARS)throw new IOException("Input limit");return value;
    }
    public static String content(Context context,Uri uri)throws IOException {
        if(!"content".equalsIgnoreCase(uri.getScheme()))throw new IOException("Unsupported URI");
        android.os.CancellationSignal signal=new android.os.CancellationSignal();
        java.util.concurrent.atomic.AtomicReference<Closeable> opened=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.Future<String> task;
        try{task=READERS.submit(()->{
            try(android.content.res.AssetFileDescriptor fd=context.getContentResolver().openAssetFileDescriptor(uri,"r",signal)){
                if(fd==null)throw new IOException("Unavailable file");opened.set(fd);signal.throwIfCanceled();
                try(InputStream in=fd.createInputStream()){opened.set(in);return readUtf8(in);}
            }finally{opened.set(null);}
        });}catch(java.util.concurrent.RejectedExecutionException busy){throw new IOException("Input readers busy",busy);}
        try{return task.get(20,java.util.concurrent.TimeUnit.SECONDS);}
        catch(InterruptedException cancelled){Thread.currentThread().interrupt();throw new InterruptedIOException("Input cancelled");}
        catch(java.util.concurrent.TimeoutException timeout){throw new java.net.SocketTimeoutException("Input deadline");}
        catch(java.util.concurrent.ExecutionException failed){throw new IOException("Input unavailable",failed.getCause());}
        finally{if(!task.isDone()){
            task.cancel(true);
            // A hostile provider may ignore cancellation. Both readers and closers are bounded;
            // callers still return on deadline and cannot accumulate unbounded blocked threads.
            try{CLOSERS.execute(()->{try{signal.cancel();}catch(RuntimeException ignored){}try{Closeable c=opened.getAndSet(null);if(c!=null)c.close();}catch(IOException ignored){}});}catch(java.util.concurrent.RejectedExecutionException busy){}
        }}
    }
    static String readUtf8(InputStream in)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input cancelled");if(n>LinkParser.MAX_INPUT_CHARS-bytes.size())throw new IOException("Input limit");bytes.write(buffer,0,n);}
        return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
}
