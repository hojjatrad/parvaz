package com.parvaz.tunnel.core;
import com.parvaz.tunnel.store.BackupInput;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class BackupInputTest {
 @Test public void readsUtf8AndRejectsInvalidBytes()throws Exception {
  assertEquals("پشتیبان",BackupInput.read(new ByteArrayInputStream("پشتیبان".getBytes(StandardCharsets.UTF_8))));
  try{BackupInput.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28}));fail();}catch(IOException expected){}
 }
 @Test public void stopsAtEnvelopeLimit()throws Exception {
  InputStream large=new InputStream(){public int read(){return 32;}public int read(byte[] b,int off,int len){java.util.Arrays.fill(b,off,off+len,(byte)32);return len;}};
  try{BackupInput.read(large);fail();}catch(IOException expected){assertEquals("Backup size limit",expected.getMessage());}
 }
}
