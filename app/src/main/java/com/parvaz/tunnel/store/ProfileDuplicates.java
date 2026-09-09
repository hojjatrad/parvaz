package com.parvaz.tunnel.store;
import com.parvaz.tunnel.model.Profile;
import java.util.*;

/** A connection shared by independent subscriptions has one visible row, but retains each
 * source record so refreshing/removing one owner never destroys another owner's server. */
public final class ProfileDuplicates {
    private ProfileDuplicates() {}
    public static ArrayList<Profile> visible(List<Profile> profiles,String selected,Set<String> favorites) {
        LinkedHashMap<String,Profile> unique=new LinkedHashMap<>();
        for(Profile p:profiles){
            String key=ProfileIdentity.fingerprint(p);Profile old=unique.get(key);
            if(old==null || preferred(p,old,selected,favorites))unique.put(key,p);
        }
        return new ArrayList<>(unique.values());
    }
    static boolean preferred(Profile p,Profile old,String selected,Set<String> favorites) {
        if(old.id.equals(selected))return false;
        if(p.id.equals(selected))return true;
        return favorites.contains(p.id)&&!favorites.contains(old.id);
    }
}
