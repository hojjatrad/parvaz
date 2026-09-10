package com.parvaz.tunnel.core;
import org.junit.Test;import java.io.*;import java.util.concurrent.*;import static org.junit.Assert.*;
public class ChildProcessReaperTest {
 static class Child extends Process {
  final CountDownLatch exit=new CountDownLatch(1);volatile boolean destroyed;
  public OutputStream getOutputStream(){return new ByteArrayOutputStream();}public InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}public InputStream getErrorStream(){return getInputStream();}
  public int waitFor()throws InterruptedException{exit.await();return 0;}public int exitValue(){if(exit.getCount()>0)throw new IllegalThreadStateException();return 0;}public void destroy(){destroyed=true;}
 }
 @Test public void destroyDoesNotPrematurelyReleasePermitOrDeleteRuntime()throws Exception {Child p=new Child();CountDownLatch released=new CountDownLatch(1);try{ChildProcessReaper.close(p,released::countDown);assertTrue(p.destroyed);assertEquals(1,released.getCount());p.exit.countDown();assertTrue(released.await(3,TimeUnit.SECONDS));}finally{p.exit.countDown();}}
 @Test public void alreadyExitedAndNeverLaunchedCanReleaseImmediately(){Child p=new Child();p.exit.countDown();CountDownLatch released=new CountDownLatch(2);ChildProcessReaper.close(p,released::countDown);ChildProcessReaper.close(null,released::countDown);assertEquals(0,released.getCount());}
}
