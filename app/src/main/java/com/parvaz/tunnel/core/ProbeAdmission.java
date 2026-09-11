package com.parvaz.tunnel.core;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
/** Shared native admission. Waiting is bounded and interruption never releases an unowned permit. */
final class ProbeAdmission {
 static final long BUSY=-3;
 private final Semaphore permits;
 ProbeAdmission(int count){permits=new Semaphore(count,true);}
 boolean enter(boolean wait)throws InterruptedException{return wait?permits.tryAcquire(30,TimeUnit.SECONDS):permits.tryAcquire();}
 void exit(){permits.release();}
}
