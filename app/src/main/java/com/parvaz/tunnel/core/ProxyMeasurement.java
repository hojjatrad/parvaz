package com.parvaz.tunnel.core;
import android.content.Context;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import libv2ray.Libv2ray;
/** Uses the actual engine for both connect and latency tests; no successful TCP-only substitute. */
public final class ProxyMeasurement {
 private ProxyMeasurement(){}
 public static long measure(Context context,Profile profile,String url)throws Exception {
  ExternalCore external=null;
  try{
   Prefs prefs=new Prefs(context);String config;
   if(EngineConfig.external(profile.protocol)){
    external=ExternalCore.start(context,profile,null);config=ManagedConfig.xray(profile,external.relay(profile),prefs,external.dnsPort,false,false);
   }else if(profile.protocol.equals("full-xray")){
    Profile dummy=new Profile();dummy.protocol="socks";dummy.address="127.0.0.1";dummy.port=10810;
    config=ManagedConfig.xray(profile,dummy,prefs,0,false,false);
   }else config=XrayConfigBuilder.b(profile,prefs,null,false,false);
   return Libv2ray.measureOutboundDelay(config,url);
  }finally{if(external!=null)external.close();}
 }
}
