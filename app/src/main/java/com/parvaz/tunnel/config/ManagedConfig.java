package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import org.json.*;

/** Keep Android's TUN/local listeners while handing full routing to the selected engine. */
public final class ManagedConfig {
 private ManagedConfig(){}
 public static String xray(Profile original,Profile relay,Prefs prefs,int dnsPort,boolean inbounds,boolean tun)throws JSONException {
  JSONObject template=new JSONObject(XrayConfigBuilder.b(relay,prefs,null,inbounds,tun));
  if(original.protocol.equals("full-xray")){
   JSONObject root=FullConfig.root(original);root.put("inbounds",template.optJSONArray("inbounds")==null?new JSONArray():template.getJSONArray("inbounds"));root.remove("api");root.remove("reverse");root.remove("metrics");root.remove("observatory");root.remove("burstObservatory");
   root.put("log",new JSONObject().put("loglevel","warning")).put("stats",new JSONObject()).put("policy",template.getJSONObject("policy"));return root.toString();
  }
  JSONArray outbounds=template.getJSONArray("outbounds");JSONObject proxy=null;
  for(int i=0;i<outbounds.length();i++)if(outbounds.getJSONObject(i).optString("tag").equals("proxy")){proxy=outbounds.getJSONObject(i);proxy.remove("streamSettings");proxy.remove("mux");}
  if(FullConfig.isFull(original.protocol)){
   if(template.has("inbounds")){JSONArray listeners=template.getJSONArray("inbounds");for(int i=0;i<listeners.length();i++)listeners.getJSONObject(i).remove("sniffing");}
   JSONArray only=new JSONArray().put(proxy);template.remove("dns");template.remove("routing");
   if(original.protocol.equals("full-clash")){
    only.put(new JSONObject().put("tag","native-dns").put("protocol","dns").put("settings",new JSONObject().put("address","127.0.0.1").put("port",dnsPort).put("network","udp")));
    template.put("routing",new JSONObject().put("rules",new JSONArray().put(new JSONObject().put("type","field").put("port","53").put("outboundTag","native-dns"))));
   }
   template.put("outbounds",only);
  }
  return template.toString();
 }
}
