package com.parvaz.tunnel.store;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
/** Bounded UTF-8 envelope reader. The caller owns and closes the provider stream. */
public final class BackupInput {
 private BackupInput(){}
 public static final int MAX_ENVELOPE_BYTES=32*1024*1024;
 public static String read(InputStream input)throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
  while((n=input.read(buffer))!=-1){if(n>MAX_ENVELOPE_BYTES-out.size())throw new IOException("Backup size limit");out.write(buffer,0,n);}
  return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(out.toByteArray())).toString();
 }
}
