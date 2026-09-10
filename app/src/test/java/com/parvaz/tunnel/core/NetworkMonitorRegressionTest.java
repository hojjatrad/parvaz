package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.Context;
import android.net.*;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NetworkMonitorRegressionTest {
 Context context;ConnectivityManager manager;ShadowConnectivityManager shadow;NetworkMonitor monitor;
 Network wifi,cell;NetworkCapabilities wifiCaps,cellCaps;
 @Before public void setup(){
  context=ApplicationProvider.getApplicationContext();manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);shadow=Shadows.shadowOf(manager);
  shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_WIFI,0,true,true));
  wifi=manager.getActiveNetwork();cell=ShadowNetwork.newInstance(100);
  wifiCaps=new NetworkCapabilities().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
  cellCaps=new NetworkCapabilities().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
  shadow.setNetworkCapabilities(wifi,wifiCaps);shadow.setNetworkCapabilities(cell,cellCaps);
  monitor=new NetworkMonitor(context,null);monitor.start();assertNotNull(monitor.d);
  monitor.d.onAvailable(wifi);monitor.d.onCapabilitiesChanged(wifi,wifiCaps);
  assertEquals(wifi.getNetworkHandle(),monitor.g);assertFalse(monitor.f6248c.hasCallbacks(monitor.j));
 }
 @After public void cleanup(){monitor.stop();}
 @Test public void secondaryAvailableMustNotRestartNewTunnel(){
  monitor.d.onAvailable(cell);monitor.d.onCapabilitiesChanged(cell,cellCaps);
  assertEquals("Secondary network stole the default",wifi.getNetworkHandle(),monitor.g);
  assertFalse("Secondary availability scheduled a core restart",monitor.f6248c.hasCallbacks(monitor.j));
 }
 @Test public void secondaryCapabilityUpdateMustNotRestartNewTunnel(){
  monitor.d.onCapabilitiesChanged(cell,cellCaps);
  assertFalse("Unrelated capabilities scheduled a core restart",monitor.f6248c.hasCallbacks(monitor.j));
  assertEquals(wifi.getNetworkHandle(),monitor.g);
 }
 @Test public void callbacksQueuedBeforeStopCannotReactivateMonitor(){
  NetworkMonitor.a callback=monitor.d;monitor.stop();callback.onAvailable(cell);callback.onCapabilitiesChanged(cell,cellCaps);
  assertEquals(-1,monitor.g);assertFalse(monitor.f6248c.hasCallbacks(monitor.j));
 }
}
