package com.parvaz.tunnel.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
public class LatencyResultTest {
 @Test public void unknownAndFailuresAreNotUntested(){assertEquals(LatencyResult.FAILED,LatencyResult.measured(-1));assertEquals(LatencyResult.UNCONFIRMED,LatencyResult.measured(-2));assertEquals(LatencyResult.BUSY,LatencyResult.measured(ProbeAdmission.BUSY));assertEquals(LatencyResult.FAILED,LatencyResult.measured(0));}
 @Test public void onlyPositiveMeasurementsBecomeMilliseconds(){assertEquals(127,LatencyResult.measured(127));assertEquals(Integer.MAX_VALUE,LatencyResult.measured(Long.MAX_VALUE));}
 @Test public void restoredPendingMarkerIsNotAnEndlessSpinner(){assertEquals(LatencyResult.UNTESTED,LatencyResult.restored(LatencyResult.TESTING));assertEquals(151,LatencyResult.restored(151));}
 @Test public void manualFourthRequestWaitsForOwnedCapacity()throws Exception{
  ProbeAdmission gate=new ProbeAdmission(3);for(int i=0;i<3;i++)assertTrue(gate.enter(false));assertFalse(gate.enter(false));ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch begun=new CountDownLatch(1);
  try{Future<Boolean> fourth=pool.submit(()->{begun.countDown();return gate.enter(true);});assertTrue(begun.await(2,TimeUnit.SECONDS));try{fourth.get(100,TimeUnit.MILLISECONDS);fail("Fourth request was dropped instead of waiting");}catch(TimeoutException expected){}gate.exit();assertTrue(fourth.get(2,TimeUnit.SECONDS));gate.exit();}finally{pool.shutdownNow();gate.exit();gate.exit();}
 }
 @Test public void interruptedWaitDoesNotInventCapacity()throws Exception{
  ProbeAdmission gate=new ProbeAdmission(1);assertTrue(gate.enter(false));ExecutorService pool=Executors.newSingleThreadExecutor();CountDownLatch begun=new CountDownLatch(1),ended=new CountDownLatch(1);
  try{Future<?> waiter=pool.submit(()->{begun.countDown();try{gate.enter(true);fail("Cancelled waiter acquired unavailable permit");}catch(InterruptedException expected){}finally{ended.countDown();}});assertTrue(begun.await(2,TimeUnit.SECONDS));waiter.cancel(true);assertTrue(ended.await(2,TimeUnit.SECONDS));assertFalse(gate.enter(false));gate.exit();assertTrue(gate.enter(false));gate.exit();}finally{pool.shutdownNow();}
 }
}
