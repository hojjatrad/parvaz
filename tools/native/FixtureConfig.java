package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import org.json.JSONObject;
public final class FixtureConfig {
 public static void main(String[] args)throws Exception {
  Profile p=new Profile();p.protocol=args[0];p.address="127.0.0.1";p.port=Integer.parseInt(args[1]);p.uuid=p.protocol.equals("tuic")?"11111111-1111-4111-8111-111111111111":"integration-only-secret";p.quicKey="integration-only-secret";p.sni="localhost";p.alpn="h3";p.headerType="native";
  if(p.protocol.equals("full-clash")){
   JSONObject proxy=new JSONObject().put("name","node").put("type","socks5").put("server",p.address).put("port",p.port).put("username","remote-user").put("password","integration-only-secret").put("udp",true);
   p.rawJson=new JSONObject().put("proxies",new org.json.JSONArray().put(proxy)).put("proxy-groups",new org.json.JSONArray().put(new JSONObject().put("name","via").put("type","select").put("proxies",new org.json.JSONArray().put("node")))).put("rules",new org.json.JSONArray().put("MATCH,via")).toString();
  }
  JSONObject root=EngineConfig.build(p,Integer.parseInt(args[2]),0,"test","integration-local-password");
  if(!p.protocol.equals("full-clash"))root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls").put("certificate_path",args[3]);
  System.out.println(EngineConfig.serialize(root,p.protocol));
 }
}
