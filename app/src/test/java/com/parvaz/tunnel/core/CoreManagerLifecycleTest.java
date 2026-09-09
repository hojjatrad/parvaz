package com.parvaz.tunnel.core;
import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={29,34},application=Application.class)
public class CoreManagerLifecycleTest {
 @Test public void staleAndIntentionalShutdownCallbacksCannotStopReplacement(){
  CoreManager manager=new CoreManager();AtomicInteger calls=new AtomicInteger();
  CoreManager.b old=manager.new b(calls::incrementAndGet);manager.running=true;manager.stop();
  manager.running=true;old.shutdown();assertTrue(manager.running);assertEquals(0,calls.get());
  CoreManager.b live=manager.new b(calls::incrementAndGet);live.shutdown();assertFalse(manager.running);assertEquals(1,calls.get());
 }
}
