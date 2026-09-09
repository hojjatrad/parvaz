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
        public int requested, updated, failed, skipped, serverCount, added, retained, removed, warnings, fetched, recognized;
        public boolean retryable, cancelled;
        public final ArrayList<String> codes=new ArrayList<>();
        public final LinkedHashSet<String> errors=new LinkedHashSet<>();
        public final LinkedHashMap<String,Integer> notes=new LinkedHashMap<>();
        public final ArrayList<String> sources=new ArrayList<>();
        public String scope="ALL";
        public int sourceIndex,activeRecords,visibleConnections,archivedRecords;
        void note(String code){
            if(notes.containsKey(code)||notes.size()<16)notes.put(code,notes.getOrDefault(code,0)+1);
            if(!codes.contains(code)&&codes.size()<32)codes.add(code);
        }
        void outcome(String code){if(sources.size()<100)sources.add("SOURCE_"+sourceIndex+": "+code);}
        void fail(String code,boolean retry){
            failed++;retryable|=retry;errors.add(code);outcome("FAILED "+code);
            if(!codes.contains(code)){if(codes.size()>=32)codes.remove(codes.size()-1);codes.add(0,code);}
        }
        void diagnostic(String code){errors.add(code);if(!codes.contains(code)){if(codes.size()>=32)codes.remove(codes.size()-1);codes.add(0,code);}}
        public String errorSummary(){
            if(failed==0)return null;
            StringBuilder summary=new StringBuilder("Updated="+updated+"; failed="+failed+"; ");
            for(int i=0;i<codes.size();i++){if(i>0)summary.append(", ");summary.append(codes.get(i));}
            return summary.toString();
        }
    }
    public static String safeReport(Result r) {
        StringBuilder detail=new StringBuilder();
        for(String error:r.errors)detail.append("\nERROR ").append(error);
        for(String source:r.sources)detail.append("\n").append(source);
        for(Map.Entry<String,Integer> note:r.notes.entrySet())detail.append("\nNOTE ").append(note.getKey()).append("; occurrences=").append(note.getValue());
        return "OPERATION_REFRESH\nstatus="+(r.failed==0?"SUCCESS":r.updated>0?"PARTIAL":"FAILED")+"\nscope="+r.scope+"; active_records="+r.activeRecords+"; visible_connections="+r.visibleConnections+"; archived_records="+r.archivedRecords+"\nrequested="+r.requested+"; fetched="+r.fetched+"; recognized="+r.recognized
            +"; updated="+r.updated+"; failed="+r.failed+"; skipped="+r.skipped+"; added="+r.added
            +"; retained="+r.retained+"; removed="+r.removed+detail;
    }
    public static Result runManual(Context context,BooleanSupplier cancelled) {
        return runOne(ProfileStore.f(context),context.getApplicationContext().getSharedPreferences("parvaz_prefs",0),cancelled,SubscriptionUpdater::a,null,true);
    }
    public static Result run(Context context,BooleanSupplier cancelled) {
        return run(ProfileStore.f(context),context.getApplicationContext().getSharedPreferences("parvaz_prefs",0),cancelled,SubscriptionUpdater::a);
    }
    static Result run(ProfileStore store,SharedPreferences prefs,BooleanSupplier cancelled,Fetcher fetcher) {
        return runOne(store,prefs,cancelled,fetcher,null);
    }
    static Result runOne(ProfileStore store,SharedPreferences prefs,BooleanSupplier cancelled,Fetcher fetcher,String targetId) {
        return runOne(store,prefs,cancelled,fetcher,targetId,targetId!=null);
    }
    static Result runOne(ProfileStore store,SharedPreferences prefs,BooleanSupplier cancelled,Fetcher fetcher,String targetId,boolean wait) {
        Result result=new Result();result.scope=store.primarySubscription().isEmpty()?"ALL":"PRIMARY";
        boolean acquired;
        try {acquired=wait?LOCK.tryLock(60,java.util.concurrent.TimeUnit.SECONDS):LOCK.tryLock();}
        catch(InterruptedException e){Thread.currentThread().interrupt();result.cancelled=true;result.fail("CANCELLED",true);return result;}
        if(!acquired){result.fail("REFRESH_ALREADY_RUNNING",true);return result;}
        try {
            store.removeDuplicates(prefs);
            result.scope=store.primarySubscription().isEmpty()?"ALL":"PRIMARY";
            int sourceNumber=0;
            for(Object o:store.f()) {
                if(stop(cancelled,result))break;
                Subscription listed=(Subscription)o;
                sourceNumber++;
                if(targetId!=null&&!targetId.equals(listed.id))continue;
                if(targetId==null&&!store.isRefreshSource(listed.id))continue;
                result.sourceIndex=sourceNumber;
                ProfileStore.Snapshot snapshot=store.beginRefresh(listed.id);
                if(snapshot==null){result.skipped++;continue;}
                result.requested++;
                try {
                    SubscriptionUpdater.b response=fetcher.fetch(snapshot.subscription.url);
                    if(stop(cancelled,result))break;
                    result.fetched++;
                    ImportResult parsed=response.parsed==null?LinkParser.parseDetailed(response.f6300a):response.parsed;
                    result.recognized+=parsed.profiles.size();
                    result.note("FORMAT_"+response.format);
                    for(String issue:parsed.issues)result.note(issue);
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
                    result.outcome("UPDATED; recognized="+parsed.profiles.size()+"; current="+plan.count);
                    result.updated++;result.serverCount+=plan.count;result.added+=plan.added;result.retained+=plan.retained;result.removed+=plan.removed;
                }catch(ProfileStore.StaleRefresh e){result.fail("STALE_RESPONSE_IGNORED",true);}
                 catch(SubscriptionHttpClient.FetchException e){result.fail(e.error.name()+(e.httpStatus>0?"_"+e.httpStatus:""),transientError(e));result.diagnostic(e.diagnostic());}
                 catch(IOException e){result.fail("NETWORK_FAILURE",true);}
                 catch(IllegalArgumentException e){result.fail("INVALID_SUBSCRIPTION",false);}
                 catch(Exception e){result.fail("REFRESH_FAILED",true);}
            }
        }catch(Exception e){result.fail("REFRESH_FAILED",true);}
        finally{
            try {
                java.util.List<com.parvaz.tunnel.model.Profile> active=store.activeProfiles();
                result.activeRecords=active.size();result.archivedRecords=store.e().size()-active.size();
                result.visibleConnections=ProfileDuplicates.visible(active,prefs.getString("selected_profile",""),Collections.emptySet()).size();
            }catch(Exception e){result.note("COUNTS_UNAVAILABLE");}
            finally{LOCK.unlock();}
        }
        if(result.requested==0&&result.failed==0)result.fail(targetId!=null||!store.primarySubscription().isEmpty()?"SUBSCRIPTION_NOT_PROCESSED":(result.skipped>0?"ALL_SUBSCRIPTIONS_DISABLED":"NO_SUBSCRIPTIONS"),false);
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
