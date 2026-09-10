package com.parvaz.tunnel.core;
import android.content.Context;
import android.content.SharedPreferences;
/** Process initialization and foreground startup have separate crash-loop counters. */
public final class SafeMode {
 public static boolean sTrippedThisRun=false;
 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("parvaz_safemode",0);}
 public static void beginProcess(Context c){SharedPreferences p=prefs(c);int n=Math.min(3,Math.max(0,p.getInt("pending_initializations",0))+1);sTrippedThisRun=p.getBoolean("safe_active",false)||n>=3||p.getInt("pending_launches",0)>=3;p.edit().putInt("pending_initializations",n).putBoolean("safe_active",sTrippedThisRun).commit();}
 public static void completeProcess(Context c){prefs(c).edit().putInt("pending_initializations",0).commit();}
 public static void beginForegroundLaunch(Context c){SharedPreferences p=prefs(c);int n=Math.min(3,Math.max(0,p.getInt("pending_launches",0))+1);sTrippedThisRun=sTrippedThisRun||p.getBoolean("safe_active",false)||n>=3;p.edit().putInt("pending_launches",n).putBoolean("safe_active",sTrippedThisRun).commit();}
 public static void markHealthy(Context c){prefs(c).edit().putInt("pending_launches",0).putInt("pending_initializations",0).putBoolean("safe_active",false).commit();}
}
