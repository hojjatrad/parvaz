package com.parvaz.tunnel.core;
import android.app.Application;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.ProfileIdentity;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ProbeOwnershipRegressionTest {
 private final List<TunnelVpnService> services=new ArrayList<>();
 private TunnelVpnService service(){TunnelVpnService s=new TunnelVpnService();services.add(s);s.operations.start(false,ticket->{});return s;}
 @Before public void before(){CoreManager.c=new CoreManager();TunnelVpnService.serviceRunning=true;}
 @After public void after(){for(TunnelVpnService service:services)service.operations.close();TunnelVpnService.serviceRunning=false;CoreManager.c.stop();CoreManager.c=null;}
 @Test public void archivedCandidateIsNotProbed(){Profile a=new Profile(),b=new Profile();a.id="active";b.id="archived";assertEquals(1,HappyEyeballs.activeCandidates(Arrays.asList(a,b),Collections.singletonList(a)).size());}
 @Test public void changedCredentialInvalidatesSameIdAndDuplicatesAreBounded(){Profile a=new Profile();a.id="one";a.uuid="old";Profile b=ProfileIdentity.copy(a);b.uuid="new";assertTrue(HappyEyeballs.activeCandidates(Collections.singletonList(a),Collections.singletonList(b)).isEmpty());assertEquals(1,HappyEyeballs.activeCandidates(Arrays.asList(a,a),Collections.singletonList(a)).size());}
 @Test public void candidateSnapshotIsDetached(){Profile a=new Profile();a.id="one";Profile snapshot=HappyEyeballs.activeCandidates(Collections.singletonList(a),Collections.singletonList(a)).get(0);a.address="changed.invalid";assertNotEquals(a.address,snapshot.address);}
 @Test public void stoppedCoreInvalidatesOldHealthTickerAndQueuedSwitch(){
  TunnelVpnService service=service();TunnelVpnService.m ticker=service.new m(15000);service.b=ticker;assertTrue(ticker.isCurrent());
  CoreManager.c.stop();assertFalse(ticker.isCurrent());new TunnelVpnService_RunnableC0008AnonymousClass3_2(ticker).run();assertFalse(service.switching);
 }
 @Test public void replacementTickerInvalidatesPreviousOne(){TunnelVpnService service=service();TunnelVpnService.m old=service.new m(15000);service.b=old;assertTrue(old.isCurrent());service.b=service.new m(15000);assertFalse(old.isCurrent());assertTrue(service.b.isCurrent());service.switching=true;assertFalse(service.b.isCurrent());}
}
