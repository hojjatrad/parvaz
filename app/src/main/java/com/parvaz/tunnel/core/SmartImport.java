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
        public int added,subscriptions,failed;
        public final ArrayList<String> codes=new ArrayList<>();
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
                if(!result.parsed.fatal)result.added+=store.a(result.parsed.profiles,"");
            }
            for(String url:input.subscriptions) {
                if(cancelled.getAsBoolean())break;
                Subscription sub=store.addOrGetSubscription(url);
                SubscriptionRefresh.Result refresh=SubscriptionRefresh.runOne(store,prefs,cancelled,fetcher,sub.id);
                result.added+=refresh.added;result.subscriptions+=refresh.updated;result.failed+=refresh.failed;
                result.codes.addAll(refresh.codes);
            }
            store.removeDuplicates(prefs);
        }catch(IllegalArgumentException e){result.failed++;result.codes.add("INVALID_INPUT_OR_URL");}
         catch(Exception e){result.failed++;result.codes.add("IMPORT_FAILED");}
        return result;
    }
}
