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
    private static final java.util.concurrent.locks.ReentrantLock LOCK=new java.util.concurrent.locks.ReentrantLock();
    private SubscriptionRefresh() {}
    interface Fetcher { SubscriptionUpdater.b fetch(String url) throws IOException; }
    public static final class Result {
        public int updated, failed, skipped, serverCount, added, retained, removed, warnings, fetched, recognized;
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
        return runOne(store,prefs,cancelled,fetcher,null);
    }
    static Result runOne(ProfileStore store,SharedPreferences prefs,BooleanSupplier cancelled,Fetcher fetcher,String targetId) {
        Result result=new Result();
        boolean acquired;
        try {acquired=targetId==null?LOCK.tryLock():LOCK.tryLock(120,java.util.concurrent.TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();result.cancelled=true;result.fail("CANCELLED",true);return result;}
        if(!acquired){result.fail("REFRESH_ALREADY_RUNNING",true);return result;}
        try {
            store.removeDuplicates(prefs);
            for(Object o:store.f()) {
                if(stop(cancelled,result))break;
                Subscription listed=(Subscription)o;
                if(targetId!=null&&!targetId.equals(listed.id))continue;
                ProfileStore.Snapshot snapshot=store.beginRefresh(listed.id);
                if(snapshot==null){result.skipped++;continue;}
                try {
                    SubscriptionUpdater.b response=fetcher.fetch(snapshot.subscription.url);
                    if(stop(cancelled,result))break;
                    result.fetched++;
                    ImportResult parsed=response.parsed==null?LinkParser.parseDetailed(response.f6300a):response.parsed;
                    result.recognized+=parsed.profiles.size();
                    if(result.codes.size()<10)result.codes.add("FORMAT_"+response.format);
                    for(String issue:parsed.issues)if(result.codes.size()<10)result.codes.add(issue);
                    result.warnings+=parsed.warnings;
                    if(!parsed.safeToReplace()) {
                        if(!parsed.fatal&&!parsed.profiles.isEmpty()) {
                            if(stop(cancelled,result))break;
                            SubscriptionReconciler.Plan partial=store.mergeSubscription(snapshot,parsed,response.f6301b,
                                    System.currentTimeMillis(),prefs.getString("selected_profile",""));
                            result.serverCount+=partial.count;result.added+=partial.added;result.retained+=partial.retained;
                        } else {
                            if(stop(cancelled,result))break;
                            store.updateQuota(snapshot,response.f6301b,System.currentTimeMillis());
                        }
                        result.warnings+=parsed.rejected;
                        result.fail(parsed.profiles.isEmpty()?"NO_VALID_CONFIGURATIONS":"PARTIAL_IMPORT_VALID_ADDED_OLD_KEPT",false);
                        continue;
                    }
                    if(stop(cancelled,result))break;
                    SubscriptionReconciler.Plan plan=store.replaceSubscription(snapshot,parsed,response.f6301b,
                            System.currentTimeMillis(),prefs.getString("selected_profile",""));
                    result.updated++;result.serverCount+=plan.count;result.added+=plan.added;result.retained+=plan.retained;result.removed+=plan.removed;
                }catch(ProfileStore.StaleRefresh e){result.fail("STALE_RESPONSE_IGNORED",true);}
                 catch(SubscriptionHttpClient.FetchException e){result.fail(e.error.name()+(e.httpStatus>0?"_"+e.httpStatus:""),transientError(e));}
                 catch(IOException e){result.fail("NETWORK_FAILURE",true);}
                 catch(IllegalArgumentException e){result.fail("INVALID_SUBSCRIPTION",false);}
                 catch(Exception e){result.fail("REFRESH_FAILED",true);}
            }
        }catch(Exception e){result.fail("REFRESH_FAILED",true);}
        finally{LOCK.unlock();}
        if(targetId!=null&&result.updated==0&&result.failed==0)result.fail("SUBSCRIPTION_NOT_PROCESSED",true);
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
