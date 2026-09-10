package com.parvaz.tunnel.core;
import android.content.Context;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded actual-engine measurements, ranked by local response history. A probe
 * success is not proof that the active VPN or every destination is ready. Native
 * work may ignore interruption; BoundedProbeRace retains its global slot until it exits. */
public final class HappyEyeballs {
 public static final int DEFAULT_PARALLEL=2;
 private static final long RACE_TIMEOUT_MS=24000;
 private HappyEyeballs(){}
 public static final class Result {
  public Profile winner;public int delayMs=-1,probed;public long elapsedMs;
  public boolean cancelled,deferred;
  public long networkEpoch;
  public String networkContext="";
  boolean currentNetwork(Context c){return NetworkEpoch.owns(networkEpoch)&&networkContext.equals(NetContext.key(c));}
  public final List<Profile> failed=new ArrayList<>();
 }
 public static Result race(Context context,List<Profile> profiles){return race(context,profiles,DEFAULT_PARALLEL);}
 public static Result race(Context context,List<Profile> profiles,int parallel){return race(context,profiles,parallel,()->true);}
 static Result race(Context context,List<Profile> profiles,int parallel,BooleanSupplier current){
  return race(context,profiles,parallel,current,0);
 }
 static Result race(Context context,List<Profile> profiles,int parallel,BooleanSupplier current,int neutralOffset){
  Context app=context.getApplicationContext();ProfileStore store=ProfileStore.f(app);
  String source=store.primarySubscription();
  ArrayList<Profile> allowed=activeCandidates(profiles,store.activeProfiles());
  ServerMemory history=new ServerMemory(app);
  ArrayList<Profile> ranked=history.rank(app,allowed);
  SwitchPolicy.rotateUnconfirmed(ranked,neutralOffset);
  Map<String,Integer> scores=new HashMap<>();Map<String,Double> jitter=new HashMap<>();
  for(Profile p:ranked){ServerMemory.Entry entry=history.entryFor(app,p.id);scores.put(p.id,entry==null?50:entry.score());jitter.put(p.id,entry==null?0:entry.jitter);}
  String pingUrl=new Prefs(app).f343a.getString("ping_url","https://www.gstatic.com/generate_204");
  String network=NetContext.key(app);long epoch=NetworkEpoch.current();
  BooleanSupplier owns=()->current.getAsBoolean()&&NetworkEpoch.owns(epoch)&&source.equals(store.primarySubscription());
  // Same adapter as manual latency tests: HY2/TUIC/full configurations must use
  // their actual engine instead of being coerced into an Xray-only probe.
  BoundedProbeRace.Result<Profile> measured=BoundedProbeRace.run(ranked,parallel,RACE_TIMEOUT_MS,
   profile->ProxyMeasurement.measure(app,profile,pingUrl),owns,(p,delay)->SwitchPolicy.cost(delay,scores.get(p.id),jitter.get(p.id)),750);
  Result result=new Result();result.networkEpoch=epoch;result.networkContext=network;result.probed=measured.probed;result.elapsedMs=measured.elapsedMs;
  result.cancelled=measured.status==BoundedProbeRace.Status.CANCELLED||!owns.getAsBoolean()||!network.equals(NetContext.key(app));
  result.deferred=measured.status==BoundedProbeRace.Status.BUSY;
  if(result.cancelled)return result;
  List<Profile> active=store.activeProfiles();
  if(measured.winner!=null&&!activeCandidates(Collections.singletonList(measured.winner),active).isEmpty()){
   result.winner=measured.winner;result.delayMs=(int)Math.min(Integer.MAX_VALUE,measured.delay);
  }else if(measured.winner!=null){result.cancelled=true;return result;}
  // Never mark untested, timed-out or cancelled losers as actual failures.
  if(network.equals(NetContext.key(app))&&owns.getAsBoolean()){
   ServerMemory memory=new ServerMemory(app);
   if(result.winner!=null)memory.recordSuccess(app,result.winner,result.delayMs);
   result.failed.addAll(activeCandidates(measured.failed,active));
   for(Profile failed:result.failed)memory.recordFailure(app,failed);
  }
  LogBuffer.listener(result.winner!=null?"Candidate proxy response confirmed; active tunnel readiness remains separate":"Candidate proxy response not confirmed");
  return result;
 }
 static ArrayList<Profile> activeCandidates(List<Profile> requested,List<Profile> active){
  ArrayList<Profile> result=new ArrayList<>();if(requested==null)return result;
  Map<String,Profile> byId=new HashMap<>();for(Profile profile:active)if(profile!=null)byId.put(profile.id,profile);
  Set<String> used=new HashSet<>();
  for(Profile candidate:requested){
   if(candidate==null||used.contains(candidate.id))continue;Profile latest=byId.get(candidate.id);
   if(latest!=null&&ProfileIdentity.fingerprint(latest).equals(ProfileIdentity.fingerprint(candidate))){result.add(ProfileIdentity.copy(latest));used.add(latest.id);}
  }
  return result;
 }
}
