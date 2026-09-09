package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.store.*;
import com.parvaz.tunnel.model.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class SmartImportRegressionTest {
    Context context; ProfileStore store; SharedPreferences prefs;
    private final String link="vless://11111111-1111-4111-8111-111111111111@one.example.invalid:443?security=tls&type=ws&host=cover.invalid&path=%2Fws#%D8%B3%D8%B1%D9%88%D8%B1";
    @Before public void setup(){
        context=ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("parvaz_store",0).edit().clear().commit();
        prefs=context.getSharedPreferences("parvaz_prefs",0);prefs.edit().clear().commit();
        store=new ProfileStore(context);
    }
    @Test public void nativeAndroidUriIsParsedAndRepeatedImportStaysUnique(){
        SmartImport.Result first=SmartImport.run(store,prefs,link,()->false,url->{throw new AssertionError("single server must not fetch network");});
        assertEquals(1,first.added);assertEquals(1,store.e().size());
        Profile p=(Profile)store.e().get(0);assertEquals("سرور",p.remark);assertEquals("/ws",p.path);
        SmartImport.Result second=SmartImport.run(store,prefs,link,()->false,url->{throw new AssertionError();});
        assertEquals(0,second.added);assertEquals(p.id,((Profile)store.e().get(0)).id);
    }
    @Test public void subscriptionIsFetchedOncePerImportAndKeepsId(){
        int[] requests={0};SubscriptionRefresh.Fetcher fetch=url->{requests[0]++;return new SubscriptionUpdater.b(link,"upload=0; download=430; total=1000");};
        SmartImport.run(store,prefs,"https://panel.invalid/sub/token",()->false,fetch);
        Profile p=(Profile)store.e().get(0);prefs.edit().putString("selected_profile",p.id).apply();store.i(p.id,25);
        SmartImport.run(store,prefs,"HTTPS://PANEL.INVALID:443/sub/token#label",()->false,fetch);
        assertEquals(2,requests[0]);assertEquals(1,store.f().size());assertEquals(1,store.e().size());assertEquals(25,store.getById(p.id).ping);
        Subscription s=QuotaState.source(store.getById(p.id),store.f());assertTrue(QuotaState.known(s));assertEquals(43,s.quotaPercent());
        s.url="";assertNull(QuotaState.source(p,Arrays.asList(s)));
    }
    @Test public void noLinkNeverReusesManualPlanOrUnrelatedQuota(){
        prefs.edit().putFloat("manual_service_total_gb",10).putLong("data_down",4300000000L).apply();
        Subscription old=new Subscription();old.id="old";old.url="https://old.invalid/s";old.replaceUserinfo("upload=0;download=43;total=100",12345);
        Profile p=LinkParser.parseMany(link).get(0);
        assertNull(QuotaState.source(p,Arrays.asList(old)));assertNull(QuotaState.source(null,Arrays.asList(old)));
    }
    @Test public void missingQuotaIsUnknownRatherThanFrozen(){
        Subscription s=new Subscription();s.replaceUserinfo("upload=0;download=43;total=100",12345);assertEquals(43,s.quotaPercent());
        s.replaceUserinfo(null,23456);assertFalse(QuotaState.known(s));assertEquals(0,s.quotaUsed());
    }
    @Test public void cleanupPreservesSelectedAndFavoriteAcrossReload(){
        SmartImport.run(store,prefs,link,()->false,url->{throw new AssertionError();});
        Profile p=(Profile)store.e().get(0),copy=ProfileIdentity.copy(p);copy.id="copy";
        store.f346b.add(copy);store.h();prefs.edit().putString("selected_profile",p.id).putString("favorites",copy.id).apply();
        assertEquals(1,store.removeDuplicates(prefs));assertNotNull(store.getById(p.id));assertTrue(prefs.getString("favorites","").contains(p.id));
        assertEquals(1,new ProfileStore(context).e().size());
    }
    @Test public void quotaViewResetsFrozenPercentageAndShowsUniqueRows(){
        Profile manual=LinkParser.parseMany(link).get(0);store.a(new ArrayList<>(Arrays.asList(manual)),"");
        prefs.edit().putString("selected_profile",manual.id).putFloat("manual_service_total_gb",10).putLong("data_down",4300000000L).apply();
        ProfileStore.d=store;
        org.robolectric.android.controller.ActivityController<com.parvaz.tunnel.MainActivity> controller=org.robolectric.Robolectric.buildActivity(com.parvaz.tunnel.MainActivity.class).create();
        com.parvaz.tunnel.MainActivity activity=controller.get();
        try {
            activity.quotaPercentText.setText("43%");activity.quotaBar.setProgress(43);activity.renderQuota();
            assertEquals("0%",activity.quotaPercentText.getText().toString());assertEquals(0,activity.quotaBar.getProgress());
            SmartImport.run(store,prefs,"https://panel.invalid/s",()->false,url->new SubscriptionUpdater.b(link,"upload=0;download=43;total=100"));
            Profile owned=null;for(Object o:store.e()){Profile p=(Profile)o;if(!p.subscriptionId.isEmpty())owned=p;}
            assertNotNull(owned);prefs.edit().putString("favorites",owned.id).apply();
            activity.favOnly=true;activity.reload();
            assertEquals(1,activity.z.getItemCount());assertTrue(activity.z.visibleFavorites.contains(manual.id));
            activity.renderQuota();assertEquals("43%",activity.quotaPercentText.getText().toString());
            Subscription sub=(Subscription)store.f().get(0);sub.url="";store.j(sub);activity.renderQuota();
            assertEquals("0%",activity.quotaPercentText.getText().toString());
        }finally{controller.destroy();ProfileStore.d=null;}
    }
    @Test public void duplicateButtonCleansDefaultVariantsAndReportsDistinctCredentials()throws Exception {
        Profile a=LinkParser.parseMany("vless://11111111-1111-4111-8111-111111111111@test.invalid:443#one").get(0);
        Profile b=ProfileIdentity.copy(a);b.id="duplicate";b.network="";b.security="none";b.encryption="";
        Profile other=ProfileIdentity.copy(a);other.id="different-credential";other.uuid="22222222-2222-4222-8222-222222222222";
        store.f346b.add(a);store.f346b.add(b);store.f346b.add(other);store.h();ProfileStore.d=store;
        org.robolectric.android.controller.ActivityController<com.parvaz.tunnel.MainActivity> controller=org.robolectric.Robolectric.buildActivity(com.parvaz.tunnel.MainActivity.class).create();
        try {
            com.parvaz.tunnel.MainActivity activity=controller.get();activity.reload();assertEquals(2,activity.z.getItemCount());
            activity.new F().onClick(null,7);
            ((androidx.appcompat.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog()).getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(2,store.e().size());assertEquals(2,activity.z.getItemCount());
            String report=prefs.getString("last_duplicate_report","");assertTrue(report.contains("removed_records=1"));assertTrue(report.contains("uuid"));
            assertFalse(report.contains(a.uuid));assertFalse(report.contains(a.address));
            assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());
        }finally{controller.destroy();ProfileStore.d=null;}
    }
    @Test public void updateVersionAndMetadataGuards(){
        assertTrue(UpdateChecker.isNewer("v1.20","1.19.1"));assertFalse(UpdateChecker.isNewer("1.19.1","1.20"));
        UpdateChecker.Release r=new UpdateChecker.Release();r.version="1.20";r.size=1024;r.sha256=String.join("",Collections.nCopies(64,"a"));
        r.downloadUrl="https://github.com/hojjatrad/parvaz/releases/download/v1.20/Parvaz-1.20.apk";assertTrue(r.valid());
        r.downloadUrl="https://evil.invalid/app.apk";assertFalse(r.valid());
        r.version="../../file";assertFalse(r.valid());
    }
}
