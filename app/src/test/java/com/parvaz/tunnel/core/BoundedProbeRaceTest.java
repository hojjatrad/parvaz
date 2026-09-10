package com.parvaz.tunnel.core;
import org.junit.*;
import org.junit.runners.MethodSorters;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BoundedProbeRaceTest {
 @Test public void changedNetworkCannotWinOrPoisonHistory(){long epoch=NetworkEpoch.current();BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(Collections.singletonList(1),1,1000,c->{NetworkEpoch.changed();return 12;},()->NetworkEpoch.owns(epoch));assertEquals(BoundedProbeRace.Status.CANCELLED,r.status);assertNull(r.winner);assertTrue(r.failed.isEmpty());}
 @Test public void allFailuresFinishWithoutWaitingForWholeDeadline(){
  BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(Arrays.asList(1,2,3),3,12000,c->-1,()->true);
  assertEquals(BoundedProbeRace.Status.NO_RESPONSE,r.status);assertEquals(3,r.probed);assertEquals(3,r.failed.size());assertTrue(r.elapsedMs<3000);
 }
 @Test public void cancelledOwnershipNeverStartsAProbe(){
  BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(Arrays.asList(1,2),3,1000,c->{throw new AssertionError();},()->false);
  assertEquals(BoundedProbeRace.Status.CANCELLED,r.status);assertEquals(0,r.probed);assertNull(r.winner);
 }
 @Test public void capCannotBeRaisedByCaller(){
  BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(Arrays.asList(1,2,3,4,5,6),100,1000,c->-1,()->true);
  assertEquals(3,r.probed);assertEquals(3,r.failed.size());
 }
 @Test public void successfulActualResponseWins(){
  BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(Collections.singletonList(7),3,1000,c->153,()->true);
  assertEquals(BoundedProbeRace.Status.SUCCESS,r.status);assertEquals(Integer.valueOf(7),r.winner);assertEquals(153,r.delay);
 }
 @Test public void supersededResultCannotWinOrPoisonHistory(){
  AtomicBoolean current=new AtomicBoolean(true);
  BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(Collections.singletonList(1),1,1000,c->{current.set(false);return 30;},current::get);
  assertEquals(BoundedProbeRace.Status.CANCELLED,r.status);assertNull(r.winner);assertTrue(r.failed.isEmpty());
 }
 @Test public void zzAbandonedNativeWorkStillOccupiesGlobalBudget()throws Exception {
  CountDownLatch entered=new CountDownLatch(3),release=new CountDownLatch(1),exited=new CountDownLatch(3);
  try{
   BoundedProbeRace.Result<Integer> first=BoundedProbeRace.run(Arrays.asList(1,2,3),3,3000,c->{entered.countDown();try{while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}return -1;}finally{exited.countDown();}},()->true);
   assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(BoundedProbeRace.Status.TIMEOUT,first.status);assertTrue(first.failed.isEmpty());
   BoundedProbeRace.Result<Integer> second=BoundedProbeRace.run(Arrays.asList(4,5,6),3,1000,c->{throw new AssertionError("Exceeded global native budget");},()->true);
   assertEquals(BoundedProbeRace.Status.BUSY,second.status);assertEquals(0,second.probed);assertTrue(second.failed.isEmpty());
  }finally{release.countDown();assertTrue(exited.await(2,TimeUnit.SECONDS));}
 }
 @org.junit.Test public void qualityWindowCanPreferStableOverFirstResponder(){
  BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(java.util.Arrays.asList(1,2),2,1000,c->{if(c==2)Thread.sleep(30);return 100;},()->true,(c,d)->c==1?900:200,100);
  org.junit.Assert.assertEquals(Integer.valueOf(2),r.winner);
 }
 @org.junit.Test public void unsupportedRouteIsNotARecordedFailure(){BoundedProbeRace.Result<Integer> r=BoundedProbeRace.run(java.util.Collections.singletonList(1),1,1000,c->-2,()->true);org.junit.Assert.assertNull(r.winner);org.junit.Assert.assertTrue(r.failed.isEmpty());}
}
