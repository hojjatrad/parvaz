package com.parvaz.tunnel.core;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** One in-memory, endpoint-free attempt. Nothing is uploaded or persisted. Core RX
 * includes probes/DNS and MUST NOT be described as first application response. */
public final class StartupDiagnostics {
 static final long PROOF_MAX_AGE_MS=120000;
 public enum Confirmation {CHECKING,RESPONSE_SEEN,UNCONFIRMED}
 private static final AtomicReference<Attempt> latest=new AtomicReference<>();
 private StartupDiagnostics(){}
 static Attempt begin(long began,long tunMs){return begin(began,tunMs,System::nanoTime,android.os.SystemClock::elapsedRealtime);}
 static Attempt begin(long began,long tunMs,LongSupplier clock){return begin(began,tunMs,clock,()->TimeUnit.NANOSECONDS.toMillis(clock.getAsLong()));}
 static Attempt begin(long began,long tunMs,LongSupplier clock,LongSupplier proofClock){Attempt a=new Attempt(began,tunMs,clock,proofClock);latest.set(a);return a;}
 public static Confirmation confirmation(){Attempt a=latest.get();return a==null?Confirmation.UNCONFIRMED:a.confirmation();}
 public static String safeReport(){Attempt a=latest.get();return a==null?"STARTUP_DIAGNOSTICS_V1\nATTEMPT_UNAVAILABLE":a.report();}
 public static int labelResource(){switch(confirmation()){
  case CHECKING:return com.parvaz.tunnel.R.string.tunnel_checking;
  case RESPONSE_SEEN:return com.parvaz.tunnel.R.string.tunnel_response_seen;
  default:return com.parvaz.tunnel.R.string.tunnel_unconfirmed;
 }}
 static final class Attempt {
  private final long resolverAtStart=NetworkEpoch.resolver();
  private final long began,tunMs;private final LongSupplier clock,proofClock;
  private long cleanupMs=-1,configMs=-1,coreMs=-1,responseMs=-1,probeMs=-1,rxMs=-1;
  private boolean stopped,routePinned,responseCurrent;
  private long proofAt=-1,proofNetwork=-1,verifiedDelayMs=-1;
  synchronized boolean resolverCurrent(){return owns()&&resolverAtStart==NetworkEpoch.resolver();}
  synchronized long verifiedLatency(){return proofFresh()&&verifiedDelayMs>0?verifiedDelayMs:-1;}
  synchronized boolean proofFresh(){return owns()&&resolverAtStart==NetworkEpoch.resolver()&&responseCurrent&&proofAt>=0&&NetworkEpoch.owns(proofNetwork)&&proofAge()<PROOF_MAX_AGE_MS;}
  synchronized boolean proofDue(){return !proofFresh()||proofAge()>=PROOF_MAX_AGE_MS/2;}
  synchronized void unconfirmed(){if(owns())responseCurrent=false;}
  synchronized void routeConfigured(boolean pinned){if(owns())routePinned=pinned;}
  Attempt(long began,long tunMs,LongSupplier clock,LongSupplier proofClock){this.began=began;this.tunMs=tunMs;this.clock=clock;this.proofClock=proofClock;}
  private long proofAge(){return proofAt<0?-1:Math.max(0,proofClock.getAsLong()-proofAt);}
  private boolean owns(){return !stopped&&latest.get()==this;}
  private long elapsed(){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(clock.getAsLong()-began));}
  synchronized void cleanupDone(){if(owns())cleanupMs=elapsed();}
  synchronized void configDone(){if(owns())configMs=elapsed();}
  synchronized void coreStarted(){if(owns())coreMs=elapsed();}
  synchronized void probeFinished(boolean ok,long duration){probeFinished(ok,duration,NetworkEpoch.current());}
  synchronized void probeFinished(boolean ok,long duration,long observedNetwork){if(owns()){probeMs=duration<0?-1:duration;if(ok&&routePinned&&resolverAtStart==NetworkEpoch.resolver()&&NetworkEpoch.owns(observedNetwork)){verifiedDelayMs=duration>0?duration:-1;responseCurrent=true;proofNetwork=observedNetwork;proofAt=proofClock.getAsLong();if(responseMs<0)responseMs=elapsed();}}}
  synchronized void received(long bytes){if(owns()&&bytes>0&&rxMs<0)rxMs=elapsed();}
  synchronized void stop(){stopped=true;}
  synchronized Confirmation confirmation(){
   if(!owns())return Confirmation.UNCONFIRMED;
   if(proofFresh())return Confirmation.RESPONSE_SEEN;
   // Invalid endpoint, busy worker, deadline cancellation and lost callback all
   // expire honestly. None keeps an indefinite spinner or proves a failed VPN.
   if(coreMs>=0&&probeMs<0&&elapsed()-coreMs<=StartupWarmup.DEADLINE_MS)return Confirmation.CHECKING;
   return Confirmation.UNCONFIRMED;
  }
  private static String value(long ms){return ms<0?"UNKNOWN":Long.toString(ms);}
  synchronized String report(){return "STARTUP_DIAGNOSTICS_V1\nclock=MONOTONIC\n"
   +"vpn_setup_ms="+value(tunMs)+"\nvpn_setup="+(tunMs<0?"REUSED_OR_UNMEASURED":"MEASURED")
   +"\nprevious_core_cleanup_ms="+value(cleanupMs)+"\nprevious_core_cleanup_scope=STOP_CALL_RETURN_NOT_CHILD_EXIT\nreconnect_fixed_delay_ms=0\nconfig_ready_from_core_entry_ms="+value(configMs)
   +"\nlocal_core_started_from_entry_ms="+value(coreMs)+"\nfirst_proxy_http_response_from_entry_ms="+value(responseMs)
   +"\nproof_clock=ELAPSED_REALTIME\nproof_age_ms="+value(proofAge())+"\nproof_revalidation_due="+proofDue()
   +"\nprobe_metric=HTTPS_REQUEST_RTT\nprobe_excludes_connect_and_tls=true\nprobe_duration_ms="+value(probeMs)+"\nfirst_proxy_rx_observed_from_entry_ms="+value(rxMs)
   +"\nconfirmation="+confirmation()+"\nsession="+(stopped?"STOPPED":"LATEST_CORE_ATTEMPT")
   +"\nselected_remote_route="+(routePinned?(proofFresh()?"PINNED_HTTPS_RESPONSE":"PINNED_AWAITING_RESPONSE"):"NOT_PROVEN")+"\ndns_ms=NOT_INSTRUMENTED\nfirst_application_response_ms=NOT_INSTRUMENTED\n";}
 }
}
