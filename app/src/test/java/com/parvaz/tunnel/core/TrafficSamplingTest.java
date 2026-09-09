package com.parvaz.tunnel.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrafficSamplingTest {
 @Test public void quarterSecondDeltasAreRatesNotQuarterSpeed(){
  TrafficSampling sampling=new TrafficSampling(1000);
  TrafficSampling.Sample first=sampling.sample(1250,250,500);
  assertEquals(1000,first.upPerSecond);assertEquals(2000,first.downPerSecond);
  assertEquals(250,first.nextDelayMs);assertFalse(first.updateNotification);
 }
 @Test public void delayedTicksUseElapsedTime(){
  TrafficSampling sampling=new TrafficSampling(1000);
  TrafficSampling.Sample sample=sampling.sample(3000,2000,6000);
  assertEquals(1000,sample.upPerSecond);assertEquals(3000,sample.downPerSecond);
 }
 @Test public void startupPollingEndsAndNotificationsRemainOneHz(){
  TrafficSampling sampling=new TrafficSampling(0);int notices=0;
  for(int i=1;i<=12;i++){
   TrafficSampling.Sample sample=sampling.sample(i*250,250,250);
   if(sample.updateNotification)notices++;
   assertEquals(i<12?250:1000,sample.nextDelayMs);
  }
  assertEquals(3,notices);assertEquals(1000,sampling.sample(4000,1000,1000).downPerSecond);
 }
 @Test public void idleDoesNotFabricateTraffic(){
  TrafficSampling.Sample sample=new TrafficSampling(0).sample(250,0,0);
  assertEquals(0,sample.upPerSecond);assertEquals(0,sample.downPerSecond);
 }
 @Test public void zeroElapsedAndOverflowStayFiniteAndNonNegative(){
  TrafficSampling sampling=new TrafficSampling(100);
  assertEquals(0,sampling.sample(100,1000,1000).upPerSecond);
  TrafficSampling.Sample sample=sampling.sample(101,Long.MAX_VALUE,-1);
  assertEquals(Long.MAX_VALUE,sample.upPerSecond);assertEquals(0,sample.downPerSecond);
 }
}
