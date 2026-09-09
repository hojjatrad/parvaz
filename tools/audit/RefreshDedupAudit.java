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
    static void primaryScenarios()throws Exception {
        PanelRefreshTest.Setup s=new PanelRefreshTest.Setup();
        for(int source=1;source<=7;source++){
            s.sub("s"+source,"https://panel"+source+".invalid/private-token");
            for(int i=0;i<14;i++){
                String json=new JSONObject().put("proxies",new JSONArray().put(new JSONObject().put("type","vless").put("name","server "+i).put("server","server"+i+".invalid").put("port",443).put("uuid",String.format("%08d-1111-4111-8111-111111111111",source)))).toString();
                Profile p=LinkParser.parseDetailed(json).profiles.get(0);p.subscriptionId="s"+source;s.add(p);
            }
        }
        Profile chosen=(Profile)s.store.e().get(14),archived=(Profile)s.store.e().get(0);
        s.prefs.edit().putString("selected_profile",chosen.id).putString("favorites",chosen.id).apply();
        s.store.setPrimarySubscription("s2");
        check("Seven independent 14-node sources become 14 active nodes",s.store.e().size()==98&&s.store.activeProfiles().size()==14);
        check("Archived IDs cannot be used to connect",s.store.getActiveById(archived.id)==null&&s.store.getActiveById(chosen.id)!=null);
        check("Choice survives a store reload",new ProfileStore(s.context).primarySubscription().equals("s2"));
        JSONArray proxies=new JSONArray();
        for(int repeat=0;repeat<2;repeat++)for(int i=0;i<14;i++)proxies.put(new JSONObject().put("type","vless").put("name","server").put("server","server"+i+".invalid").put("port",443).put("uuid","00000002-1111-4111-8111-111111111111"));
        final String valid=new JSONObject().put("proxies",proxies).toString();int[] fetched={0};
        SubscriptionRefresh.Result result=SubscriptionRefresh.run(s.store,s.prefs,()->false,url->{check("Only the primary source is fetched",url.contains("panel2.invalid"));fetched[0]++;return new SubscriptionUpdater.b(valid,null);});
        check("One source refreshed and auto-deduplicated",fetched[0]==1&&result.updated==1&&s.store.activeProfiles().size()==14&&result.visibleConnections==14);
        check("Archived ownership survives refresh",s.store.e().size()==98&&s.store.getById(archived.id)!=null);
        check("Stable selected and favorite IDs survive refresh",s.store.getActiveById(chosen.id)!=null&&s.prefs.getString("favorites","").contains(chosen.id));
        String safe=SubscriptionRefresh.safeReport(result);
        check("Report distinguishes active and archived counts",safe.contains("scope=PRIMARY")&&safe.contains("archived_records=84")&&!safe.contains("private-token"));
        SubscriptionRefresh.run(s.store,s.prefs,()->false,url->{throw new java.io.IOException();});
        check("Fetch failure does not resurrect archived sources or clear primary",s.store.activeProfiles().size()==14&&s.store.primarySubscription().equals("s2"));
        ImportResult incomplete=LinkParser.parseDetailed(valid);incomplete.reject("INVALID_NODE",1);
        SubscriptionRefresh.run(s.store,s.prefs,()->false,url->new SubscriptionUpdater.b(valid,null,0,incomplete));
        check("Partial response retains primary data safely",s.store.activeProfiles().size()==14);
        String replacement=new JSONObject().put("proxies",new JSONArray().put(new JSONObject().put("type","vless").put("name","new").put("server","replacement.invalid").put("port",443).put("uuid","99999999-1111-4111-8111-111111111111"))).toString();
        SubscriptionRefresh.run(s.store,s.prefs,()->false,url->new SubscriptionUpdater.b(replacement,null));
        check("Complete response removes old primary nodes, not archived owners",s.store.activeProfiles().size()==1&&s.store.e().size()==85);
        ProfileStore.Snapshot old=s.store.beginRefresh("s2");s.store.setPrimarySubscription("s3");
        try{s.store.replaceSubscription(old,LinkParser.parseDetailed(replacement),null,1,"");throw new AssertionError("stale accepted");}catch(ProfileStore.StaleRefresh expected){check("Scope change rejects an in-flight stale commit",true);}
        s.store.setPrimarySubscription("");check("All-source mode can restore archived records",s.store.activeProfiles().size()==85);
        s.store.setPrimarySubscription("s3");
        s.store.f347c.removeIf(o->((Subscription)o).id.equals("s3"));s.store.h();
        SubscriptionRefresh.Result missing=SubscriptionRefresh.run(s.store,s.prefs,()->false,url->{throw new AssertionError("must not fall back to other sources");});
        check("Missing primary fails closed without fetching other sources",missing.failed==1&&missing.requested==0);
        PanelRefreshTest.Setup aliases=new PanelRefreshTest.Setup();aliases.sub("a","https://same.invalid/sub");aliases.sub("b","https://same.invalid/sub");aliases.add(PanelRefreshTest.profile("b","selected"));
        aliases.store.setPrimarySubscription("b");aliases.store.removeDuplicates(aliases.prefs);
        check("URL dedup remaps primary ownership",aliases.store.primarySubscription().equals("a")&&aliases.store.activeProfiles().size()==1);
        PanelRefreshTest.Setup noisy=new PanelRefreshTest.Setup();
        for(int i=1;i<=7;i++)noisy.sub("s"+i,"https://source"+i+".invalid/private-token");
        final ImportResult parsed=LinkParser.parseDetailed(replacement);for(int i=0;i<80;i++)parsed.warn("RAW_OUTBOUND_EXTRACTION_ONLY",0);
        SubscriptionRefresh.Result partial=SubscriptionRefresh.run(noisy.store,noisy.prefs,()->false,url->{if(url.contains("source7"))throw new java.io.IOException();return new SubscriptionUpdater.b(replacement,null,0,parsed);});
        String report=SubscriptionRefresh.safeReport(partial);
        check("Six successes cannot hide the seventh failure behind warnings",partial.updated==6&&partial.failed==1&&report.contains("ERROR NETWORK_FAILURE")&&report.contains("SOURCE_7: FAILED"));
        check("Repeated warnings are aggregated",report.indexOf("RAW_OUTBOUND_EXTRACTION_ONLY")==report.lastIndexOf("RAW_OUTBOUND_EXTRACTION_ONLY")&&report.contains("occurrences=480"));
        check("Failure is prioritized for existing import consumers",partial.codes.get(0).equals("NETWORK_FAILURE"));
        check("Per-source report is credential and endpoint free",!report.contains("private-token")&&!report.contains(".invalid")&&!report.contains("99999999"));
    }
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
            java.util.concurrent.atomic.AtomicReference<Thread> manualThread=new java.util.concurrent.atomic.AtomicReference<>();
            CountDownLatch manualStarted=new CountDownLatch(1);
            Future<SubscriptionRefresh.Result> manual=pool.submit(()->{manualThread.set(Thread.currentThread());manualStarted.countDown();return SubscriptionRefresh.runOne(store.store,store.prefs,()->false,url->new SubscriptionUpdater.b(newBody,null),null,true);});
            check("Manual worker started under held lock",manualStarted.await(2,TimeUnit.SECONDS));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(!manual.isDone()&&manualThread.get().getState()!=Thread.State.TIMED_WAITING&&System.nanoTime()<deadline)Thread.sleep(5);
            check("Manual actually waits while background owns the lock",!manual.isDone()&&manualThread.get().getState()==Thread.State.TIMED_WAITING);
            release.countDown();background.get(5,TimeUnit.SECONDS);SubscriptionRefresh.Result done=manual.get(5,TimeUnit.SECONDS);
            check("Manual refresh queues and fetches instead of busy-zero result",done.updated==1&&done.requested==1&&done.failed==0);
            check("Queued refresh replaces old source data",store.store.e().size()==1&&((Profile)store.store.e().get(0)).address.equals("new.invalid"));
            String safe=SubscriptionRefresh.safeReport(done);check("Refresh report identifies operation and excludes source",safe.contains("OPERATION_REFRESH")&&!safe.contains("private-token")&&!safe.contains("one.invalid"));
        }finally{release.countDown();pool.shutdownNow();}
        primaryScenarios();
        System.out.println("REFRESH/DEDUP TOTAL: "+count+" assertions passed.");
    }
}
