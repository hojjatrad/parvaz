package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;
import org.json.*;
import java.util.*;

/** A separate loopback probe entry, pinned ahead of DIRECT/domain rules. Normal
 * application inbounds/routing and every transport/TLS setting remain unchanged.
 * Ambiguous full profiles fail closed for readiness, not for user traffic. */
public final class ReadinessConfig {
 private ReadinessConfig(){}
 private static boolean remote(String type){return Arrays.asList("vmess","vless","trojan","shadowsocks","socks","http","wireguard","hysteria2","tuic").contains(type);}
 public static final class Plan {
  public final String config;public final boolean pinned;
  Plan(String config,boolean pinned){this.config=config;this.pinned=pinned;}
 }
 public static Plan prepare(String config,Profile profile,boolean nativeRemoteOnly,int port)throws JSONException {
  JSONObject root=new JSONObject(config);JSONArray outbounds=root.optJSONArray("outbounds");String selected=null;Set<String> tags=new HashSet<>();
  if(EngineConfig.external(profile.protocol)&&!nativeRemoteOnly)return new Plan(config,false);
  if(outbounds!=null)for(int i=0;i<outbounds.length();i++){
   JSONObject out=outbounds.getJSONObject(i);
   String outTag=out.optString("tag");if(!outTag.isEmpty()&&!tags.add(outTag))return new Plan(config,false);
   if(!remote(out.optString("protocol")))continue;
   if(!profile.protocol.equals("full-xray")&&!out.optString("tag").equals("proxy"))continue;
   // A full profile with multiple remote outbounds has no single selected node.
   // Do not choose the first/random node or modify its routing policy.
   if(selected!=null)return new Plan(config,false);
   selected=out.optString("tag");if(selected.isEmpty())return new Plan(config,false);
  }
  if(selected==null)return new Plan(config,false);
  if(port<1||port>65535)throw new IllegalArgumentException("Invalid readiness port");
  String tag="parvaz-readiness-"+UUID.randomUUID();
  JSONArray inbounds=root.optJSONArray("inbounds");if(inbounds==null)throw new IllegalArgumentException("Missing active inbounds");
  inbounds.put(new JSONObject().put("tag",tag).put("listen","127.0.0.1").put("port",port).put("protocol","http").put("settings",new JSONObject()));
  JSONObject routing=root.optJSONObject("routing");if(routing==null)routing=new JSONObject();
  JSONArray rules=new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put(tag)).put("outboundTag",selected));
  JSONArray old=routing.optJSONArray("rules");if(old!=null)for(int i=0;i<old.length();i++)rules.put(old.get(i));
  routing.put("rules",rules);root.put("routing",routing);return new Plan(root.toString(),true);
 }
 /** Verify the INNER engine too. A loopback relay tagged 'proxy' is not proof. */
 public static boolean nativeRemoteOnly(JSONObject root,String protocol){
  if(protocol.equals("full-clash"))return clashRemoteOnly(root);
  JSONArray outs=root.optJSONArray("outbounds");if(outs==null||outs.length()!=1)return false;
  JSONObject out=outs.optJSONObject(0);if(out==null||!remote(out.optString("type"))||out.has("detour"))return false;
  JSONObject route=root.optJSONObject("route");if(route==null)return true;
  String destination=route.optString("final");if(!destination.isEmpty()&&!destination.equals(out.optString("tag")))return false;
  JSONArray rules=route.optJSONArray("rules");if(rules==null)return true;
  for(int i=0;i<rules.length();i++){
   JSONObject rule=rules.optJSONObject(i);if(rule==null)return false;
   // Only the exact DNS/sniff rules injected by EngineConfig are harmless here.
   if(rule.length()==1&&rule.optString("action").equals("sniff"))continue;
   if(rule.length()==2&&rule.optString("action").equals("hijack-dns")&&rule.optString("protocol").equals("dns"))continue;
   return false;
  }
  return true;
 }
 private static boolean clashRemoteOnly(JSONObject root){
  if(!root.optString("mode","rule").equalsIgnoreCase("rule"))return false;
  Set<String> names=new HashSet<>();
  for(String key:new String[]{"proxies","proxy-groups"}){JSONArray items=root.optJSONArray(key);if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null||item.optString("name").isEmpty()||!names.add(item.optString("name")))return false;}}
  JSONArray rules=root.optJSONArray("rules");if(rules==null||rules.length()==0)return false;
  for(int i=0;i<rules.length();i++){
   String rule=rules.optString(i);String[] parts=rule.split(",");
   if(parts.length<2||!Arrays.asList("DOMAIN","DOMAIN-SUFFIX","DOMAIN-KEYWORD","DOMAIN-REGEX","GEOSITE","IP-CIDR","IP-CIDR6","GEOIP","IP-SUFFIX","IP-ASN","SRC-IP-CIDR","SRC-IP-SUFFIX","SRC-IP-ASN","SRC-GEOIP","DST-PORT","SRC-PORT","IN-PORT","IN-TYPE","IN-USER","IN-NAME","NETWORK","UID","PROCESS-NAME","PROCESS-PATH","RULE-SET","AND","OR","NOT","MATCH").contains(parts[0].trim()))return false;
   int last=parts.length-1;if(parts[last].trim().equalsIgnoreCase("no-resolve"))last--;
   if(last<1||!clashTarget(root,parts[last].trim(),new HashSet<>(),0))return false;
   if(i==rules.length()-1&&!(parts.length==2&&parts[0].trim().equalsIgnoreCase("MATCH")))return false;
  }
  return true;
 }
 private static boolean clashTarget(JSONObject root,String name,Set<String> path,int depth){
  if(depth>12||!path.add(name)||Arrays.asList("DIRECT","COMPATIBLE","GLOBAL","PASS").contains(name))return false;
  if(name.equals("REJECT")||name.equals("REJECT-DROP"))return true; // cannot supply a successful HTTPS response
  JSONArray proxies=root.optJSONArray("proxies");if(proxies!=null)for(int i=0;i<proxies.length();i++){
   JSONObject p=proxies.optJSONObject(i);if(p!=null&&p.optString("name").equals(name))return Arrays.asList("ss","ssr","vmess","vless","trojan","socks5","http","wireguard","hysteria","hysteria2","tuic").contains(p.optString("type"))&&!p.has("dialer-proxy");
  }
  JSONArray groups=root.optJSONArray("proxy-groups");if(groups!=null)for(int i=0;i<groups.length();i++){
   JSONObject group=groups.optJSONObject(i);if(group==null||!group.optString("name").equals(name))continue;
   JSONArray members=group.optJSONArray("proxies");if(group.has("use")||group.optBoolean("include-all")||group.optBoolean("include-all-proxies")||group.optBoolean("include-all-providers")||members==null||members.length()==0)return false;
   for(int j=0;j<members.length();j++)if(!clashTarget(root,members.optString(j),new HashSet<>(path),depth+1))return false;
   return true;
  }
  return false;
 }
}
