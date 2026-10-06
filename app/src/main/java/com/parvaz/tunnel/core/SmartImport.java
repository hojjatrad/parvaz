package com.parvaz.tunnel.core;
import android.content.Context;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.store.*;
import com.parvaz.tunnel.model.*;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/** Blocking user import entrypoint. Called exactly once on a worker, never from the UI thread. */
public final class SmartImport {
    private SmartImport(){}
    public static final class Result {
        public ImportResult parsed=new ImportResult();
        public int added,subscriptions,failed,requested,fetched,recognized;
        public final ArrayList<String> codes=new ArrayList<>();
    }
    public static String safeReport(Result r,String version) {
        StringBuilder text=new StringBuilder("Parvaz "+version+"\nRequested="+r.requested+"; fetched="+r.fetched+"; recognized="+r.recognized+"; added="+r.added+"; failed="+r.failed);
        for(int i=0;i<Math.min(16,r.codes.size());i++)text.append("\n").append(r.codes.get(i));
        return text.toString();
    }
    /**
     * The first failure code of a run, or null when nothing failed.
     *
     * <p>The codes are fixed identifiers (never input or credentials), so they are safe to
     * put in front of the user. "Nothing valid was found" is useless when the real answer
     * is that the panel answered 404.
     */
    public static String firstFailureCode(Result r) {
        for(String code:r.codes){
            if(code==null||code.isEmpty())continue;
            if(code.startsWith("STAGE_"))continue; // diagnostic detail, not a cause
            return code;
        }
        return null;
    }
    /** The HTTP status inside a HTTP_STATUS_&lt;code&gt; failure, or 0 when it is not one. */
    public static int httpStatusOf(String code) {
        if(code==null||!code.startsWith("HTTP_STATUS_"))return 0;
        try{return Integer.parseInt(code.substring("HTTP_STATUS_".length()));}
        catch(NumberFormatException notANumber){return 0;}
    }
    public static Result run(Context context,String text,BooleanSupplier cancelled) {
        return run(ProfileStore.f(context),context.getSharedPreferences("parvaz_prefs",0),text,cancelled,SubscriptionUpdater::a);
    }
    static Result run(ProfileStore store,android.content.SharedPreferences prefs,String text,BooleanSupplier cancelled,SubscriptionRefresh.Fetcher fetcher) {
        Result result=new Result();
        try {
            ImportInput input=ImportInput.parse(text);
            if(input.configs.trim().isEmpty()&&input.subscriptions.isEmpty()){result.failed++;result.codes.add("EMPTY_INPUT");return result;}
            if(cancelled.getAsBoolean())return result;
            if(!input.configs.trim().isEmpty()) {
                result.parsed=LinkParser.parseDetailed(input.configs);
                result.recognized+=result.parsed.profiles.size();
                if(!result.parsed.fatal){
                    result.added+=store.a(result.parsed.profiles,"");
                    if(!result.parsed.profiles.isEmpty()&&input.subscriptions.isEmpty())store.setPrimarySubscription(ProfileStore.MANUAL_GROUP);
                }
                if(result.parsed.profiles.isEmpty()){result.failed++;result.codes.add("NO_LOCAL_CONFIGURATIONS");}
                result.codes.addAll(result.parsed.issues.subList(0,Math.min(8,result.parsed.issues.size())));
            }
            boolean activate=true;
            for(String url:input.subscriptions) {
                if(cancelled.getAsBoolean())break;
                result.requested++;
                Subscription sub=store.addOrGetSubscription(url);
                if(activate){store.setPrimarySubscription(sub.id);activate=false;}
                SubscriptionRefresh.Result refresh=SubscriptionRefresh.runOne(store,prefs,cancelled,fetcher,sub.id);
                result.added+=refresh.added;result.subscriptions+=refresh.updated;result.failed+=refresh.failed;
                result.fetched+=refresh.fetched;result.recognized+=refresh.recognized;
                result.codes.addAll(refresh.codes);
            }
            store.removeDuplicates(prefs);
        }catch(IllegalArgumentException e){result.failed++;result.codes.add("INVALID_INPUT_OR_URL");}
         catch(Exception e){result.failed++;result.codes.add("IMPORT_FAILED");}
        return result;
    }
}
