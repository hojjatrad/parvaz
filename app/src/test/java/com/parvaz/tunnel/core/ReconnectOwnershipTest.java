package com.parvaz.tunnel.core;
import android.app.Application;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
/** Cancellation checkpoints with no fake native-success or internet claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class ReconnectOwnershipTest {
 private CoreManager manager;
 @Before public void setup(){manager=new CoreManager();}
 @After public void cleanup(){manager.stop();}
 @Test public void alreadyStaleRequestDoesNotStopExistingSession(){
  manager.running=true;long session=manager.sessionId();
  assertFalse(manager.startOwned(null,null,0,()->fail("Unexpected failure callback"),()->false));
  assertTrue(manager.running);assertEquals(session,manager.sessionId());assertNull(manager.startupAttempt());
 }
 @Test public void invalidationDuringStopCannotAllocateReplacement(){
  manager.running=true;long session=manager.sessionId();AtomicInteger checks=new AtomicInteger();
  assertFalse(manager.startOwned(null,null,0,()->fail("Cancellation is not a failure"),()->checks.incrementAndGet()==1));
  assertFalse(manager.running);assertEquals(session+1,manager.sessionId());assertEquals(2,checks.get());
  assertNull(manager.controller);assertEquals(StartupDiagnostics.Confirmation.UNCONFIRMED,manager.startupAttempt().confirmation());
  assertTrue(manager.startupAttempt().report().contains("session=STOPPED"));
 }
 @Test public void stopCallTimingDoesNotClaimAsynchronousChildExit(){
  AtomicInteger checks=new AtomicInteger();manager.startOwned(null,null,0,null,()->checks.incrementAndGet()==1);
  String report=manager.startupAttempt().report();
  assertTrue(report.contains("previous_core_cleanup_scope=STOP_CALL_RETURN_NOT_CHILD_EXIT"));
  assertTrue(report.contains("reconnect_fixed_delay_ms=0"));
  assertTrue(report.contains("dns_ms=NOT_INSTRUMENTED"));
 }
}
