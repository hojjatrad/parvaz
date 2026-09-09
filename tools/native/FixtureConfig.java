package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import org.json.JSONObject;
public final class FixtureConfig {
 public static void main(String[] args)throws Exception {
  Profile p=new Profile();p.protocol=args[0];p.address="127.0.0.1";p.port=Integer.parseInt(args[1]);p.uuid=p.protocol.equals("tuic")?"11111111-1111-4111-8111-111111111111":"integration-only-secret";p.quicKey="integration-only-secret";p.sni="localhost";p.alpn="h3";p.headerType="native";
  JSONObject root=EngineConfig.build(p,Integer.parseInt(args[2]),0,"test","integration-local-password");
  root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("tls").put("certificate_path",args[3]);
  System.out.println(root);
 }
}
