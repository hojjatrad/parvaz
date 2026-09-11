package com.parvaz.tunnel.core;

import android.content.*;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.*;
import com.parvaz.tunnel.store.*;
import com.sun.net.httpserver.HttpServer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Actual parser/store/refresh/Worker classes with in-memory Android API substitutes.
 * NOT Android disk crash recovery, WorkManager scheduling or native VPN tests. */
public class PanelRefreshTest {
    static int checks;
    static void check(String name,boolean value){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS B: "+name);}
    interface Action {void run()throws Exception;}
    static void rejects(String name,Action action)throws Exception{try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){check(name,true);return;}throw new AssertionError(name);}
    static String fixture(String name)throws Exception{return Files.readString(Paths.get("app/src/test/resources/import",name),StandardCharsets.UTF_8);}
    static Profile first(String s){return LinkParser.parseMany(s).get(0);}
    static Profile profile(String owner,String name){Profile p=new Profile();p.protocol="vless";p.address="server.example.invalid";p.port=443;p.uuid="11111111-1111-4111-8111-111111111111";p.remark=name;p.subscriptionId=owner;return p.normalize();}
    static ImportResult accepted(Profile...profiles){ImportResult r=new ImportResult();for(Profile p:profiles)r.add(p);return r;}
    static class MemoryPrefs implements SharedPreferences {
        final Map<String,String> values=new HashMap<>();int writes;boolean failNext;Set<String> lastKeys;
        public synchronized String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
        public int getInt(String key,int fallback){return fallback;}
        public boolean getBoolean(String key,boolean fallback){return fallback;}
        public Editor edit(){return new Editor(){Map<String,String> pending=new HashMap<>();public Editor putString(String k,String v){pending.put(k,v);return this;}public void apply(){synchronized(MemoryPrefs.this){if(failNext){failNext=false;throw new IllegalStateException("injected write failure");}values.putAll(pending);writes++;lastKeys=new HashSet<>(pending.keySet());}}};}
    }
    static class MemoryContext extends Context {
        final Map<String,MemoryPrefs> stores=new HashMap<>();
        @Override public MemoryPrefs getSharedPreferences(String name,int mode){return stores.computeIfAbsent(name,k->new MemoryPrefs());}
    }
    static class Setup {
        final MemoryContext context=new MemoryContext();
        final ProfileStore store=new ProfileStore(context);
        final MemoryPrefs prefs=context.getSharedPreferences("parvaz_prefs",0),disk=context.getSharedPreferences("parvaz_store",0);
        void sub(String id,String url){Subscription s=new Subscription();s.id=id;s.url=url;s.name="local name";s.quotaTotal=1000;s.quotaDownload=10;store.f347c.add(s);store.h();}
        void add(Profile p){store.a(new ArrayList<>(Arrays.asList(p)),p.subscriptionId);}
    }
    public static void main(String[]args)throws Exception{
        String yaml=fixture("clash-mixed.yaml"),json=fixture("sing-box-mixed.json");
        ImportResult clash=LinkParser.parseDetailed(yaml);
        check("Clash mixed inline/nested YAML imports four nodes",clash.profiles.size()==4&&clash.rejected==0);
        check("Clash groups/rules explicitly warned",clash.warnings>0&&clash.safeToReplace());
        check("YAML password yes is a string, not boolean",clash.profiles.get(0).uuid.equals("yes"));
        Profile reality=clash.profiles.get(1),ws=clash.profiles.get(2),wg=clash.profiles.get(3);
        check("Leading-zero Reality short-id preserved",reality.shortId.equals("00123456"));
        check("Clash gRPC service/SNI/ALPN mapped",reality.serviceName.equals("demo/service")&&reality.sni.equals("cover.example.invalid")&&reality.alpn.equals("h2,http/1.1"));
        check("Clash Reality key and TLS mode mapped",reality.security.equals("reality")&&reality.publicKey.equals("dummy-public-key"));
        check("Clash WS nested headers/path preserved",ws.host.equals("edge.example.invalid")&&ws.path.equals("/test?a=1&b=2"));
        check("Clash TLS verification flag preserved",!ws.allowInsecure);
        check("Clash WG addresses, preshared key, reserved and MTU mapped",wg.localAddress.equals("10.7.0.2/32,fd00::2/128")&&wg.presharedKey.equals("dummy-preshared-key")&&wg.reserved.equals("1,2,255")&&wg.wgMtu==1280);
        check("WG builder brackets IPv6",XrayConfigBuilder.c(wg,new Prefs()).getJSONObject("settings").getJSONArray("peers").getJSONObject(0).getString("endpoint").equals("[2001:db8::1]:51820"));
        String inline="proxies: [{name: demo, type: ss, server: demo.example.invalid, port: 443, cipher: aes-128-gcm, password: dummy}]";
        check("Whole inline proxies array supported",LinkParser.parseDetailed(inline).safeToReplace());
        check("Flow-style YAML document supported",LinkParser.parseMany("{proxies: [{type: ss, server: demo.example.invalid, port: 443, cipher: aes-128-gcm, password: dummy}]}").size()==1);
        check("JSON Clash document supported",LinkParser.parseMany("{\"proxies\":[{\"type\":\"ss\",\"server\":\"demo.example.invalid\",\"port\":443,\"cipher\":\"aes-128-gcm\",\"password\":\"dummy\"}]}").size()==1);
        check("Duplicate YAML keys rejected",!LinkParser.parseDetailed("proxies: []\nproxies: []\n").safeToReplace());
        check("YAML aliases rejected including scalars",LinkParser.parseDetailed("proxies:\n - {type: ss, server: &s demo.example.invalid, port: 443, cipher: aes-128-gcm, password: *s}").fatal);
        check("Arbitrary Java YAML tags rejected",ClashParser.parseDetailed("proxies: !!java.util.ArrayList []").fatal);
        check("Multiple YAML documents rejected",ClashParser.parseDetailed(inline+"\n---\n"+inline).fatal);
        check("Malformed YAML rejected",ClashParser.parseDetailed("proxies: [ {bad").fatal);
        ImportResult partial=LinkParser.parseDetailed(yaml.replace("port: 8443","port: not-a-number"));
        check("Bad port not silently repaired",partial.profiles.size()==3&&partial.rejected==1&&!partial.safeToReplace());
        check("Missing VMess UUID rejected",LinkParser.parseDetailed(yaml.replace("22222222-2222-4222-8222-222222222222","not-a-uuid")).rejected==1);
        ImportResult plugin=LinkParser.parseDetailed(inline.replace("password: dummy","password: secret-token, plugin: unsupported-plugin"));
        check("Unsupported SS plugin rejected instead of stripped",plugin.rejected==1&&plugin.profiles.isEmpty());
        check("Diagnostics contain no credential/input text",!plugin.issues.toString().contains("secret-token")&&!plugin.issues.toString().contains("unsupported-plugin"));
        check("Unsupported WS early-data option rejected",LinkParser.parseDetailed(yaml.replace("      path:","      max-early-data: 2048\n      path:")).rejected==1);
        check("Base64 wrapping retains diagnostics",LinkParser.parseDetailed(Base64.getEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8))).warnings==clash.warnings);
        ImportResult sing=LinkParser.parseDetailed(json);
        check("Sing-box mixed outbounds mapped",sing.profiles.size()==4&&sing.rejected==0&&sing.safeToReplace());
        check("Sing-box selectors/routing reported",sing.warnings>=2);
        check("Sing-box ALPN/uTLS mapped",sing.profiles.get(0).alpn.equals("h2,http/1.1")&&sing.profiles.get(0).fingerprint.equals("firefox"));
        check("Sing-box WS settings preserved",sing.profiles.get(0).host.equals("edge.example.invalid")&&sing.profiles.get(0).path.equals("/ws"));
        check("Sing-box Reality/gRPC mapped",sing.profiles.get(1).shortId.equals("00abcd")&&sing.profiles.get(1).serviceName.equals("demo"));
        check("Sing-box SOCKS credentials preserved",sing.profiles.get(2).uuid.equals("test-user")&&sing.profiles.get(2).quicKey.equals("dummy-password"));
        check("Sing-box legacy WireGuard mapped",sing.profiles.get(3).uuid.equals("dummy-private")&&sing.profiles.get(3).localAddress.equals("10.8.0.2/32,fd00:1::2/128"));
        Profile endpoint=first(fixture("wireguard-endpoint.json"));
        check("Sing-box new WireGuard endpoint mapped",endpoint.address.equals("2001:db8::3")&&endpoint.reserved.equals("3,4,5")&&endpoint.presharedKey.equals("dummy-preshared"));
        check("Sing-box detour not silently dropped",LinkParser.parseDetailed(json.replace("\"tag\":\"ws\"","\"tag\":\"ws\",\"detour\":\"auth\"")).rejected==1);
        check("Sing-box TLS certificate pin not silently dropped",LinkParser.parseDetailed(json.replace("\"alpn\":[","\"certificate\":\"dummy-cert\",\"alpn\":[")).rejected==1);
        check("Sing-box malformed node preserves other results with error",LinkParser.parseDetailed(json.replace("\"server_port\":1080","\"server_port\":99999")).rejected==1);
        String vmess="vmess://"+Base64.getEncoder().encodeToString("{\"v\":\"2\",\"add\":\"demo.example.invalid\",\"port\":\"443\",\"id\":\"11111111-1111-4111-8111-111111111111\",\"net\":\"tcp\"}".getBytes(StandardCharsets.UTF_8));
        check("Explicit panel links envelope supported",LinkParser.parseDetailed(new JSONObject().put("links",new JSONArray().put(vmess)).put("data_limit",1000).toString()).safeToReplace());
        check("Mixed link text marked partial",!LinkParser.parseDetailed(vmess+"\nnot-a-link").safeToReplace());
        check("Metadata-only JSON not refreshable",!LinkParser.parseDetailed("{\"data_limit\":1000}").safeToReplace());
        check("Empty subscription never authorizes deletion",!LinkParser.parseDetailed("[]").safeToReplace());
        Profile old=profile("a","old name"),renamed=ProfileIdentity.copy(old);old.ping=42;renamed.id="untrusted-new-id";renamed.remark="new name";renamed.rawLink="changed-format";
        check("Identity excludes display name/raw link/internal ID",ProfileIdentity.fingerprint(old).equals(ProfileIdentity.fingerprint(renamed)));
        for(String field:new String[]{"sni","publicKey","shortId","flow","quicKey","encryption","allowInsecure"}) {
            Profile changed=ProfileIdentity.copy(old);
            if(field.equals("allowInsecure"))changed.allowInsecure=true;else Profile.class.getField(field).set(changed,"different");
            check("Identity includes "+field,!ProfileIdentity.fingerprint(old).equals(ProfileIdentity.fingerprint(changed)));
        }
        String raw1="{\"outbounds\":[{\"protocol\":\"socks\",\"settings\":{\"servers\":[{\"address\":\"demo.example.invalid\",\"port\":1080}]}}]}";
        String raw2="{\"remarks\":\"renamed\",\"outbounds\":[{\"settings\":{\"servers\":[{\"port\":1080,\"address\":\"demo.example.invalid\"}]},\"protocol\":\"socks\"}]}";
        check("Custom JSON identity independent of key order and remarks",ProfileIdentity.fingerprint(first(raw1)).equals(ProfileIdentity.fingerprint(first(raw2))));
        Profile other=profile("b","other"),manual=profile("","manual");
        SubscriptionReconciler.Plan plan=SubscriptionReconciler.plan(Arrays.asList(old,other,manual),Arrays.asList(renamed,ProfileIdentity.copy(renamed)),"a",old.id);
        check("Reconcile retains ID/ping and updates remote name",plan.all.get(0).id.equals(old.id)&&plan.all.get(0).ping==42&&plan.all.get(0).remark.equals("new name"));
        check("Reconcile deduplicates within owner only",plan.count==1&&plan.duplicates==1&&plan.all.size()==3);
        check("Other subscription/manual profiles untouched",plan.all.get(1)==other&&plan.all.get(2)==manual);
        Profile changed=ProfileIdentity.copy(old);changed.uuid="22222222-2222-4222-8222-222222222222";
        SubscriptionReconciler.Plan rotated=SubscriptionReconciler.plan(Arrays.asList(old),Arrays.asList(changed),"a",old.id);
        check("Credential rotation creates new identity, not stale stats",!rotated.all.get(0).id.equals(old.id)&&rotated.all.get(0).ping==-1&&rotated.added==1&&rotated.removed==1);
        rejects("Empty plan rejected",()->SubscriptionReconciler.plan(Arrays.asList(old),Collections.emptyList(),"a",old.id));
        Setup setup=new Setup();setup.sub("a","https://panel.example.invalid/a");setup.sub("b","https://panel.example.invalid/b");setup.add(old);setup.add(other);setup.add(manual);
        setup.store.i(old.id,42);setup.prefs.edit().putString("selected_profile",old.id).putString("favorites",old.id).apply();
        int before=setup.disk.writes;
        ProfileStore.Snapshot snapshot=setup.store.beginRefresh("a");
        setup.store.replaceSubscription(snapshot,accepted(renamed),"upload=2; download=20; total=2000",12345,old.id);
        check("Store refresh performs exactly ONE edit",setup.disk.writes==before+1&&setup.disk.lastKeys.equals(new HashSet<>(Arrays.asList("profiles","subs","pings_https_rtt_v1"))));
        check("Store retains selected/favorite IDs without preference rewrite",setup.prefs.getString("selected_profile","").equals(old.id)&&setup.prefs.getString("favorites","").equals(old.id)&&setup.store.getById(old.id)!=null);
        Subscription updated=(Subscription)setup.store.f().get(0);
        check("Quota/time/count updated in same snapshot",updated.quotaDownload==20&&updated.quotaTotal==2000&&updated.lastUpdate==12345&&updated.count==1);
        ProfileStore reconstructed=new ProfileStore(setup.context);
        check("Reloading stored snapshot retains ID/ping",reconstructed.getById(old.id)!=null&&reconstructed.getById(old.id).ping==42);
        before=setup.disk.writes;
        rejects("Partial parser result cannot replace stored list",()->setup.store.replaceSubscription(setup.store.beginRefresh("a"),partial,null,999,old.id));
        check("Partial result does not write or remove existing profiles",setup.disk.writes==before&&setup.store.getById(old.id)!=null);
        ProfileStore.Snapshot stale=setup.store.beginRefresh("a");setup.store.h();
        rejects("Changed generation rejects stale download",()->setup.store.replaceSubscription(stale,accepted(renamed),null,999,old.id));
        ProfileStore.Snapshot meta=setup.store.beginRefresh("a");meta.subscription.quotaTotal=9;
        check("Snapshot metadata detached from store",((Subscription)setup.store.f().get(0)).quotaTotal==2000);
        setup.disk.failNext=true;String stored=setup.disk.getString("profiles","");
        rejects("Injected apply exception propagates",()->setup.store.replaceSubscription(setup.store.beginRefresh("a"),accepted(changed),null,999,old.id));
        check("Failed apply leaves old memory and fake storage intact",setup.store.getById(old.id)!=null&&setup.disk.getString("profiles","").equals(stored));
        Setup dedup=new Setup();dedup.sub("a","a");dedup.sub("b","b");dedup.add(old);dedup.add(other);dedup.add(manual);dedup.add(ProfileIdentity.copy(old));
        check("Manual/store add respects subscription ownership",dedup.store.e().size()==3);
        Profile different=ProfileIdentity.copy(old);different.publicKey="different";dedup.add(different);
        check("Store add does not collapse different Reality settings",dedup.store.e().size()==4);
        Setup engine=new Setup();engine.sub("a","https://panel.example.invalid/a");engine.sub("b","https://panel.example.invalid/b");engine.add(old);engine.add(other);
        SubscriptionRefresh.Result mixed=SubscriptionRefresh.run(engine.store,engine.prefs,()->false,url->{if(url.endsWith("b"))throw new java.io.IOException("secret-token");return new SubscriptionUpdater.b(raw1,null);});
        check("Partial refresh reports success AND failure",mixed.updated==1&&mixed.failed==1&&mixed.retryable&&mixed.errorSummary()!=null);
        check("Failed subscription is retained",engine.store.getById(other.id)!=null);
        check("Engine errors redact secrets",!mixed.errorSummary().contains("secret-token"));
        String engineDisk=engine.disk.getString("profiles","");
        SubscriptionRefresh.Result bad=SubscriptionRefresh.run(engine.store,engine.prefs,()->false,url->new SubscriptionUpdater.b(vmess+"\nbroken",null));
        boolean oldKept=true;JSONArray beforePartial=new JSONArray(engineDisk);
        for(int i=0;i<beforePartial.length();i++)oldKept &= engine.store.getById(beforePartial.getJSONObject(i).getString("id"))!=null;
        check("Partial import adds valid nodes without deleting old records",bad.updated==0&&bad.failed==2&&!bad.retryable&&oldKept);
        engineDisk=engine.disk.getString("profiles","");
        AtomicBoolean cancel=new AtomicBoolean();SubscriptionRefresh.Result cancelled=SubscriptionRefresh.run(engine.store,engine.prefs,cancel::get,url->{cancel.set(true);return new SubscriptionUpdater.b(raw1,null);});
        check("Cancellation after download prevents persistence",cancelled.cancelled&&cancelled.updated==0&&engine.disk.getString("profiles","").equals(engineDisk));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> threadFailure=new AtomicReference<>();
        Thread thread=new Thread(()->{try{SubscriptionRefresh.run(engine.store,engine.prefs,()->false,url->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException();}catch(InterruptedException e){throw new java.io.IOException();}return new SubscriptionUpdater.b(raw1,null);});}catch(Throwable e){threadFailure.set(e);}});
        thread.start();check("Concurrent test reached download",entered.await(5,TimeUnit.SECONDS));
        try {SubscriptionRefresh.Result busy=SubscriptionRefresh.run(engine.store,engine.prefs,()->false,url->{throw new AssertionError("duplicate network request");});check("Concurrent refresh coalesced as retryable busy",busy.retryable&&busy.failed==1&&busy.codes.contains("REFRESH_ALREADY_RUNNING"));}
        finally{release.countDown();thread.join(5000);}
        check("Concurrent job terminates cleanly",!thread.isAlive()&&threadFailure.get()==null);
        AtomicReference<String> callback=new AtomicReference<>();new SubscriptionUpdater_5((error,n)->callback.set(error),new int[]{3},new String[]{"one failed"}).run();
        check("Legacy callback no longer hides partial errors",callback.get().equals("one failed"));
        check("Retry classification: TLS permanent",!SubscriptionRefresh.transientError(new SubscriptionHttpClient.FetchException(SubscriptionHttpClient.Error.TLS_FAILURE)));
        check("Retry classification: 429 transient",SubscriptionRefresh.transientError(new SubscriptionHttpClient.FetchException(SubscriptionHttpClient.Error.HTTP_STATUS,429)));
        check("Retry classification: 401 permanent",!SubscriptionRefresh.transientError(new SubscriptionHttpClient.FetchException(SubscriptionHttpClient.Error.HTTP_STATUS,401)));
        // Actual Worker.doWork with fake Android base class + actual loopback HTTP transport.
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/ok",e->{byte[] body=raw1.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,body.length);try(java.io.OutputStream out=e.getResponseBody()){out.write(body);}e.close();});
        server.createContext("/fail",e->{e.sendResponseHeaders(503,-1);e.close();});server.start();
        try {
            Setup workerSetup=new Setup();workerSetup.sub("a","http://127.0.0.1:"+server.getAddress().getPort()+"/ok");ProfileStore.d=workerSetup.store;
            SubscriptionWorker worker=new SubscriptionWorker(workerSetup.context,new androidx.work.WorkerParameters());
            check("Worker returns success for real local HTTP good payload",worker.doWork().kind.equals("success"));
            Subscription sub=(Subscription)workerSetup.store.f347c.get(0);sub.url="http://127.0.0.1:"+server.getAddress().getPort()+"/fail";workerSetup.store.h();
            check("Worker returns retry for HTTP 503",worker.doWork().kind.equals("retry"));
            sub=(Subscription)workerSetup.store.f347c.get(0);sub.url="file:///secret-token";workerSetup.store.h();
            check("Worker returns failure for permanent invalid URL",worker.doWork().kind.equals("failure"));
            worker.stopForTest();check("Stopped Worker returns retry without new fetch",worker.doWork().kind.equals("retry"));
        }finally{server.stop(0);ProfileStore.d=null;}
        check("A word in a URI fragment is not a YAML document",!ClashParser.isClash("vless://dummy@example.invalid:443#my proxies: demo"));
        Profile duplicate=ProfileIdentity.copy(old);duplicate.id="second-existing-id";duplicate.ping=90;
        SubscriptionReconciler.Plan prefer=SubscriptionReconciler.plan(Arrays.asList(old,duplicate),Arrays.asList(renamed),"a",duplicate.id);
        check("Legacy duplicate matching prefers selected ID",prefer.all.get(0).id.equals(duplicate.id)&&prefer.all.get(0).ping==90);
        Setup staleEngine=new Setup();staleEngine.sub("a","https://panel.example.invalid/a");staleEngine.add(old);
        SubscriptionRefresh.Result staleResult=SubscriptionRefresh.run(staleEngine.store,staleEngine.prefs,()->false,url->{staleEngine.store.h();return new SubscriptionUpdater.b(raw1,null);});
        check("Engine rejects responses invalidated during fetch",staleResult.updated==0&&staleResult.retryable&&staleEngine.store.getById(old.id)!=null);
        ProfileStore.Snapshot disabled=staleEngine.store.beginRefresh("a");((Subscription)staleEngine.store.f347c.get(0)).enabled=false;
        rejects("Disabled subscription cannot be republished",()->staleEngine.store.replaceSubscription(disabled,accepted(renamed),null,100,old.id));
        check("Disabled subscription is not downloaded",SubscriptionRefresh.run(staleEngine.store,staleEngine.prefs,()->false,url->{throw new AssertionError();}).skipped==1);
        Profile legacyCustom=first(raw1);legacyCustom.rawJson="[".repeat(70)+"]".repeat(70);
        check("Legacy overly nested custom is safely rejected by capability filter",!ProtocolSupport.isSupported(legacyCustom));
        check("Legacy invalid custom can still be fingerprinted without recursion",!ProfileIdentity.fingerprint(legacyCustom).isEmpty());
        check("Foreign protocol fields are not silently ignored",LinkParser.parseDetailed(inline.replace("password: dummy","password: dummy, public-key: secret-token")).rejected==1);
        check("Password without username not silently discarded",LinkParser.parseDetailed(json.replace("\"username\":\"test-user\",","")).rejected==1);
        check("Explicit insecure TLS generates warning",LinkParser.parseDetailed(yaml.replace("skip-cert-verify: false","skip-cert-verify: true")).warnings>clash.warnings);
        check("Concatenated JSON does not silently import only first config",!LinkParser.parseDetailed(raw1+"\n"+raw1).safeToReplace());
        check("Trailing JSON garbage rejected",!LinkParser.parseDetailed("["+raw1+"]secret-token").safeToReplace());
        System.out.println("PHASE B TOTAL: "+checks+" assertions passed (test substitutes, not Android device/durability tests).");
    }
}
