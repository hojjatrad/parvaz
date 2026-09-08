package androidx.work;
public class ListenableWorker {
 private final android.content.Context context;
 private boolean stopped;
 public ListenableWorker(android.content.Context c,WorkerParameters p){context=c;}
 public android.content.Context getApplicationContext(){return context;}
 public boolean isStopped(){return stopped;}
 public void stopForTest(){stopped=true;}
 public static class Result {
  public final String kind; private Result(String k){kind=k;}
  public static Result retry(){return new Result("retry");}
  public static Result success(){return new Result("success");}
  public static Result failure(){return new Result("failure");}
 }
}
