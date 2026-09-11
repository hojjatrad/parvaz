package com.parvaz.tunnel.core;

import java.net.URL;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Rechecks the active proxy until an actual HTTPS response is observed. Never
 * starts/stops a core, uses traffic counters as success, or falls back directly.
 * At most one scheduled retry and one bounded network worker are retained. */
final class ReadinessMonitor implements AutoCloseable {
    static final String GOOGLE="https://www.gstatic.com/generate_204";
    static final String CLOUDFLARE="https://cp.cloudflare.com/generate_204";
    interface ObservedResult {void finished(boolean ok,long ms,long network);}
    interface Probe {void start(String endpoint,int port,StartupWarmup.Result result);void cancel();}
    interface Pending {void cancel();}
    interface Timer {Pending later(Runnable work,long delayMs);}
    private final Probe probe;
    private final Timer timer;
    private final AtomicReference<Session> active=new AtomicReference<>();
    private final ScheduledThreadPoolExecutor executor;
    private final LatencyProbe network;
    ReadinessMonitor(){
        network=new LatencyProbe();
        probe=new Probe(){public void start(String url,int port,StartupWarmup.Result result){network.start(url,port,result);}public void cancel(){network.cancel();}};
        executor=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"parvaz-readiness-retry");t.setDaemon(true);return t;});
        executor.setRemoveOnCancelPolicy(true);
        timer=(work,delay)->{ScheduledFuture<?> f=executor.schedule(work,delay,TimeUnit.MILLISECONDS);return ()->f.cancel(false);};
    }
    ReadinessMonitor(Probe probe,Timer timer){this.probe=probe;this.timer=timer;network=null;executor=null;}
    static String[] endpoints(String configured){
        URL valid=StartupWarmup.endpoint(configured);
        String first=valid==null?GOOGLE:valid.toExternalForm();
        String second=valid!=null&&"cp.cloudflare.com".equalsIgnoreCase(valid.getHost())?GOOGLE:CLOUDFLARE;
        return new String[]{first,second};
    }
    synchronized void start(String configured,StartupWarmup.Result result){start(configured,-1,result);}
    synchronized void start(String configured,int port,StartupWarmup.Result result){
        startObserved(configured,port,(ok,ms,epoch)->result.finished(ok,ms));
    }
    synchronized void startObserved(String configured,int port,ObservedResult result){
        cancel();Session session=new Session(endpoints(configured),port,result);active.set(session);attempt(session);
    }
    private synchronized void attempt(Session session){
        if(active.get()!=session)return;
        session.pending=null;
        String endpoint=session.endpoints[session.nextEndpoint];
        session.nextEndpoint=(session.nextEndpoint+1)%session.endpoints.length;
        session.attempts=Math.min(6,session.attempts+1);
        long round=++session.round;session.network=NetworkEpoch.current();
        probe.start(endpoint,session.port,(ok,ms)->finished(session,round,ok,ms));
    }
    private synchronized void finished(Session session,long round,boolean ok,long ms){
        if(active.get()!=session)return;
        if(session.round!=round||session.reportedRound==round)return;
        session.reportedRound=round;
        if(!NetworkEpoch.owns(session.network)){ok=false;ms=-1;}
        session.result.finished(ok,ms,session.network);
        if(active.get()!=session)return;
        if(ok){active.set(null);return;}
        session.pending=timer.later(()->attempt(session),delayAfter(session.attempts));
    }
    static long delayAfter(int attempts){
        if(attempts<=1)return 500;
        if(attempts==2)return 2000;
        if(attempts==3)return 5000;
        if(attempts<=5)return 15000;
        return 60000;
    }
    synchronized void confirmedExternally(){Session previous=active.getAndSet(null);if(previous!=null&&previous.pending!=null)previous.pending.cancel();}
    synchronized void cancel(){Session previous=active.getAndSet(null);if(previous!=null&&previous.pending!=null)previous.pending.cancel();probe.cancel();}
    @Override public synchronized void close(){cancel();if(network!=null)network.close();if(executor!=null)executor.shutdownNow();}
    private static final class Session {
        final String[] endpoints;final int port;final ObservedResult result;int attempts,nextEndpoint;long round,reportedRound,network;Pending pending;
        Session(String[] endpoints,int port,ObservedResult result){this.endpoints=endpoints;this.port=port;this.result=result;}
    }
}
