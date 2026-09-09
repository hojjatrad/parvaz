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
            JSONObject value=connectionValue(profile);
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for(byte b:bytes)result.append(String.format(Locale.ROOT,"%02x",b&255));
            return result.toString();
        }catch(Exception e){throw new IllegalArgumentException("Profile fingerprint failed");}
    }
    /** Conservative equivalences already implemented by the outbound builder. No credential,
     * transport security or independent subscription ownership is inferred from a display name. */
    public static JSONObject connectionValue(Profile profile)throws JSONException {
        JSONObject value=profile.toJson();
        for(String key:new String[]{"id","remark","subscriptionId","rawLink"})value.remove(key);
        if("custom".equals(profile.protocol)) {
            try {
                JSONObject outbound=CustomOutbound.fromJson(profile.rawJson);
                if(outbound!=null){outbound.remove("tag");return new JSONObject().put("protocol","custom").put("rawJson",outbound);}
            }catch(JSONException|IllegalArgumentException ignored){}
            return value;
        }
        if(!Arrays.asList("vless","vmess","trojan","shadowsocks","socks","http","wireguard").contains(profile.protocol))return value;
        value.remove("rawJson"); // The builder reads raw JSON only for custom profiles.
        value.put("address",profile.address.contains("%")?profile.address:profile.address.toLowerCase(Locale.ROOT));
        String network=profile.network.isEmpty()?"tcp":profile.network;
        if(network.equals("http"))network="h2";
        if(network.equals("splithttp"))network="xhttp";
        value.put("network",network);
        if(profile.security.equalsIgnoreCase("none"))value.put("security","");
        if(profile.encryption.isEmpty()&&profile.protocol.equals("vless"))value.put("encryption","none");
        if(profile.encryption.isEmpty()&&profile.protocol.equals("vmess"))value.put("encryption","auto");
        if(Arrays.asList("ws","httpupgrade","h2","xhttp").contains(network)&&profile.path.isEmpty())value.put("path","/");
        if(profile.headerType.isEmpty())value.put("headerType","none");
        if(network.equals("grpc")) {
            value.put("serviceName",profile.serviceName.isEmpty()?profile.path.replaceFirst("^/",""):profile.serviceName);
            value.put("path","");value.put("mode",profile.mode.equals("multi")?"multi":"");
        }
        if(profile.security.equals("reality")&&profile.fingerprint.isEmpty())value.put("fingerprint","chrome");
        if(profile.security.equals("tls")) {
            String sni=profile.sni.isEmpty()?(profile.host.isEmpty()?profile.address:profile.host):profile.sni;
            value.put("sni",sni.toLowerCase(Locale.ROOT));
            ArrayList<String> alpn=new ArrayList<>();for(String token:profile.alpn.split(","))if(!token.trim().isEmpty())alpn.add(token.trim());
            value.put("alpn",String.join(",",alpn));
        }
        if((profile.protocol.equals("vless")||profile.protocol.equals("vmess"))&&profile.uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            value.put("uuid",profile.uuid.toLowerCase(Locale.ROOT));
        if(!profile.protocol.equals("wireguard"))for(String key:new String[]{"localAddress","presharedKey","reserved","wgMtu"})value.remove(key);
        return value;
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
