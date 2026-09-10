package com.parvaz.tunnel.core;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
public class SerialConnectionQueueTest {
 static void await(CountDownLatch latch){try{assertTrue(latch.await(4,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}}
 static void uninterruptible(CountDownLatch latch){while(latch.getCount()>0)try{latch.await();}catch(InterruptedException ignored){}}
 @Test public void duplicateStartsDoNotAccumulate(){
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger starts=new AtomicInteger();
  try(SerialConnectionQueue q=new SerialConnectionQueue()){
   assertTrue(q.start(false,t->{starts.incrementAndGet();entered.countDown();uninterruptible(release);}));await(entered);
   for(int i=0;i<100;i++)assertFalse(q.start(false,t->starts.incrementAndGet()));assertEquals(0,q.pending());assertEquals(1,starts.get());
  }finally{release.countDown();}
 }
 @Test public void onlyLatestReplacementExecutesAndOldCompletionIsRejected(){
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger chosen=new AtomicInteger();AtomicBoolean staleCommitted=new AtomicBoolean(true);
  try(SerialConnectionQueue q=new SerialConnectionQueue()){
   q.start(false,t->{entered.countDown();uninterruptible(release);staleCommitted.set(q.commit(t,()->chosen.set(-1)));});await(entered);
   for(int i=1;i<=100;i++){final int value=i;q.replace(t->{q.commit(t,()->chosen.set(value));done.countDown();});assertTrue(q.pending()<=1);}
   release.countDown();await(done);assertEquals(100,chosen.get());assertFalse(staleCommitted.get());
  }finally{release.countDown();}
 }
 @Test public void stopWaitsForOwnedResourcesThenNoLateStartCanReviveThem(){
  CountDownLatch entered=new CountDownLatch(1),invalidated=new CountDownLatch(1),release=new CountDownLatch(1),stopped=new CountDownLatch(1);AtomicBoolean running=new AtomicBoolean();
  try(SerialConnectionQueue q=new SerialConnectionQueue()){
   q.start(false,t->{entered.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException e){invalidated.countDown();}q.commit(t,()->running.set(true));});await(entered);
   Thread stop=new Thread(()->{q.stop(()->running.set(false));stopped.countDown();});stop.start();await(invalidated);
   assertEquals(1,stopped.getCount());assertFalse(q.replace(t->running.set(true)));release.countDown();await(stopped);assertFalse(running.get());
  }finally{release.countDown();}
 }
 @Test public void stoppedQueuedRequestsAreDiscarded(){
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger pendingRan=new AtomicInteger();
  try(SerialConnectionQueue q=new SerialConnectionQueue()){
   q.start(false,t->{entered.countDown();uninterruptible(release);});await(entered);q.replace(t->pendingRan.incrementAndGet());
   new Thread(()->{q.stop(()->{});done.countDown();}).start();release.countDown();await(done);assertEquals(0,q.pending());
   // If the replacement raced ahead of STOP it may execute before invalidation;
   // STOP must still finish serially and no request may execute after it returns.
   int atStop=pendingRan.get();assertFalse(q.replace(t->pendingRan.incrementAndGet()));assertEquals(atStop,pendingRan.get());
  }finally{release.countDown();}
 }
 @Test public void oldNetworkRunnableCannotReplaceNewerUserIntent(){
  try(SerialConnectionQueue q=new SerialConnectionQueue()){
   q.start(false,t->{});long old=q.ticket();q.start(true,t->{});
   assertFalse(q.replace(old,t->{throw new AssertionError();}));assertFalse(q.stopIfCurrent(old,()->{throw new AssertionError();}));assertTrue(q.current(q.ticket()));
  }
 }
 @Test public void explicitStartAfterStopIsAllowed(){try(SerialConnectionQueue q=new SerialConnectionQueue()){q.start(false,t->{});q.stop(()->{});CountDownLatch done=new CountDownLatch(1);assertTrue(q.start(false,t->done.countDown()));await(done);}}
 @Test public void closeInvalidatesCallbacksAndRejectsFurtherRequests(){SerialConnectionQueue q=new SerialConnectionQueue();q.start(false,t->{});long old=q.ticket();q.close();assertFalse(q.current(old));assertFalse(q.commit(old,()->{throw new AssertionError();}));assertFalse(q.start(true,t->{}));assertFalse(q.replace(t->{}));}
 @Test public void failureCleanupCanRunInsideTheWorkerWithoutDeadlock(){try(SerialConnectionQueue q=new SerialConnectionQueue()){CountDownLatch done=new CountDownLatch(1);q.start(false,t->{assertTrue(q.stopIfCurrent(t,done::countDown));});await(done);assertFalse(q.current(q.ticket()));}}
}
