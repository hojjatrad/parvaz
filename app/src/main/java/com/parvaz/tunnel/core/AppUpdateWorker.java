package com.parvaz.tunnel.core;
import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.work.*;
import com.parvaz.tunnel.R;
import java.io.File;
import java.util.concurrent.TimeUnit;
/** Metadata-only periodic checks; optional downloads on unmetered power, never
 * automatic installer launches. The normal strict/resumable updater is reused. */
public final class AppUpdateWorker extends Worker {
 static final String CHECK="parvaz-app-update-check",DOWNLOAD="parvaz-app-update-download";
 private volatile Thread running;
 public AppUpdateWorker(Context context,WorkerParameters parameters){super(context,parameters);}
 public static void schedule(Context context){
  try{
   WorkManager work=WorkManager.getInstance(context);boolean enabled=context.getSharedPreferences("parvaz_prefs",0).getBoolean("background_updates",true);
   if(!enabled){work.cancelUniqueWork(CHECK);work.cancelUniqueWork(DOWNLOAD);return;}
   work.enqueueUniquePeriodicWork(CHECK,ExistingPeriodicWorkPolicy.KEEP,checkRequest());
   if(!context.getSharedPreferences("parvaz_prefs",0).getBoolean("auto_download_wifi",true))work.cancelUniqueWork(DOWNLOAD);
  }catch(RuntimeException ignored){/* Manual update remains available. */}
 }
 static PeriodicWorkRequest checkRequest(){return new PeriodicWorkRequest.Builder(AppUpdateWorker.class,6,TimeUnit.HOURS)
    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build();}
 static OneTimeWorkRequest downloadRequest(){return new OneTimeWorkRequest.Builder(AppUpdateWorker.class).setInputData(new Data.Builder().putBoolean("download",true).build())
      .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true).setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
      .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build();}
 @Override public Result doWork(){
  running=Thread.currentThread();Context context=getApplicationContext();
  try{
   if(!context.getSharedPreferences("parvaz_prefs",0).getBoolean("background_updates",true)||isStopped())return Result.success();
   UpdateChecker.Release release=UpdateChecker.check(context);
   if(isStopped()||!context.getSharedPreferences("parvaz_prefs",0).getBoolean("background_updates",true)||release==null||UpdateChecker.isSkipped(context,release.version))return Result.success();
   boolean download=getInputData().getBoolean("download",false);
   if(!download){
    notifyUpdate(context,release,null);
    if(context.getSharedPreferences("parvaz_prefs",0).getBoolean("auto_download_wifi",true))WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD,ExistingWorkPolicy.KEEP,
     downloadRequest());
    return Result.success();
   }
   if(!context.getSharedPreferences("parvaz_prefs",0).getBoolean("auto_download_wifi",true))return Result.success();
   File file=UpdateChecker.download(context,release,percent->{if(isStopped())Thread.currentThread().interrupt();});
   if(!isStopped()&&context.getSharedPreferences("parvaz_prefs",0).getBoolean("background_updates",true))notifyUpdate(context,release,file);
   return Result.success();
  }catch(Exception error){
   context.getSharedPreferences("parvaz_update",0).edit().putString("last_background_error",UpdateChecker.safeError(error)).apply();
   return getRunAttemptCount()<3&&!isStopped()?Result.retry():Result.failure();
  }finally{running=null;}
 }
 @Override public void onStopped(){Thread thread=running;if(thread!=null)thread.interrupt();super.onStopped();}
 private void notifyUpdate(Context context,UpdateChecker.Release release,File file){
  if(isStopped()||!context.getSharedPreferences("parvaz_prefs",0).getBoolean("background_updates",true)||(file!=null&&!context.getSharedPreferences("parvaz_prefs",0).getBoolean("auto_download_wifi",true)))return;
  try{
   NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null)return;
   String channel="parvaz_updates";
   if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(channel,context.getString(R.string.update_title),NotificationManager.IMPORTANCE_LOW));
   Intent intent=file==null?new Intent(context,com.parvaz.tunnel.SettingsActivity.class):UpdateInstallActivity.intent(context,file,release);
   PendingIntent open=PendingIntent.getActivity(context,821,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   manager.notify(821,new NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_tile).setContentTitle(context.getString(R.string.update_available,release.version))
    .setContentText(context.getString(file==null?R.string.update_download:R.string.update_install_ready)).setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true).build());
  }catch(SecurityException denied){/* Android 13 notification permission may be denied. */}
 }
}
