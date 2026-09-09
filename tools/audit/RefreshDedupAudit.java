package com.parvaz.tunnel.core;
import com.parvaz.tunnel.model.*;
import com.parvaz.tunnel.store.*;
import com.parvaz.tunnel.config.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
public class RefreshDedupAudit {
    static int count;
    static void check(String text,boolean yes){if(!yes)throw new AssertionError(text);count++;System.out.println("PASS REFRESH/DEDUP: "+text);}
    static Profile profile(){return PanelRefreshTest.profile("one","name");}
    static String key(Profile p){return ProfileIdentity.fingerprint(p);}
    public static void main(String[] args)throws Exception {
        Profile a=profile(),b=ProfileIdentity.copy(a);b.id="two";b.remark="second";b.network="";b.security="none";b.encryption="";b.wgMtu=1280;
        check("Explicit defaults collapse across imported records",key(a).equals(key(b)));
        check("Default equivalence matches actual builder",XrayConfigBuilder.c(a,new Prefs()).toString().equals(XrayConfigBuilder.c(b,new Prefs()).toString()));
        b.uuid="22222222-2222-4222-8222-222222222222";check("Different credentials never collapse",!key(a).equals(key(b)));
        b=ProfileIdentity.copy(a);a.network=b.network="ws";a.security=b.security="tls";b.path="/";b.sni=b.address;b.alpn="h2, http/1.1";a.alpn="h2,http/1.1";
        check("Default WS path, inferred SNI and ALPN whitespace collapse",key(a).equals(key(b)));
        check("WS normalization agrees with actual outbound",XrayConfigBuilder.c(a,new Prefs()).toString().equals(XrayConfigBuilder.c(b,new Prefs()).toString()));
        b.allowInsecure=true;check("TLS verification difference retained",!key(a).equals(key(b)));b.allowInsecure=false;
        b.path="/other";check("Different transport paths retained",!key(a).equals(key(b)));
        a=profile();b=ProfileIdentity.copy(a);a.network=b.network="grpc";a.path="/rpc";b.serviceName="rpc";
        check("gRPC fallback service is equivalent",key(a).equals(key(b)));
        check("gRPC equivalence matches builder",XrayConfigBuilder.c(a,new Prefs()).toString().equals(XrayConfigBuilder.c(b,new Prefs()).toString()));
        a=profile();b=ProfileIdentity.copy(a);a.security=b.security="reality";b.fingerprint="chrome";
        check("Reality default fingerprint normalized",key(a).equals(key(b)));
        b=ProfileIdentity.copy(a);b.subscriptionId="two";b.id="independent";
        check("Independent owners show one row",ProfileDuplicates.visible(Arrays.asList(a,b),"",Collections.emptySet()).size()==1);
        PanelRefreshTest.Setup setup=new PanelRefreshTest.Setup();setup.sub("one","http://one.invalid/sub");setup.sub("two","http://two.invalid/sub");setup.add(a);setup.add(b);
        setup.store.removeDuplicates(setup.prefs);check("Independent owner records retained",setup.store.e().size()==2);
        b=ProfileIdentity.copy(a);b.uuid="private-other-uuid";
        String report=DuplicateReport.safe(Arrays.asList(a,b),0);
        check("Same endpoint difference names are actionable",report.contains("same_endpoint_different_connections=1")&&report.contains("uuid"));
        check("Duplicate report contains no endpoint or credential",!report.contains(a.address)&&!report.contains(a.uuid)&&!report.contains(b.uuid));
        PanelRefreshTest.Setup empty=new PanelRefreshTest.Setup();
        SubscriptionRefresh.Result nothing=SubscriptionRefresh.run(empty.store,empty.prefs,()->false,url->{throw new AssertionError();});
        check("No subscriptions not reported as successful refresh",nothing.failed==1&&nothing.codes.contains("NO_SUBSCRIPTIONS"));
        setup=new PanelRefreshTest.Setup();setup.sub("one","http://one.invalid/private-token");final PanelRefreshTest.Setup store=setup;
        final String oldBody=SmartImportAudit.raw("old.invalid"),newBody=SmartImportAudit.raw("new.invalid");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<SubscriptionRefresh.Result> background=pool.submit(()->SubscriptionRefresh.run(store.store,store.prefs,()->false,url->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new java.io.IOException();}return new SubscriptionUpdater.b(oldBody,null);}));
            check("Background refresh entered",entered.await(5,TimeUnit.SECONDS));
            Future<SubscriptionRefresh.Result> manual=pool.submit(()->SubscriptionRefresh.runOne(store.store,store.prefs,()->false,url->new SubscriptionUpdater.b(newBody,null),null,true));
            release.countDown();background.get(5,TimeUnit.SECONDS);SubscriptionRefresh.Result done=manual.get(5,TimeUnit.SECONDS);
            check("Manual refresh queues and fetches instead of busy-zero result",done.updated==1&&done.requested==1&&done.failed==0);
            check("Queued refresh replaces old source data",store.store.e().size()==1&&((Profile)store.store.e().get(0)).address.equals("new.invalid"));
            String safe=SubscriptionRefresh.safeReport(done);check("Refresh report identifies operation and excludes source",safe.contains("OPERATION_REFRESH")&&!safe.contains("private-token")&&!safe.contains("one.invalid"));
        }finally{release.countDown();pool.shutdownNow();}
        System.out.println("REFRESH/DEDUP TOTAL: "+count+" assertions passed.");
    }
}
