package com.parvaz.tunnel.core;

import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** One best-effort, bounded HTTPS HEAD through the already-started local tunnel.
 * Never blocks core startup, changes connection state, switches servers, downloads
 * a response body, follows redirects, or falls back to the system route.
 */
final class StartupWarmup implements AutoCloseable {
    static final int CONNECT_TIMEOUT_MS=5000,READ_TIMEOUT_MS=6000,DEADLINE_MS=12000;
    interface Connections {HttpURLConnection open(URL url)throws Exception;}
    interface Result {void finished(boolean confirmed,long elapsedMs);}
    private final Connections connections;
    private final Result result;
    private final int deadlineMs;
    private final AtomicReference<Attempt> active=new AtomicReference<>();
    // No unbounded task queue: rapid reconnects cannot accumulate old probes.
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"tunnel-startup-probe");t.setDaemon(true);return t;});
    private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"tunnel-startup-deadline");t.setDaemon(true);return t;});
    StartupWarmup(){this(StartupWarmup::tunnelConnection,(ok,ms)->android.util.Log.i("ParvazCore",ok?"Startup tunnel probe completed in "+ms+" ms":"Startup tunnel probe not confirmed; user traffic remains available"));}
    StartupWarmup(Connections connections,Result result){this(connections,result,DEADLINE_MS);}
    StartupWarmup(Connections connections,Result result,int deadlineMs){this.connections=connections;this.result=result;this.deadlineMs=deadlineMs;deadlines.setRemoveOnCancelPolicy(true);}
    static HttpURLConnection tunnelConnection(URL url)throws java.io.IOException{return new AppNetwork.Route(true).open(url);}
    static HttpURLConnection pinnedConnection(URL url,int port)throws java.io.IOException {
        if(port<1||port>65535)throw new java.io.IOException("Pinned readiness route unavailable");
        return (HttpURLConnection)url.openConnection(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",port)));
    }
    static URL endpoint(String value){
        try{
            if(value==null||value.length()>2048)return null;
            URL url=new URL(value);
            if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty()||url.getUserInfo()!=null||url.getRef()!=null)return null;
            return url;
        }catch(Exception invalid){return null;}
    }
    void start(String endpoint){start(endpoint,result);}
    void start(String endpoint,Result completion){start(endpoint,connections,completion);}
    void start(String endpoint,Connections route,Result completion){
        cancel();URL url=endpoint(endpoint);if(url==null||worker.isShutdown()){completion.finished(false,0);return;}
        Attempt attempt=new Attempt(url,route,completion);active.set(attempt);
        try{worker.execute(()->run(attempt));}
        catch(RejectedExecutionException busy){finish(attempt,false,0);}
    }
    void cancel(){Attempt attempt=active.getAndSet(null);if(attempt!=null)attempt.cancel();}
    private void run(Attempt attempt){
        long began=System.nanoTime();ScheduledFuture<?> deadline=null;boolean confirmed=false;
        try{
            if(attempt.cancelled||active.get()!=attempt)return;
            deadline=deadlines.schedule(()->finish(attempt,false,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)),deadlineMs,TimeUnit.MILLISECONDS);
            HttpURLConnection connection=attempt.route.open(attempt.url);
            if(!attempt.attach(connection))return;
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            connection.setRequestMethod("HEAD");connection.setRequestProperty("Connection","close");
            if(attempt.cancelled||active.get()!=attempt)return;
            int status=connection.getResponseCode();confirmed=status>=200&&status<300;
        }catch(Exception ignored){/* No URL/credentials in logs and no direct fallback. */}
        finally{
            if(deadline!=null)deadline.cancel(false);
            finish(attempt,confirmed,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began));
        }
    }
    private void finish(Attempt attempt,boolean confirmed,long elapsed){
        boolean owned=active.compareAndSet(attempt,null);
        attempt.cancel();
        // Deadline and worker race through the same CAS: exactly one outcome.
        // Explicit STOP/replacement removes ownership and reports nothing.
        if(owned)attempt.result.finished(confirmed,elapsed);
    }
    @Override public void close(){cancel();worker.shutdownNow();deadlines.shutdownNow();}
    private static final class Attempt {
        final URL url;final Connections route;final Result result;volatile boolean cancelled;private HttpURLConnection connection;
        Attempt(URL url,Connections route,Result result){this.url=url;this.route=route;this.result=result;}
        boolean attach(HttpURLConnection value){
            synchronized(this){if(!cancelled){connection=value;return true;}}
            value.disconnect();return false;
        }
        void cancel(){
            HttpURLConnection value;
            synchronized(this){cancelled=true;value=connection;connection=null;}
            if(value!=null)value.disconnect();
            // Disconnect this socket, never interrupt a pool thread that may
            // already have been reused by a newer session.
        }
    }
}
