package com.parvaz.tunnel.core;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Deterministic virtual-time tests; no internet, sleeping, or device claims. */
public class ReadinessMonitorTest {
 static class Fake implements ReadinessMonitor.Probe,ReadinessMonitor.Timer {
  final List<Integer> ports=new ArrayList<>();final List<String> urls=new ArrayList<>();final List<StartupWarmup.Result> callbacks=new ArrayList<>();
  final Deque<Task> tasks=new ArrayDeque<>();int cancels;long delay;
  static class Task implements ReadinessMonitor.Pending {Runnable run;boolean cancelled;Task(Runnable r){run=r;}public void cancel(){cancelled=true;}}
  public void start(String url,int port,StartupWarmup.Result result){ports.add(port);urls.add(url);callbacks.add(result);}
  public void cancel(){cancels++;}
  public ReadinessMonitor.Pending later(Runnable r,long ms){delay=ms;Task t=new Task(r);tasks.add(t);return t;}
  void outcome(boolean ok){callbacks.get(callbacks.size()-1).finished(ok,100);}
  void advance(){Task t=tasks.remove();if(!t.cancelled)t.run.run();}
 }
 @Test public void initialFailureThenOtherEndpointSuccessTurnsCurrentDiagnosticsGreen(){
  Fake f=new Fake();AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt trace=StartupDiagnostics.begin(0,0,now::get);trace.routeConfigured(true);trace.coreStarted();
  try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,trace::probeFinished);f.outcome(false);
   assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,trace.confirmation());assertEquals(500,f.delay);
   now.set(10_000_000_000L);f.advance();assertEquals(ReadinessMonitor.CLOUDFLARE,f.urls.get(1));f.outcome(true);
   assertEquals(StartupDiagnostics.Confirmation.RESPONSE_SEEN,trace.confirmation());assertTrue(f.tasks.isEmpty());
   assertTrue(trace.report().contains("first_proxy_http_response_from_entry_ms=10000"));
   assertTrue(trace.report().contains("selected_remote_route=PINNED_HTTPS_RESPONSE"));
  }
 }
 @Test public void successIsImmediateWithoutWaitingForBackoff(){
  Fake f=new Fake();List<Boolean> outcomes=new ArrayList<>();try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,(ok,ms)->outcomes.add(ok));f.outcome(true);
   assertEquals(Collections.singletonList(true),outcomes);assertTrue(f.tasks.isEmpty());assertEquals(1,f.urls.size());
  }
 }
 @Test public void repeatedFailuresHaveOnlyOneTimerAndEventuallyOneAttemptPerMinute(){
  Fake f=new Fake();try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,(ok,ms)->assertFalse(ok));
   for(int i=1;i<=100;i++){f.outcome(false);assertEquals(1,f.tasks.size());assertEquals(ReadinessMonitor.delayAfter(i),f.delay);f.advance();}
   assertEquals(60000,f.delay);for(int i=0;i<f.urls.size();i++)assertEquals(i%2==0?ReadinessMonitor.GOOGLE:ReadinessMonitor.CLOUDFLARE,f.urls.get(i));
  }
 }
 @Test public void stopCancelsPendingRetryAndIgnoresLateResponse(){
  Fake f=new Fake();List<Boolean> outcomes=new ArrayList<>();try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,(ok,ms)->outcomes.add(ok));f.outcome(false);m.cancel();f.advance();f.outcome(true);
   assertEquals(1,f.urls.size());assertEquals(Collections.singletonList(false),outcomes);
  }
 }
 @Test public void replacedSessionCannotConfirmOrScheduleForNewSession(){
  Fake f=new Fake();List<Boolean> old=new ArrayList<>(),current=new ArrayList<>();try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,(ok,ms)->old.add(ok));StartupWarmup.Result stale=f.callbacks.get(0);
   m.start(ReadinessMonitor.CLOUDFLARE,(ok,ms)->current.add(ok));stale.finished(true,10);stale.finished(false,10);
   assertTrue(old.isEmpty());assertTrue(current.isEmpty());assertTrue(f.tasks.isEmpty());f.outcome(true);assertEquals(Collections.singletonList(true),current);
  }
 }
 @Test public void duplicateAndOldRoundResultsCannotCreateFalseSuccessOrRetryQueue(){
  Fake f=new Fake();List<Boolean> outcomes=new ArrayList<>();try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,(ok,ms)->outcomes.add(ok));StartupWarmup.Result first=f.callbacks.get(0);first.finished(false,10);first.finished(true,10);
   assertEquals(Collections.singletonList(false),outcomes);assertEquals(1,f.tasks.size());f.advance();first.finished(true,10);
   assertEquals(Collections.singletonList(false),outcomes);f.outcome(true);assertEquals(Arrays.asList(false,true),outcomes);assertTrue(f.tasks.isEmpty());
  }
 }
 @Test public void invalidPlaintextAndCredentialUrlsUseSafeHttpsEndpoints(){
  for(String value:new String[]{null,"","http://fixture.invalid/","https://user:secret@fixture.invalid/","bad"})assertArrayEquals(new String[]{ReadinessMonitor.GOOGLE,ReadinessMonitor.CLOUDFLARE},ReadinessMonitor.endpoints(value));
  assertArrayEquals(new String[]{ReadinessMonitor.CLOUDFLARE,ReadinessMonitor.GOOGLE},ReadinessMonitor.endpoints(ReadinessMonitor.CLOUDFLARE));
  assertEquals("https://fixture.invalid/check",ReadinessMonitor.endpoints("https://fixture.invalid/check")[0]);
 }
 @Test public void retryKeepsItsPinnedPortAndReplacementUsesNewPort(){
  Fake f=new Fake();try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,23111,(ok,ms)->{});f.outcome(false);f.advance();
   m.start(ReadinessMonitor.GOOGLE,23222,(ok,ms)->{});f.outcome(false);f.advance();
   assertEquals(Arrays.asList(23111,23111,23222,23222),f.ports);
  }
 }
 @Test public void slowRecoveryAfterCheckingExpiryIsStillConfirmed(){
  Fake f=new Fake();AtomicLong now=new AtomicLong();StartupDiagnostics.Attempt trace=StartupDiagnostics.begin(0,0,now::get);trace.routeConfigured(true);trace.coreStarted();
  try(ReadinessMonitor m=new ReadinessMonitor(f,f)){
   m.start(ReadinessMonitor.GOOGLE,trace::probeFinished);
   for(int i=0;i<8;i++){f.outcome(false);f.advance();}
   now.set(180_000_000_000L);assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,trace.confirmation());f.outcome(true);
   assertEquals(StartupDiagnostics.Confirmation.RESPONSE_SEEN,trace.confirmation());assertTrue(f.tasks.isEmpty());
  }
 }
}
