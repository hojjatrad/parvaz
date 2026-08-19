package com.parvaz.tunnel.core;

import android.content.Context;
import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.core.SubscriptionUpdater;
import com.parvaz.tunnel.model.Subscription;
import com.parvaz.tunnel.store.ProfileStore;
import java.util.ArrayList;
import java.util.Iterator;

/* renamed from: com.parvaz.tunnel.core.c */
/* loaded from: classes.dex */
public final class SubscriptionUpdater_4 implements Runnable {
    public final SubscriptionUpdater.a b;
    public final SubscriptionUpdater c;

    public SubscriptionUpdater_4(SubscriptionUpdater subscriptionUpdater, SubscriptionUpdater.a aVar) {
        this.c = subscriptionUpdater;
        this.b = aVar;
    }

    @Override // java.lang.Runnable
    public final void run() {
        SubscriptionUpdater subscriptionUpdater = this.c;
        subscriptionUpdater.getClass();
        int[] iArr = {0};
        String[] strArr = {null};
        Context context = subscriptionUpdater.f6298a;
        Iterator it = ProfileStore.f(context).f().iterator();
        while (it.hasNext()) {
            Subscription subscription = (Subscription) it.next();
            if (subscription.enabled) {
                try {
                    SubscriptionUpdater.b a = SubscriptionUpdater.a(subscription.url);
                    ArrayList H = LinkParser.parseMany(a.f6300a);
                    if (!H.isEmpty()) {
                        subscription.applyUserinfo(a.f6301b);
                        ProfileStore f = ProfileStore.f(context);
                        f.g(subscription.id);
                        iArr[0] = iArr[0] + f.a(H, subscription.id);
                        subscription.lastUpdate = System.currentTimeMillis();
                        subscription.count = H.size();
                        f.j(subscription);
                    }
                } catch (Exception e) {
                    strArr[0] = e.getMessage();
                }
            }
        }
        subscriptionUpdater.f6299b.post(new SubscriptionUpdater_5(this.b, iArr, strArr));
    }
}
