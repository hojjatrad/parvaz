package com.parvaz.tunnel.store;
import com.parvaz.tunnel.model.Profile;
import java.util.*;
import org.json.*;
/** Diagnostic contains only aggregate counts and known field names, never endpoints or values. */
public final class DuplicateReport {
    private DuplicateReport(){}
    public static String safe(List<Profile> records,int removed) {
        Map<String,List<Profile>> endpoints=new LinkedHashMap<>();Set<String> identities=new HashSet<>();
        for(Profile p:records){identities.add(ProfileIdentity.fingerprint(p));String key=p.protocol+"\n"+p.address.toLowerCase(Locale.ROOT)+"\n"+p.port;endpoints.computeIfAbsent(key,k->new ArrayList<>()).add(p);}
        int distinctGroups=0;Set<String> fields=new TreeSet<>();
        for(List<Profile> group:endpoints.values()) {
            Set<String> keys=new HashSet<>();for(Profile p:group)keys.add(ProfileIdentity.fingerprint(p));
            if(keys.size()<2)continue;distinctGroups++;
            try {
                JSONObject first=ProfileIdentity.connectionValue(group.get(0));
                for(int i=1;i<group.size();i++) {
                    JSONObject next=ProfileIdentity.connectionValue(group.get(i));Set<String> names=new HashSet<>();
                    Iterator<String> a=first.keys(),b=next.keys();while(a.hasNext())names.add(a.next());while(b.hasNext())names.add(b.next());
                    for(String name:names)if(!String.valueOf(first.opt(name)).equals(String.valueOf(next.opt(name))))fields.add(name.equals("rawJson")?"custom_options":name);
                }
            }catch(JSONException e){fields.add("unreadable_profile");}
        }
        return "OPERATION_DUPLICATES\nremoved_records="+removed+"; stored_records="+records.size()+"; visible_connections="+identities.size()
            +"; shared_source_copies="+(records.size()-identities.size())+"\nsame_endpoint_different_connections="+distinctGroups+"\ndiffering_fields="+String.join(",",fields);
    }
}
