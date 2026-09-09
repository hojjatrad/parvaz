package com.parvaz.tunnel.store;
import com.parvaz.tunnel.model.Profile;
import java.util.*;

/** A connection shared by independent subscriptions has one visible row, but retains each
 * source record so refreshing/removing one owner never destroys another owner's server. */
public final class ProfileDuplicates {
    private ProfileDuplicates() {}
    public static final class Grouped {
        public final ArrayList<Profile> profiles=new ArrayList<>();
        public final Set<String> favoriteIds=new HashSet<>();
    }
    public static Grouped group(List<Profile> profiles,String selected,Set<String> favorites) {
        LinkedHashMap<String,Profile> unique=new LinkedHashMap<>();Set<String> favoriteKeys=new HashSet<>();
        for(Profile p:profiles){
            String key=ProfileIdentity.fingerprint(p);Profile old=unique.get(key);
            if(favorites.contains(p.id))favoriteKeys.add(key);
            if(old==null || preferred(p,old,selected,favorites))unique.put(key,p);
        }
        Grouped result=new Grouped();
        for(Map.Entry<String,Profile> entry:unique.entrySet()) {
            result.profiles.add(entry.getValue());
            if(favoriteKeys.contains(entry.getKey()))result.favoriteIds.add(entry.getValue().id);
        }
        return result;
    }
    public static ArrayList<Profile> visible(List<Profile> profiles,String selected,Set<String> favorites) {
        return group(profiles,selected,favorites).profiles;
    }
    public static Set<String> connectionIds(Profile selected,List<Profile> records) {
        Set<String> ids=new LinkedHashSet<>();String key=ProfileIdentity.fingerprint(selected);
        for(Profile p:records)if(key.equals(ProfileIdentity.fingerprint(p)))ids.add(p.id);
        return ids;
    }
    static boolean preferred(Profile p,Profile old,String selected,Set<String> favorites) {
        if(old.id.equals(selected))return false;
        if(p.id.equals(selected))return true;
        return favorites.contains(p.id)&&!favorites.contains(old.id);
    }
}
