package com.parvaz.tunnel.core;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
public class StartupDiagnosticsTest {
 @Test public void monotonicPhasesAreSeparateAndUnknownsAreNotZero(){
  AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,17,now::get);
  now.set(5_000_000);a.cleanupDone();now.set(12_000_000);a.configDone();now.set(20_000_000);a.coreStarted();
  String s=a.report();assertTrue(s.contains("vpn_setup_ms=17"));assertTrue(s.contains("previous_core_cleanup_ms=5"));assertTrue(s.contains("local_core_started_from_entry_ms=20"));assertTrue(s.contains("first_proxy_rx_observed_from_entry_ms=UNKNOWN"));assertTrue(s.contains("dns_ms=NOT_INSTRUMENTED"));assertTrue(s.contains("first_application_response_ms=NOT_INSTRUMENTED"));
 }
 @Test public void localCoreAloneDoesNotConfirmCommunication(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,-1,now::get);a.coreStarted();assertEquals(StartupDiagnostics.Confirmation.CHECKING,a.confirmation());now.set(13_000_000_000L);assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,a.confirmation());}
 @Test public void proxyBytesCannotMasqueradeAsHttpOrApplicationResponse(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,0,now::get);a.coreStarted();a.received(100);assertEquals(StartupDiagnostics.Confirmation.CHECKING,a.confirmation());assertTrue(a.report().contains("first_application_response_ms=NOT_INSTRUMENTED"));}
 @Test public void unpinnedHttpResponseCannotTurnGreen(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,0,now::get);a.coreStarted();now.set(120_000_000);a.probeFinished(true,100);assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,a.confirmation());assertTrue(a.report().contains("selected_remote_route=NOT_PROVEN"));}
 @Test public void supersededAttemptCannotChangeCurrentReport(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt old=StartupDiagnostics.begin(0,1,now::get);old.routeConfigured(true);old.coreStarted();StartupDiagnostics.Attempt current=StartupDiagnostics.begin(0,2,now::get);current.routeConfigured(true);current.coreStarted();old.probeFinished(true,10);old.received(100);assertEquals(StartupDiagnostics.Confirmation.CHECKING,StartupDiagnostics.confirmation());assertFalse(StartupDiagnostics.safeReport().contains("probe_duration_ms=10"));}
 @Test public void stoppedAttemptCannotBecomeConfirmedLater(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,0,now::get);a.routeConfigured(true);a.coreStarted();a.stop();a.probeFinished(true,10);assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,a.confirmation());assertTrue(a.report().contains("session=STOPPED"));}
 @Test public void failedEndpointMeansUnconfirmedNotAForcedDisconnect(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,0,now::get);a.coreStarted();a.probeFinished(false,23);assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,a.confirmation());assertTrue(a.report().contains("session=LATEST_CORE_ATTEMPT"));}
 @Test public void onlyFirstReceivedObservationIsStored(){AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt a=StartupDiagnostics.begin(0,-1,now::get);a.coreStarted();a.received(0);now.set(200_000_000);a.received(1);now.set(400_000_000);a.received(9);assertTrue(a.report().contains("first_proxy_rx_observed_from_entry_ms=200"));}
}
