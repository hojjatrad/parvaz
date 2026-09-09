package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
import java.util.*;

public final class EngineConfig {
 private EngineConfig(){}
 public static boolean external(String p){return Arrays.asList("hysteria2","hy2","tuic","full-singbox","full-clash").contains(p);}
 public static JSONObject build(Profile profile,int port,int dnsPort,String user,String password)throws JSONException {
  String protocol=ProtocolNames.canonical(profile.protocol);
  JSONObject root=FullConfig.isFull(protocol)?FullConfig.root(profile):new JSONObject();
  if(protocol.equals("full-clash")){
   for(String key:new String[]{"external-controller","external-controller-tls","external-controller-unix","external-controller-pipe","external-ui","external-ui-url","secret"})root.remove(key);
   root.put("allow-lan",false).put("bind-address","127.0.0.1").put("mixed-port",port).put("port",0).put("socks-port",0).put("redir-port",0).put("tproxy-port",0);
   root.put("listeners",new JSONArray()).put("tunnels",new JSONArray()).put("tun",new JSONObject().put("enable",false));
   root.put("authentication",new JSONArray().put(user+":"+password)).put("skip-auth-prefixes",new JSONArray()).put("log-level","silent").put("geo-auto-update",false);
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
  String inboundTag="parvaz";JSONArray original=root.optJSONArray("inbounds");if(original!=null&&original.length()>0&&original.optJSONObject(0)!=null)inboundTag=original.getJSONObject(0).optString("tag","parvaz");
  root.put("inbounds",new JSONArray().put(new JSONObject().put("type","socks").put("tag",inboundTag).put("listen","127.0.0.1").put("listen_port",port).put("users",new JSONArray().put(new JSONObject().put("username",user).put("password",password)))));
  if(!protocol.equals("full-singbox")){
   JSONObject outbound=new JSONObject().put("type",protocol).put("tag","proxy").put("server",profile.address).put("server_port",profile.port);
   JSONObject tls=new JSONObject().put("enabled",true).put("server_name",profile.sni.isEmpty()?profile.address:profile.sni).put("insecure",profile.allowInsecure);
   if(!profile.alpn.isEmpty()){JSONArray alpn=new JSONArray();for(String value:profile.alpn.split(","))if(!value.trim().isEmpty())alpn.put(value.trim());tls.put("alpn",alpn);}
   outbound.put("tls",tls);
   if(protocol.equals("hysteria2")){
    outbound.put("password",profile.uuid);
    if(!profile.mode.isEmpty())outbound.put("obfs",new JSONObject().put("type",profile.mode).put("password",profile.host));
   }else if(protocol.equals("tuic")){
    outbound.put("uuid",profile.uuid).put("password",profile.quicKey).put("congestion_control",profile.mode.isEmpty()?"bbr":profile.mode).put("udp_relay_mode",profile.headerType.isEmpty()?"native":profile.headerType);
   }else throw new IllegalArgumentException("Unsupported engine protocol");
   root.put("outbounds",new JSONArray().put(outbound));
   root.put("dns",new JSONObject().put("servers",new JSONArray().put(new JSONObject().put("type","https").put("tag","bootstrap").put("server","1.1.1.1").put("path","/dns-query"))));
   root.put("route",new JSONObject().put("final","proxy").put("default_domain_resolver",new JSONObject().put("server","bootstrap")));
  }else if(root.has("dns")){
   JSONObject route=root.optJSONObject("route");if(route==null)route=new JSONObject();JSONArray rules=new JSONArray().put(new JSONObject().put("action","sniff")).put(new JSONObject().put("protocol","dns").put("action","hijack-dns"));
   JSONArray old=route.optJSONArray("rules");if(old!=null)for(int i=0;i<old.length();i++)rules.put(old.get(i));route.put("rules",rules);root.put("route",route);
  }
  return root;
 }
}
