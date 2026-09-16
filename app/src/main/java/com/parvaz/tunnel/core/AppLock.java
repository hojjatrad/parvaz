package com.parvaz.tunnel.core;
import android.app.*;import android.content.*;import android.os.Bundle;
/** Process-memory UI authorization; no persisted 'unlocked' bit. Backgrounding locks immediately. */
public final class AppLock {
 private static boolean granted,installed;private static int started;
 private AppLock(){}
 public static boolean enabled(Context c){return c.getSharedPreferences("parvaz_prefs",0).getBoolean("app_lock",false);}
 public static synchronized boolean allowed(Context c){return !enabled(c)||granted;}
 public static synchronized boolean foreground(){return started>0;}
 public static synchronized void grant(){granted=true;}
 public static synchronized void lock(){granted=false;}
 public static synchronized void install(Application app){
  if(installed)return;installed=true;
  app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
   public void onActivityStarted(Activity a){synchronized(AppLock.class){started++;}}
   public void onActivityStopped(Activity a){synchronized(AppLock.class){started=Math.max(0,started-1);if(started==0&&!a.isChangingConfigurations())granted=false;}}
   public void onActivityCreated(Activity a,Bundle b){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
  });
 }
}
