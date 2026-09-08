package com.parvaz.tunnel.store;

import com.parvaz.tunnel.config.LinkParser;
import com.parvaz.tunnel.model.Profile;
import java.util.*;

/** Pure plan: no delete-first step, no writes, no global deduplication across owners. */
public final class SubscriptionReconciler {
    private SubscriptionReconciler() {}
    public static final class Plan {
        public final ArrayList<Profile> all;
        public final int added, retained, removed, duplicates, count;
        Plan(ArrayList<Profile> all,int added,int retained,int removed,int duplicates,int count){
            this.all=all;this.added=added;this.retained=retained;this.removed=removed;this.duplicates=duplicates;this.count=count;
        }
    }
    public static Plan plan(List<Profile> old,List<Profile> incoming,String owner,String selectedId) {
        if(owner==null||owner.isEmpty()||incoming==null||incoming.isEmpty())throw new IllegalArgumentException("Empty subscription replacement refused");
        Map<String,Profile> previous=new HashMap<>();int oldCount=0;
        Set<String> usedIds=new HashSet<>();
        for(Profile p:old) {
            usedIds.add(p.id);
            if(owner.equals(p.subscriptionId)) {
                oldCount++;
                String fingerprint=ProfileIdentity.fingerprint(p);
                if(!previous.containsKey(fingerprint)||p.id.equals(selectedId))previous.put(fingerprint,p);
            }
        }
        ArrayList<Profile> replacement=new ArrayList<>();Set<String> seen=new HashSet<>();
        int added=0,retained=0,duplicates=0;
        for(Profile entry:incoming) {
            if(entry==null||!LinkParser.valid(entry))throw new IllegalArgumentException("Invalid subscription replacement");
            Profile p=ProfileIdentity.copy(entry);p.subscriptionId=owner;
            String fingerprint=ProfileIdentity.fingerprint(p);
            if(!seen.add(fingerprint)){duplicates++;continue;}
            Profile existing=previous.get(fingerprint);
            if(existing!=null){p.id=existing.id;p.ping=existing.ping;retained++;}
            else{do{p.id=UUID.randomUUID().toString();}while(usedIds.contains(p.id));p.ping=-1;added++;}
            usedIds.add(p.id);replacement.add(p);
        }
        ArrayList<Profile> all=new ArrayList<>();boolean inserted=false;
        for(Profile p:old) {
            if(owner.equals(p.subscriptionId)) {
                if(!inserted){all.addAll(replacement);inserted=true;}
            }else all.add(p);
        }
        if(!inserted)all.addAll(replacement);
        return new Plan(all,added,retained,oldCount-retained,duplicates,replacement.size());
    }
}
