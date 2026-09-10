package com.parvaz.tunnel.core;

import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One best-effort, bounded HTTPS HEAD through the already-started local tunnel.
 * Never blocks core startup, changes connection state, switches servers, downloads
 * a response body, follows redirects, or falls back to the system route.
 */
final class StartupWarmup implements AutoCloseable {
    static final int CONNECT_TIMEOUT_MS=1200,READ_TIMEOUT_MS=1800,DEADLINE_MS=4000;
    interface Connections {HttpURLConnection open(URL url)throws Exception;}
    interface Result {void finished(boolean confirmed,long elapsedMs);}
    private final Connections connections;
    private final Result result;
    private final AtomicReference<Attempt> active=new AtomicReference<>();
    // No unbounded task queue: rapid reconnects cannot accumulate old probes.
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"tunnel-startup-probe");t.setDaemon(true);return t;});
    private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"tunnel-startup-deadline");t.setDaemon(true);return t;});
    StartupWarmup(){this(StartupWarmup::tunnelConnection,(ok,ms)->android.util.Log.i("ParvazCore",ok?"Startup tunnel probe completed in "+ms+" ms":"Startup tunnel probe not confirmed; user traffic remains available"));}
    StartupWarmup(Connections connections,Result result){this.connections=connections;this.result=result;deadlines.setRemoveOnCancelPolicy(true);}
    static HttpURLConnection tunnelConnection(URL url)throws java.io.IOException{return new AppNetwork.Route(true).open(url);}
    static URL endpoint(String value){
        try{
            if(value==null||value.length()>2048)return null;
            URL url=new URL(value);
            if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty()||url.getUserInfo()!=null||url.getRef()!=null)return null;
            return url;
        }catch(Exception invalid){return null;}
    }
    void start(String endpoint){start(endpoint,result);}
    void start(String endpoint,Result completion){
        cancel();URL url=endpoint(endpoint);if(url==null||worker.isShutdown())return;
        Attempt attempt=new Attempt(url,completion);active.set(attempt);
        try{worker.execute(()->run(attempt));}
        catch(RejectedExecutionException busy){active.compareAndSet(attempt,null);attempt.cancel();}
    }
    void cancel(){Attempt attempt=active.getAndSet(null);if(attempt!=null)attempt.cancel();}
    private void run(Attempt attempt){
        long began=System.nanoTime();ScheduledFuture<?> deadline=null;boolean confirmed=false;
        try{
            if(attempt.cancelled||active.get()!=attempt)return;
            attempt.thread=Thread.currentThread();
            deadline=deadlines.schedule(attempt::cancel,DEADLINE_MS,TimeUnit.MILLISECONDS);
            HttpURLConnection connection=connections.open(attempt.url);
            if(!attempt.attach(connection))return;
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setRequestMethod("HEAD");connection.setRequestProperty("Connection","close");
            if(attempt.cancelled||active.get()!=attempt)return;
            int status=connection.getResponseCode();confirmed=status>=200&&status<300;
        }catch(Exception ignored){/* No URL/credentials in logs, retries, or direct fallback. */}
        finally{
            if(deadline!=null)deadline.cancel(false);
            boolean owned=active.compareAndSet(attempt,null);
            boolean report=!attempt.cancelled&&owned;
            attempt.cancel();
            if(report)attempt.result.finished(confirmed,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began));
        }
    }
    @Override public void close(){cancel();worker.shutdownNow();deadlines.shutdownNow();}
    private static final class Attempt {
        final URL url;final Result result;volatile boolean cancelled;volatile Thread thread;private HttpURLConnection connection;
        Attempt(URL url,Result result){this.url=url;this.result=result;}
        boolean attach(HttpURLConnection value){
            synchronized(this){if(!cancelled){connection=value;return true;}}
            value.disconnect();return false;
        }
        void cancel(){
            HttpURLConnection value;Thread running;
            synchronized(this){cancelled=true;value=connection;running=thread;}
            if(value!=null)value.disconnect();
            if(running!=null&&running!=Thread.currentThread())running.interrupt();
        }
    }
}
