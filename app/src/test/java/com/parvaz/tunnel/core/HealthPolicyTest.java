package com.parvaz.tunnel.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class HealthPolicyTest {
 @Test public void oneFailureCannotRestartEvenWithOldOneStrikeSetting(){assertFalse(HealthPolicy.evaluate(true,false,-1,1200,0,1).restart);}
 @Test public void twoFailuresAreToleratedAndThirdCanSwitch(){assertFalse(HealthPolicy.evaluate(true,false,-1,1200,1,3).restart);assertTrue(HealthPolicy.evaluate(true,false,-1,1200,2,3).restart);}
 @Test public void receivingTrafficClearsStrikesDespiteFailedPing(){HealthPolicy.Decision d=HealthPolicy.evaluate(true,true,-1,1200,9,3);assertEquals(0,d.strikes);assertFalse(d.restart);}
 @Test public void healthyProbeClearsStrikes(){assertEquals(0,HealthPolicy.evaluate(true,false,150,1200,2,3).strikes);}
 @Test public void deadCoreIsDifferentFromOneEndpointFailure(){assertTrue(HealthPolicy.evaluate(false,true,100,1200,0,3).restart);}
 @Test public void slowResponseIsNotADisconnection(){assertFalse(HealthPolicy.evaluate(true,false,1400,1200,0,3).restart);assertFalse(HealthPolicy.evaluate(true,false,1400,1200,2,3).restart);}
 @Test public void largerUserToleranceIsPreserved(){assertFalse(HealthPolicy.evaluate(true,false,-1,1200,2,5).restart);assertTrue(HealthPolicy.evaluate(true,false,-1,1200,4,5).restart);}
 @Test public void unprovableOrBusyMeasurementCannotCauseSwitch(){assertFalse(HealthPolicy.evaluate(true,false,VerifiedProbe.UNKNOWN,1200,4,3).restart);assertEquals(0,HealthPolicy.evaluate(true,true,VerifiedProbe.UNKNOWN,1200,4,3).strikes);}
}
