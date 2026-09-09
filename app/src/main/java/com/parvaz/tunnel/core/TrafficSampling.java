package com.parvaz.tunnel.core;

/** Monotonic-time rates. Counter deltas remain untouched for quota accounting. */
public final class TrafficSampling {
    public static final long FAST_INTERVAL_MS=250, NORMAL_INTERVAL_MS=1000, STARTUP_WINDOW_MS=3000;
    private final long started;
    private long previous, notificationAt;
    public TrafficSampling(long now){started=previous=now;notificationAt=now;}
    public static final class Sample {
        public final long upPerSecond,downPerSecond,nextDelayMs;
        public final boolean updateNotification;
        Sample(long up,long down,long delay,boolean notification){upPerSecond=up;downPerSecond=down;nextDelayMs=delay;updateNotification=notification;}
    }
    public Sample sample(long now,long upBytes,long downBytes){
        long elapsed=now-previous;
        if(elapsed<=0)return new Sample(0,0,FAST_INTERVAL_MS,false);
        previous=now;
        boolean notify=now-notificationAt>=NORMAL_INTERVAL_MS;
        if(notify)notificationAt=now;
        return new Sample(rate(upBytes,elapsed),rate(downBytes,elapsed),now-started<STARTUP_WINDOW_MS?FAST_INTERVAL_MS:NORMAL_INTERVAL_MS,notify);
    }
    private static long rate(long bytes,long elapsed){
        if(bytes<=0)return 0;
        return (long)Math.min(Long.MAX_VALUE,(double)bytes*1000.0/elapsed);
    }
}
