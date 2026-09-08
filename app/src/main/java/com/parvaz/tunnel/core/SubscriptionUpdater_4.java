package com.parvaz.tunnel.core;

/** UI adapter: the caller already runs this on a background thread. */
public final class SubscriptionUpdater_4 implements Runnable {
    public final SubscriptionUpdater.a b;
    public final SubscriptionUpdater c;
    public SubscriptionUpdater_4(SubscriptionUpdater updater,SubscriptionUpdater.a callback){this.c=updater;this.b=callback;}
    @Override public void run() {
        SubscriptionRefresh.Result result=SubscriptionRefresh.run(c.f6298a,()->Thread.currentThread().isInterrupted());
        c.f6299b.post(()->b.onComplete(result));
    }
}
