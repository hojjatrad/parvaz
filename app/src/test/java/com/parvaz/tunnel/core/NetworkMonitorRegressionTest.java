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
  wifiCaps=capabilities(NetworkCapabilities.TRANSPORT_WIFI);
  cellCaps=capabilities(NetworkCapabilities.TRANSPORT_CELLULAR);
  shadow.setNetworkCapabilities(wifi,wifiCaps);shadow.setNetworkCapabilities(cell,cellCaps);
  monitor=new NetworkMonitor(context,null);monitor.start();assertNotNull(monitor.d);
  monitor.d.onAvailable(wifi);monitor.d.onCapabilitiesChanged(wifi,wifiCaps);
  assertEquals(wifi.getNetworkHandle(),monitor.g);assertFalse(monitor.f6248c.hasCallbacks(monitor.j));
 }
 static NetworkCapabilities capabilities(int transport){
  NetworkCapabilities caps=ShadowNetworkCapabilities.newInstance();
  Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  Shadows.shadowOf(caps).addTransportType(transport);return caps;
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
 @Test public void realDefaultHandoverStillSchedulesReconnect(){
  shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_MOBILE,0,true,true));
  Network next=manager.getActiveNetwork();shadow.setNetworkCapabilities(next,cellCaps);
  monitor.d.onAvailable(next);monitor.d.onCapabilitiesChanged(next,cellCaps);
  assertEquals(next.getNetworkHandle(),monitor.g);assertTrue(monitor.f6248c.hasCallbacks(monitor.j));
 }
 @Test public void sameDefaultCapabilitiesDoNotRestart(){
  for(int i=0;i<5;i++){monitor.d.onAvailable(wifi);monitor.d.onCapabilitiesChanged(wifi,wifiCaps);}
  assertFalse(monitor.f6248c.hasCallbacks(monitor.j));
 }
 @Test public void vpnOverlayIsNotAnUnderlyingNetworkChange(){
  shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_VPN,0,true,true));
  Network vpn=manager.getActiveNetwork();NetworkCapabilities caps=capabilities(NetworkCapabilities.TRANSPORT_VPN);shadow.setNetworkCapabilities(vpn,caps);
  monitor.d.onAvailable(vpn);monitor.d.onCapabilitiesChanged(vpn,caps);monitor.d.onLost(wifi);
  assertEquals(wifi.getNetworkHandle(),monitor.g);assertFalse(monitor.f6253i);assertFalse(monitor.f6248c.hasCallbacks(monitor.j));
 }
 @Test public void genuineLossAndReturnStillSchedulesReconnect(){
  monitor.d.onLost(wifi);assertTrue(monitor.f6253i);
  monitor.d.onAvailable(wifi);assertTrue(monitor.f6248c.hasCallbacks(monitor.j));
 }
 @Test public void oldRegistrationCannotModifyRestartedMonitor(){
  NetworkMonitor.a old=monitor.d;monitor.stop();monitor.start();
  old.onAvailable(wifi);old.onCapabilitiesChanged(wifi,wifiCaps);
  assertEquals(-1,monitor.g);assertFalse(monitor.f6248c.hasCallbacks(monitor.j));
  monitor.d.onAvailable(wifi);assertEquals(wifi.getNetworkHandle(),monitor.g);
 }
}
