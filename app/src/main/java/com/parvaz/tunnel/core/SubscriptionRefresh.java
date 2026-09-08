package com.parvaz.tunnel.core;

import android.content.Context;
import android.content.SharedPreferences;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Subscription;
import com.parvaz.tunnel.store.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Shared synchronous refresh engine for UI and WorkManager. No fire-and-forget child threads. */
public final class SubscriptionRefresh {
    private static final AtomicBoolean ACTIVE=new AtomicBoolean();
    private SubscriptionRefresh() {}
    interface Fetcher { SubscriptionUpdater.b fetch(String url) throws IOException; }
    public static final class Result {
        public int updated, failed, skipped, serverCount, added, retained, removed, warnings;
        public boolean retryable, cancelled;
        public final ArrayList<String> codes=new ArrayList<>();
        void fail(String code,boolean retry){failed++;retryable|=retry;if(codes.size()<10)codes.add(code);}
        public String errorSummary(){
            if(failed==0)return null;
            StringBuilder summary=new StringBuilder("Updated="+updated+"; failed="+failed+"; ");
            for(int i=0;i<codes.size();i++){if(i>0)summary.append(", ");summary.append(codes.get(i));}
            return summary.toString();
        }
    }
    public static Result run(Context context,BooleanSupplier cancelled) {
        return run(ProfileStore.f(context),context.getApplicationContext().getSharedPreferences("parvaz_prefs",0),cancelled,SubscriptionUpdater::a);
    }
    static Result run(ProfileStore store,SharedPreferences prefs,BooleanSupplier cancelled,Fetcher fetcher) {
        Result result=new Result();
        if(!ACTIVE.compareAndSet(false,true)){result.fail("REFRESH_ALREADY_RUNNING",true);return result;}
        try {
            for(Object o:store.f()) {
                if(stop(cancelled,result))break;
                Subscription listed=(Subscription)o;
                ProfileStore.Snapshot snapshot=store.beginRefresh(listed.id);
                if(snapshot==null){result.skipped++;continue;}
                try {
                    SubscriptionUpdater.b response=fetcher.fetch(snapshot.subscription.url);
                    if(stop(cancelled,result))break;
                    ImportResult parsed=LinkParser.parseDetailed(response.f6300a);
                    result.warnings+=parsed.warnings;
                    if(!parsed.safeToReplace()) {
                        result.fail(parsed.profiles.isEmpty()?"NO_VALID_CONFIGURATIONS":"PARTIAL_IMPORT_OLD_LIST_KEPT",false);
                        continue;
                    }
                    if(stop(cancelled,result))break;
                    SubscriptionReconciler.Plan plan=store.replaceSubscription(snapshot,parsed,response.f6301b,
                            System.currentTimeMillis(),prefs.getString("selected_profile",""));
                    result.updated++;result.serverCount+=plan.count;result.added+=plan.added;result.retained+=plan.retained;result.removed+=plan.removed;
                }catch(ProfileStore.StaleRefresh e){result.fail("STALE_RESPONSE_IGNORED",true);}
                 catch(SubscriptionHttpClient.FetchException e){result.fail(e.error.name(),transientError(e));}
                 catch(IOException e){result.fail("NETWORK_FAILURE",true);}
                 catch(IllegalArgumentException e){result.fail("INVALID_SUBSCRIPTION",false);}
                 catch(Exception e){result.fail("REFRESH_FAILED",true);}
            }
        }catch(Exception e){result.fail("REFRESH_FAILED",true);}
        finally{ACTIVE.set(false);}
        return result;
    }
    private static boolean stop(BooleanSupplier cancelled,Result result) {
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()) {
            result.cancelled=true;result.fail("CANCELLED",true);return true;
        }
        return false;
    }
    static boolean transientError(SubscriptionHttpClient.FetchException error) {
        switch(error.error) {
            case TIMEOUT:case NETWORK_FAILURE:return true;
            case HTTP_STATUS:return error.httpStatus==408||error.httpStatus==429||error.httpStatus>=500;
            default:return false;
        }
    }
}
