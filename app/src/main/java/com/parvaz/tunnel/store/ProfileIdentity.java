package com.parvaz.tunnel.store;

import com.parvaz.tunnel.config.CustomOutbound;
import com.parvaz.tunnel.model.Profile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Local connection fingerprint. NEVER log its input (credentials), nor send fingerprints remotely. */
public final class ProfileIdentity {
    private ProfileIdentity() {}
    public static Profile copy(Profile p) {
        try { Profile result=Profile.fromJson(p.toJson());result.ping=p.ping;return result; }
        catch(JSONException e){throw new IllegalArgumentException("Profile serialization failed");}
    }
    public static String fingerprint(Profile profile) {
        try {
            JSONObject value=profile.toJson();
            for(String key:new String[]{"id","remark","subscriptionId","rawLink"})value.remove(key);
            if("custom".equals(profile.protocol)) {
                try {
                    JSONObject outbound=CustomOutbound.fromJson(profile.rawJson);
                    if(outbound!=null){outbound.remove("tag");value.put("rawJson",outbound);}
                }catch(JSONException | IllegalArgumentException ignored){ /* old invalid custom: distinguish by original raw JSON */ }
            }
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));
            return result.toString();
        }catch(Exception e){throw new IllegalArgumentException("Profile fingerprint failed");}
    }
    private static String canonical(Object object) throws JSONException {
        if(object instanceof JSONObject) {
            JSONObject value=(JSONObject)object;List<String> keys=new ArrayList<>();
            Iterator<String> iterator=value.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys);
            StringBuilder out=new StringBuilder("{");
            for(int i=0;i<keys.size();i++){if(i>0)out.append(',');String key=keys.get(i);out.append(JSONObject.quote(key)).append(':').append(canonical(value.get(key)));}
            return out.append('}').toString();
        }
        if(object instanceof JSONArray) {
            JSONArray value=(JSONArray)object;StringBuilder out=new StringBuilder("[");
            for(int i=0;i<value.length();i++){if(i>0)out.append(',');out.append(canonical(value.get(i)));}
            return out.append(']').toString();
        }
        return object==null||object==JSONObject.NULL?"null":object instanceof String?JSONObject.quote((String)object):object.toString();
    }
}
