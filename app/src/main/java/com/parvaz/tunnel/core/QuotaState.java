package com.parvaz.tunnel.core;
import com.parvaz.tunnel.config.SubscriptionUrl;
import com.parvaz.tunnel.model.*;
import java.util.List;

/** No manual-plan/session-traffic fallbacks: only metadata from the applicable subscription. */
public final class QuotaState {
    private QuotaState() {}
    public static Subscription source(Profile selected,List<Subscription> subscriptions) {
        if(selected!=null) {
            if(selected.subscriptionId==null||selected.subscriptionId.isEmpty())return null;
            for(Subscription s:subscriptions)if(s.id.equals(selected.subscriptionId)&&s.enabled&&SubscriptionUrl.valid(s.url))return s;
            return null;
        }
        return null;
    }
    public static boolean known(Subscription s){return s!=null&&s.quotaUpdatedAt>0&&s.quotaTotal>=0&&s.quotaUpload>=0&&s.quotaDownload>=0;}
    public static int percent(long used,long total){return total<=0?0:(int)Math.min(100d,100d*Math.max(0L,used)/total);}
}
