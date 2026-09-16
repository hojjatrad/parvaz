package com.parvaz.tunnel.core;
import android.content.Context;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.*;
import libv2ray.*;
import org.json.*;
import java.net.*;

/** Isolated actual-engine HTTP probes. The selected route must be provable;
 * multiple successful responses are required, never just an open socket. */
public final class ProxyMeasurement {
 private static final ProbeAdmission CAPACITY=new ProbeAdmission(3);
 private ProxyMeasurement(){}
 public static long measure(Context context,Profile profile,String url)throws Exception {
  return measure(context,profile,url,false);
 }
 /** Manual tests wait their turn rather than silently dropping queued profiles. */
 public static long measureQueued(Context context,Profile profile,String url)throws Exception {
  return measure(context,profile,url,true);
 }
 public static long measureStrictTarget(Context context,Profile profile,String url)throws Exception {return measure(context,profile,url,true,true);}
 private static long measure(Context context,Profile profile,String url,boolean wait)throws Exception {return measure(context,profile,url,wait,false);}
 public static long measureWithPreferences(Context context,Profile profile,String url,Prefs snapshot)throws Exception{return measure(context,profile,url,true,false,snapshot);}
 private static long measure(Context context,Profile profile,String url,boolean wait,boolean strictTarget)throws Exception{return measure(context,profile,url,wait,strictTarget,null);}
 private static long measure(Context context,Profile profile,String url,boolean wait,boolean strictTarget,Prefs snapshot)throws Exception {
  // The active profile is measured through its existing pinned listener. Do not
  // start a competing native controller just to test the already-live route.
  CoreManager manager=CoreManager.b();CoreManager.LiveProbe live=manager.liveProbe(profile);
  if(live!=null&&snapshot==null){
   long measured=wait?VerifiedProbe.measureDetailed(live.port,url,true,strictTarget):VerifiedProbe.measure(live.port,url,true);
   return manager.ownsLiveProbe(live)?measured:VerifiedProbe.UNKNOWN;
  }
  if(!CAPACITY.enter(wait))return wait?ProbeAdmission.BUSY:VerifiedProbe.UNKNOWN;
  ExternalCore external=null;CoreController controller=null;
  try{
   Prefs prefs=snapshot==null?new Prefs(context):snapshot;String config;boolean nativeProfile=EngineConfig.external(profile.protocol);
   String chainId=prefs.f343a.getString("chain_profile","");Profile chain=chainId==null||chainId.isEmpty()||chainId.equals(profile.id)?null:ProfileStore.f(context).getById(chainId);
   if(chain!=null&&(nativeProfile||FullConfig.isFull(profile.protocol)||EngineConfig.external(chain.protocol)))return wait?VerifiedProbe.ROUTE_UNVERIFIED:VerifiedProbe.UNKNOWN;
   if(nativeProfile){
    external=ExternalCore.start(context,profile,null);config=ManagedConfig.xray(profile,external.relay(profile),prefs,external.dnsPort,false,false);
   }else if(profile.protocol.equals("full-xray")){
    Profile dummy=new Profile();dummy.protocol="socks";dummy.address="127.0.0.1";dummy.port=10810;
    config=ManagedConfig.xray(profile,dummy,prefs,0,false,false);
   }else config=XrayConfigBuilder.b(profile,prefs,chain,false,false);
   JSONObject root=new JSONObject(config);root.put("inbounds",new JSONArray());
   int port;try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
   ReadinessConfig.Plan plan=ReadinessConfig.prepare(root.toString(),profile,external!=null&&external.readinessRemoteOnly,port);
   if(!plan.pinned)return wait?VerifiedProbe.ROUTE_UNVERIFIED:VerifiedProbe.UNKNOWN;
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   controller=Libv2ray.newCoreController(new CoreCallbackHandler(){public long startup(){return 0;}public long shutdown(){return 0;}public long onEmitStatus(long code,String message){return 0;}});
   CoreManager.b().startIsolatedProbe(controller,plan.config);
   if(!controller.getIsRunning()||(external!=null&&!external.isRunning()))return wait?VerifiedProbe.START_FAILED:-1;
   return wait?VerifiedProbe.measureDetailed(port,url,true,strictTarget):VerifiedProbe.measure(port,url,true);
  }finally{
   // Keep capacity until the Xray instance really returns from StopLoop.
   try{if(controller!=null)controller.stopLoop();}finally{if(external!=null)external.close();CAPACITY.exit();}
  }
 }
}
