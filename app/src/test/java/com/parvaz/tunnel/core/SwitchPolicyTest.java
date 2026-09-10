package com.parvaz.tunnel.core;
import org.junit.Test;import static org.junit.Assert.*;
public class SwitchPolicyTest {
 @Test public void tinyOrUnmeasuredImprovementsNeverSwitch(){assertFalse(SwitchPolicy.better(200,150));assertFalse(SwitchPolicy.better(1000,800));assertFalse(SwitchPolicy.better(-1,100));assertFalse(SwitchPolicy.better(1000,-1));}
 @Test public void meaningfulImprovementCanSwitch(){assertTrue(SwitchPolicy.better(1000,700));assertTrue(SwitchPolicy.better(400,300));}
 @Test public void qualitySearchNeedsAgeRepeatedSlownessCooldownAndIdleTraffic(){assertTrue(SwitchPolicy.qualityDue(600000,0,0,3,false));assertFalse(SwitchPolicy.qualityDue(600000,550000,0,3,false));assertFalse(SwitchPolicy.qualityDue(600000,0,500000,3,false));assertFalse(SwitchPolicy.qualityDue(600000,0,0,2,false));assertFalse(SwitchPolicy.qualityDue(600000,0,0,3,true));}
 @Test public void recoveryBackoffIsBounded(){assertEquals(30000,SwitchPolicy.backoff(1));assertEquals(60000,SwitchPolicy.backoff(2));assertEquals(300000,SwitchPolicy.backoff(100000));}
 @Test public void jitterAndPoorReliabilityMatterAlongsideDelay(){assertTrue(SwitchPolicy.cost(100,20,200)>SwitchPolicy.cost(200,90,10));}
}
