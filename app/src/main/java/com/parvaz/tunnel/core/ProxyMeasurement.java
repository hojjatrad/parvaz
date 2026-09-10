package com.parvaz.tunnel.core;
import android.content.Context;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.*;
import libv2ray.*;
import org.json.*;
import java.net.*;
import java.util.concurrent.Semaphore;
/** Isolated actual-engine HTTP probes. The selected route must be provable;
 * two successful responses are required, never just an open socket. */
public final class ProxyMeasurement {
 private static final Semaphore CAPACITY=new Semaphore(3);
 private ProxyMeasurement(){}
 public static long measure(Context context,Profile profile,String url)throws Exception {
  if(!CAPACITY.tryAcquire())return VerifiedProbe.UNKNOWN;
  ExternalCore external=null;CoreController controller=null;
  try{
   Prefs prefs=new Prefs(context);String config;boolean nativeProfile=EngineConfig.external(profile.protocol);
   String chainId=prefs.f343a.getString("chain_profile","");Profile chain=chainId==null||chainId.isEmpty()||chainId.equals(profile.id)?null:ProfileStore.f(context).getById(chainId);
   if(chain!=null&&(nativeProfile||FullConfig.isFull(profile.protocol)||EngineConfig.external(chain.protocol)))return VerifiedProbe.UNKNOWN;
   if(nativeProfile){
    external=ExternalCore.start(context,profile,null);config=ManagedConfig.xray(profile,external.relay(profile),prefs,external.dnsPort,false,false);
   }else if(profile.protocol.equals("full-xray")){
    Profile dummy=new Profile();dummy.protocol="socks";dummy.address="127.0.0.1";dummy.port=10810;
    config=ManagedConfig.xray(profile,dummy,prefs,0,false,false);
   }else config=XrayConfigBuilder.b(profile,prefs,chain,false,false);
   JSONObject root=new JSONObject(config);root.put("inbounds",new JSONArray());
   int port;try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
   ReadinessConfig.Plan plan=ReadinessConfig.prepare(root.toString(),profile,external!=null&&external.readinessRemoteOnly,port);
   if(!plan.pinned)return VerifiedProbe.UNKNOWN;
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   controller=Libv2ray.newCoreController(new CoreCallbackHandler(){public long startup(){return 0;}public long shutdown(){return 0;}public long onEmitStatus(long code,String message){return 0;}});
   CoreManager.b().startIsolatedProbe(controller,plan.config);
   if(!controller.getIsRunning()||(external!=null&&!external.isRunning()))return -1;
   return VerifiedProbe.measure(port,url,true);
  }finally{
   // Keep capacity until the Xray instance really returns from StopLoop.
   try{if(controller!=null)controller.stopLoop();}finally{if(external!=null)external.close();CAPACITY.release();}
  }
 }
}
