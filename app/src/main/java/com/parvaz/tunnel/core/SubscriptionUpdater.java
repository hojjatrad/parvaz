package com.parvaz.tunnel.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

/**
 * Subscription updater for compatible panel outputs; importing does not guarantee connectivity.
 */
public final class SubscriptionUpdater {

    public final Context f6298a;
    public final Handler f6299b = new Handler(Looper.getMainLooper());

    public interface a {
        void a(String str, int i);
        default void onComplete(SubscriptionRefresh.Result result) { a(result.errorSummary(), result.serverCount); }
    }

    public static class b {
        public final String f6300a;
        public final String f6301b;
        public final int format;
        public final com.parvaz.tunnel.config.ImportResult parsed;
        public b(String str,String str2){this(str,str2,0,null);}
        public b(String str,String str2,int format,com.parvaz.tunnel.config.ImportResult parsed) {
            this.f6300a = str == null ? "" : str;
            this.f6301b = str2;this.format=format;this.parsed=parsed;
        }
    }

    public SubscriptionUpdater(Context context) {
        this.f6298a = context.getApplicationContext();
    }

    /** Fetch with system TLS validation, bounded bodies and safe redirects. */
    public static b a(String str) throws java.io.IOException {
        return negotiate(str,SubscriptionHttpClient::fetch);
    }
    interface FormatFetcher {SubscriptionHttpClient.Response fetch(String url,int format)throws java.io.IOException;}
    static b negotiate(String str,FormatFetcher fetcher)throws java.io.IOException {
        b best=null;int bestCount=-1;
        SubscriptionHttpClient.FetchException last=null;
        for(int format=0;format<3;format++) {
            if(Thread.currentThread().isInterrupted())throw new java.io.IOException("CANCELLED");
            try {
                SubscriptionHttpClient.Response response=fetcher.fetch(str,format);
                com.parvaz.tunnel.config.ImportResult parsed=com.parvaz.tunnel.config.LinkParser.parseDetailed(response.body);
                b candidate=withMetadata(response,format,parsed);
                if(parsed.profiles.size()>bestCount){best=candidate;bestCount=parsed.profiles.size();}
                if(parsed.safeToReplace())return candidate;
                // A partial response with valid servers is useful: do not replace it with an unrelated format.
                if(!parsed.fatal&&!parsed.profiles.isEmpty())return candidate;
            }catch(SubscriptionHttpClient.FetchException e) {
                // Do not retry TLS/auth/rate-limit failures under another identity.
                if(e.error!=SubscriptionHttpClient.Error.HTML_RESPONSE&&e.error!=SubscriptionHttpClient.Error.EMPTY_RESPONSE)throw e;
                last=e;
            }
        }
        if(best!=null)return best;
        throw last==null?new java.io.IOException("NO_SUBSCRIPTION_RESPONSE"):last;
    }
    private static b withMetadata(SubscriptionHttpClient.Response response,int format,com.parvaz.tunnel.config.ImportResult parsed) {
        String responseBody = response.body;
        String userinfo = response.userinfo;

        // If userinfo header was not found, check if response is JSON with Marzban / panel info
        if (userinfo == null && responseBody.trim().startsWith("{") && responseBody.trim().endsWith("}")) {
            try {
                com.parvaz.tunnel.config.LinkParser.checkJsonDepth(responseBody);
                JSONObject j = com.parvaz.tunnel.config.JsonInput.object(responseBody.trim());
                long total = j.optLong("data_limit", j.optLong("total", -1L));
                long used = j.optLong("used_traffic", j.optLong("used", -1L));
                long exp = j.optLong("expire", -1L);
                if (total > 0 || exp > 0) {
                    userinfo = "upload=0; download=" + Math.max(0L, used) + "; total=" + total + "; expire=" + exp;
                }
            } catch (Throwable ignored) {
            }
        }

        return new b(responseBody,userinfo,format,parsed);
    }
}
