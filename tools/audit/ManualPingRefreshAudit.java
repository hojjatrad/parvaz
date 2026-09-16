package com.parvaz.tunnel.core;
import com.parvaz.tunnel.model.*;
import com.parvaz.tunnel.store.*;
import com.parvaz.tunnel.config.*;
import java.util.*;
/** Actual refresh/store ownership with local API substitutes; no user endpoints. */
public final class ManualPingRefreshAudit {
 static int checks;
 static void check(String message,boolean ok){if(!ok)throw new AssertionError(message);checks++;System.out.println("PASS MANUAL_PING_REFRESH: "+message);}
 static PanelRefreshTest.Setup setup(){PanelRefreshTest.Setup s=new PanelRefreshTest.Setup();s.sub("current","https://panel.example.invalid/sub");s.add(PanelRefreshTest.profile("current","one"));Profile second=PanelRefreshTest.profile("current","two");second.address="second.example.invalid";s.add(second);s.store.setPrimarySubscription("current");return s;}
 static SubscriptionUpdater.b response(PanelRefreshTest.Setup s,int kind){ImportResult parsed=new ImportResult();if(kind!=2)for(Profile p:s.store.activeProfiles())parsed.add(ProfileIdentity.copy(p));if(kind==1)parsed.reject("FIXTURE_PARTIAL",1);return new SubscriptionUpdater.b("", "upload=1; download=2; total=100",0,parsed);}
 static void pendingBeforeFetch(){
  PanelRefreshTest.Setup s=setup();Profile p=s.store.activeProfiles().get(0);ProfileStore.Measurement ticket=s.store.beginMeasurement(p);int[] calls={0};
  SubscriptionRefresh.Result r=SubscriptionRefresh.runActive(s.store,s.prefs,()->false,url->{calls[0]++;return response(s,0);},false);
  check("background fetch is deferred while a manual row is queued",calls[0]==0&&r.retryable&&r.codes.contains("MANUAL_LATENCY_ACTIVE"));
  check("queued ownership survives a deferred background refresh",s.store.ownsMeasurement(ticket));check("genuine result still publishes",s.store.finishMeasurement(ticket,124)&&p.ping==124);
 }
 static void startedDuringFetch(int kind){
  PanelRefreshTest.Setup s=setup();Profile p=s.store.activeProfiles().get(0);ProfileStore.Measurement[] ticket={null};
  SubscriptionRefresh.Result r=SubscriptionRefresh.runActive(s.store,s.prefs,()->false,url->{ticket[0]=s.store.beginMeasurement(p);return response(s,kind);},false);
  check("in-flight background commit defers for manual test; response kind="+kind,r.retryable&&r.codes.contains("MANUAL_LATENCY_ACTIVE"));
  check("response cannot replace the tested profile; kind="+kind,s.store.getActiveById(p.id)==p&&s.store.ownsMeasurement(ticket[0]));
  check("active test can publish after deferred response; kind="+kind,s.store.finishMeasurement(ticket[0],93)&&p.ping==93);
 }
 static void idleAndExplicitRefresh(){
  PanelRefreshTest.Setup s=setup();SubscriptionRefresh.Result idle=SubscriptionRefresh.runActive(s.store,s.prefs,()->false,url->response(s,0),false);
  check("idle automatic refresh still works",idle.updated==1&&!idle.retryable);
  Profile p=s.store.activeProfiles().get(0);ProfileStore.Measurement ticket=s.store.beginMeasurement(p);
  SubscriptionRefresh.Result explicit=SubscriptionRefresh.runActive(s.store,s.prefs,()->false,url->response(s,0),true);
  check("explicit refresh still invalidates old tests instead of publishing stale data",explicit.updated==1&&!s.store.ownsMeasurement(ticket));
  s.store.finishMeasurement(ticket,99);
  check("old result never reaches the replacement row",s.store.getActiveById(p.id).ping!=99);
 }
 static void sourceSwitchStillInvalidates(){
  PanelRefreshTest.Setup s=setup();Profile p=s.store.activeProfiles().get(0);ProfileStore.Measurement ticket=s.store.beginMeasurement(p);
  s.store.setPrimarySubscription(ProfileStore.MANUAL_GROUP);s.store.setPrimarySubscription("current");
  check("A-B-A source switch is still rejected",!s.store.ownsMeasurement(ticket));s.store.finishMeasurement(ticket,99);check("source switch never becomes a successful ping",p.ping==LatencyResult.CANCELLED);
 }
 public static void main(String[] args){pendingBeforeFetch();for(int kind=0;kind<3;kind++)startedDuringFetch(kind);idleAndExplicitRefresh();sourceSwitchStillInvalidates();System.out.println("MANUAL_PING_REFRESH_CHECKS="+checks);}
}
