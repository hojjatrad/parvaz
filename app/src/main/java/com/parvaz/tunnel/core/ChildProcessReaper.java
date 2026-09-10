package com.parvaz.tunnel.core;
import java.util.concurrent.TimeUnit;
/** Cleanup/permit release is deferred until actual exit, never just destroy().
 * Caller capacity bounds the number of live reapers. No work queue is retained. */
final class ChildProcessReaper {
 static void close(Process process,Runnable released){
  if(process==null){released.run();return;}
  try{process.exitValue();released.run();return;}catch(IllegalThreadStateException alive){}
  process.destroy();
  Thread reaper=new Thread(()->{
   boolean interrupted=false;
   try{
    try{
     long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1500);boolean exited=false;
     while(System.nanoTime()<deadline){try{process.exitValue();exited=true;break;}catch(IllegalThreadStateException alive){}Thread.sleep(25);}
     if(!exited){if(android.os.Build.VERSION.SDK_INT>=26)process.destroyForcibly();else process.destroy();}
    }catch(InterruptedException stop){interrupted=true;process.destroy();}
    while(true)try{process.waitFor();break;}catch(InterruptedException stop){interrupted=true;}
    released.run();
   }finally{if(interrupted)Thread.currentThread().interrupt();}
  },"parvaz-native-reaper");reaper.setDaemon(true);reaper.start();
 }
}
