package com.parvaz.tunnel.core;
import android.app.*;import android.content.*;import android.net.*;import android.os.*;import androidx.core.app.NotificationCompat;import androidx.core.content.ContextCompat;import com.parvaz.tunnel.*;
/** Process-lifetime default-network observer, independent of Activity resume.
 * No illicit background FGS launch: Android background start uses a user-tapped notification. */
public final class NetworkAutomation {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());private static boolean installed;private static String observed="",handled="",manual="";
 private NetworkAutomation(){}
 public static synchronized void install(Context context){
  if(installed)return;Context c=context.getApplicationContext();ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return;
  try{cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
   public void onAvailable(Network n){schedule(c);}
   public void onCapabilitiesChanged(Network n,NetworkCapabilities caps){schedule(c);}
   public void onLost(Network n){schedule(c);}
  });installed=true;schedule(c);}catch(RuntimeException ignored){}
 }
 private static Runnable pending;
 private static synchronized void schedule(Context c){if(pending!=null)MAIN.removeCallbacks(pending);pending=()->{synchronized(NetworkAutomation.class){pending=null;}evaluate(c);};MAIN.postDelayed(pending,1200);}
 static String key(Context c){try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);Network n=cm==null?null:cm.getActiveNetwork();NetworkCapabilities caps=n==null?null:cm.getNetworkCapabilities(n);if(caps==null||caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "";return n.getNetworkHandle()+":"+NetContext.transport(c);}catch(RuntimeException e){return "";}}
 public static synchronized void manualChoice(Context c){manual=key(c);handled=manual;}
 public static synchronized void settingsChanged(Context c){handled="";manual="";schedule(c.getApplicationContext());}
 public static synchronized void evaluate(Context c){
  String key=key(c);if(!key.equals(observed)){observed=key;if(!key.equals(manual))manual="";NetworkEpoch.changed();}
  if(key.isEmpty()||key.equals(handled)||key.equals(manual)||SafeMode.sTrippedThisRun)return;
  int action=AutoProfile.decide(c);if(action==AutoProfile.ACTION_NONE)return;handled=key;
  if(action==AutoProfile.ACTION_DISCONNECT){if(TunnelVpnService.serviceRunning)c.startService(new Intent(c,TunnelVpnService.class).setAction("com.parvaz.tunnel.STOP").putExtra("automatic_network_rule",true));return;}
  if(TunnelVpnService.serviceRunning||TunnelVpnService.currentState==1||TunnelVpnService.currentState==5)return;
  if(AppLock.foreground()&&AppLock.allowed(c)&&VpnService.prepare(c)==null){
   com.parvaz.tunnel.store.LastConnected.restore(c);
   try{ContextCompat.startForegroundService(c,new Intent(c,TunnelVpnService.class).setAction("com.parvaz.tunnel.START").putExtra("automatic_network_rule",true));return;}catch(RuntimeException restricted){}
  }
  try{NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
   if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("network_rules",c.getString(R.string.auto_profile),NotificationManager.IMPORTANCE_LOW));
   Intent open=new Intent(c,MainActivity.class).putExtra("com.parvaz.tunnel.AUTO_CONNECT",true);
   nm.notify(822,new NotificationCompat.Builder(c,"network_rules").setSmallIcon(R.drawable.ic_tile).setContentTitle(c.getString(R.string.auto_profile))
     .setContentText(c.getString(R.string.auto_profile_tap_connect)).setContentIntent(PendingIntent.getActivity(c,822,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true).build());
  }catch(SecurityException denied){/* No permission means no autonomous background start. */}
 }
}
