package androidx.work; public class WorkManager {
 public static WorkManager getInstance(android.content.Context c){return new WorkManager();}
 public void cancelUniqueWork(String s){}
 public void enqueueUniquePeriodicWork(String s,ExistingPeriodicWorkPolicy p,PeriodicWorkRequest r){}
}
