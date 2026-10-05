package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
import java.util.*;

public final class EngineConfig {
 private EngineConfig(){}
 public static boolean external(String p){return Arrays.asList("hysteria2","hy2","tuic","hysteria","hy","hy1","hysteria1","anytls","snell","shadowtls","shadow-tls","stls","shadowtls2","full-singbox","full-clash").contains(p);}
 /** Tag of the ShadowTLS hop a Shadowsocks outbound is detoured through. */
 public static String detourTag(String tag){return tag+"-shadowtls";}
 /** Android JSON escapes solidus; Mihomo's YAML reader rejects that JSON escape. */
 public static String serialize(JSONObject root,String protocol){
  String text=root.toString();return ProtocolNames.canonical(protocol).equals("full-clash")?text.replace("\\/","/"):text;
 }

 /** Megabit hint for Hysteria v1, stored as "up,down". Defaults stay modest on purpose. */
 static int bandwidth(String seed,boolean up){
  int fallback=up?20:100;
  if(seed==null)return fallback;
  String[] parts=seed.split(",");int index=up?0:1;
  if(parts.length<=index)return fallback;
  try{int value=Integer.parseInt(parts[index].trim());return value>0&&value<=100000?value:fallback;}
  catch(NumberFormatException invalid){return fallback;}
 }
 /** Snell protocol revision; only v3 and v4 are current, anything else becomes v4. */
 static int snellVersion(String mode){
  try{int value=Integer.parseInt(mode==null?"":mode.trim());return value==3||value==4?value:4;}
  catch(NumberFormatException invalid){return 4;}
 }

 /**
  * The single remote outbound for one profile, exactly as the live tunnel would dial it.
  *
  * <p>Extracted so the shared-engine batch can place many of these side by side in one
  * configuration instead of starting one engine process per server. Nothing protocol
  * specific may live anywhere else: the live path and the measurement path must agree.
  */
 public static JSONObject outbound(Profile profile,String tag)throws JSONException {
  String protocol=ProtocolNames.canonical(profile.protocol);
  // Protocols Xray normally carries are delegated, so one server can be dialled by
  // either engine with identical settings (see SingBoxOutbound.capable).
  if(SingBoxOutbound.capable(profile))return SingBoxOutbound.build(profile,tag);
   JSONObject outbound=new JSONObject().put("type",protocol).put("server",profile.address).put("server_port",profile.port);
  JSONObject tls=new JSONObject().put("enabled",true).put("server_name",profile.sni.isEmpty()?profile.address:profile.sni).put("insecure",profile.allowInsecure);
  if(!profile.alpn.isEmpty()){JSONArray alpn=new JSONArray();for(String value:profile.alpn.split(","))if(!value.trim().isEmpty())alpn.put(value.trim());tls.put("alpn",alpn);}
  outbound.put("tls",tls);
  if(protocol.equals("hysteria2")){
   outbound.put("password",profile.uuid);
   if(!profile.mode.isEmpty())outbound.put("obfs",new JSONObject().put("type",profile.mode).put("password",profile.host));
  }else if(protocol.equals("tuic")){
   outbound.put("uuid",profile.uuid).put("password",profile.quicKey).put("congestion_control",profile.mode.isEmpty()?"bbr":profile.mode).put("udp_relay_mode",profile.headerType.isEmpty()?"native":profile.headerType);
  }else if(protocol.equals("hysteria")){
   // Hysteria v1. The protocol requires both bandwidth hints; they travel in `seed`
   // as "up,down" megabits because the model has no dedicated field, and a missing or
   // unparsable value falls back to a conservative default instead of failing the dial.
   outbound.put("auth_str",profile.uuid).put("up_mbps",bandwidth(profile.seed,true)).put("down_mbps",bandwidth(profile.seed,false));
   if(!profile.host.isEmpty())outbound.put("obfs",profile.host);
  }else if(protocol.equals("anytls")){
   outbound.put("password",profile.uuid);
  }else if(protocol.equals("shadowtls")){
   // ShadowTLS is a transport hop, not a proxy: the real Shadowsocks outbound dials
   // through it with `detour`. Both halves are produced by outbounds(...) below.
   outbound.remove("tls");
   outbound.put("type","shadowsocks").put("method",profile.encryption.isEmpty()?"aes-128-gcm":profile.encryption)
    .put("password",profile.uuid).put("detour",detourTag(tag));
   // The address belongs to the ShadowTLS hop; the dialler must not repeat it.
   outbound.remove("server");
   outbound.remove("server_port");
  }else if(protocol.equals("snell")){
   // Snell carries its own obfuscation and has no TLS layer of its own.
   outbound.remove("tls");
   outbound.put("psk",profile.uuid).put("version",snellVersion(profile.mode));
   if(!profile.host.isEmpty())outbound.put("network","tcp");
  }else throw new IllegalArgumentException("Unsupported engine protocol");
  outbound.put("tag",tag);
  return outbound;
 }

 /**
  * Every outbound this profile needs, the dialling one first.
  *
  * <p>Almost every protocol is a single outbound. ShadowTLS is the exception: the
  * Shadowsocks outbound carries the traffic but reaches the server through a separate
  * ShadowTLS hop, so the pair must travel together and keep their tags consistent.
  */
 public static JSONArray outbounds(Profile profile,String tag)throws JSONException {
  JSONArray all=new JSONArray().put(outbound(profile,tag));
  if(ProtocolNames.canonical(profile.protocol).equals("shadowtls")){
   JSONObject hop=new JSONObject().put("type","shadowtls").put("tag",detourTag(tag))
    .put("server",profile.address).put("server_port",profile.port)
    .put("version",shadowTlsVersion(profile.mode)).put("password",profile.quicKey==null?"":profile.quicKey);
   JSONObject tls=new JSONObject().put("enabled",true)
    .put("server_name",profile.sni.isEmpty()?profile.address:profile.sni).put("insecure",profile.allowInsecure);
   if(!profile.fingerprint.isEmpty())tls.put("utls",new JSONObject().put("enabled",true).put("fingerprint",profile.fingerprint));
   hop.put("tls",tls);all.put(hop);
  }
  return all;
 }
 /** ShadowTLS protocol revision; only v1-v3 exist and anything else becomes v3. */
 static int shadowTlsVersion(String mode){
  try{int value=Integer.parseInt(mode==null?"":mode.trim());return value>=1&&value<=3?value:3;}
  catch(NumberFormatException invalid){return 3;}
 }

 public static JSONObject build(Profile profile,int port,int dnsPort,String user,String password)throws JSONException {
  String protocol=ProtocolNames.canonical(profile.protocol);
  JSONObject root=FullConfig.isFull(protocol)?FullConfig.root(profile):new JSONObject();
  if(protocol.equals("full-clash")){
   for(String key:new String[]{"external-controller","external-controller-tls","external-controller-unix","external-controller-pipe","external-ui","external-ui-url","secret"})root.remove(key);
   root.put("allow-lan",false).put("bind-address","127.0.0.1").put("mixed-port",port).put("port",0).put("socks-port",0).put("redir-port",0).put("tproxy-port",0);
   root.put("listeners",new JSONArray()).put("tunnels",new JSONArray()).put("tun",new JSONObject().put("enable",false));
   root.put("authentication",new JSONArray().put(user+":"+password)).put("skip-auth-prefixes",new JSONArray()).put("log-level","silent").put("geo-auto-update",false);
   root.put("find-process-mode","off");
   root.put("profile",new JSONObject().put("store-selected",false).put("store-fake-ip",false));
   JSONObject dns=root.optJSONObject("dns");if(dns==null)dns=new JSONObject();
   dns.put("enable",true).put("listen","127.0.0.1:"+dnsPort);
   // Fake-IP addresses cannot survive a child engine restart; real answers remain routable.
   dns.put("enhanced-mode","redir-host");
   if(!dns.has("nameserver"))dns.put("nameserver",new JSONArray().put("https://1.1.1.1/dns-query"));
   if(!dns.has("default-nameserver"))dns.put("default-nameserver",new JSONArray().put("1.1.1.1"));
   root.put("dns",dns);return root;
  }
  root.put("log",new JSONObject().put("disabled",true));root.remove("experimental");root.remove("services");
  if(protocol.equals("full-singbox")&&root.optJSONObject("route")!=null)root.getJSONObject("route").put("auto_detect_interface",false);
  String inboundTag=FullConfig.isFull(protocol)?FullConfig.inboundTag(root):"parvaz";
  root.put("inbounds",new JSONArray().put(new JSONObject().put("type","socks").put("tag",inboundTag).put("listen","127.0.0.1").put("listen_port",port).put("users",new JSONArray().put(new JSONObject().put("username",user).put("password",password)))));
  if(!protocol.equals("full-singbox")){
   root.put("outbounds",outbounds(profile,"proxy"));
   root.put("dns",new JSONObject().put("servers",new JSONArray().put(new JSONObject().put("type","https").put("tag","bootstrap").put("server","1.1.1.1").put("path","/dns-query"))));
   root.put("route",new JSONObject().put("final","proxy").put("default_domain_resolver",new JSONObject().put("server","bootstrap")));
  }else if(root.has("dns")){
   JSONObject route=root.optJSONObject("route");if(route==null)route=new JSONObject();JSONArray rules=new JSONArray().put(new JSONObject().put("action","sniff")).put(new JSONObject().put("protocol","dns").put("action","hijack-dns"));
   JSONArray old=route.optJSONArray("rules");if(old!=null)for(int i=0;i<old.length();i++)rules.put(old.get(i));route.put("rules",rules);root.put("route",route);
  }
  return root;
 }
}
