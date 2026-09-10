package com.parvaz.tunnel.core;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import android.app.Application;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={29,34},application=Application.class)
public class ReadinessConfigTest {
 private Profile profile(String protocol){Profile p=new Profile();p.protocol=protocol;return p;}
 private JSONObject base()throws Exception{return new JSONObject("{\"inbounds\":[{\"tag\":\"http\",\"port\":10809,\"listen\":\"127.0.0.1\",\"protocol\":\"http\"}],\"outbounds\":[{\"tag\":\"proxy\",\"protocol\":\"vless\",\"streamSettings\":{\"security\":\"tls\"}},{\"tag\":\"direct\",\"protocol\":\"freedom\"}],\"routing\":{\"rules\":[{\"type\":\"field\",\"domain\":[\"cp.cloudflare.com\",\"www.gstatic.com\"],\"outboundTag\":\"direct\"}]}}");}
 @Test public void directEndpointRuleCannotShortCircuitPinnedProbe()throws Exception {
  JSONObject before=base();ReadinessConfig.Plan plan=ReadinessConfig.prepare(before.toString(),profile("vless"),false,21000);assertTrue(plan.pinned);
  JSONObject after=new JSONObject(plan.config);JSONObject inbound=after.getJSONArray("inbounds").getJSONObject(1),first=after.getJSONObject("routing").getJSONArray("rules").getJSONObject(0);
  assertEquals("127.0.0.1",inbound.getString("listen"));assertEquals(21000,inbound.getInt("port"));assertEquals("http",inbound.getString("protocol"));assertFalse(inbound.has("sniffing"));
  assertEquals(inbound.getString("tag"),first.getJSONArray("inboundTag").getString(0));assertEquals("proxy",first.getString("outboundTag"));
  assertEquals(before.getJSONObject("routing").getJSONArray("rules").get(0).toString(),after.getJSONObject("routing").getJSONArray("rules").get(1).toString());
  assertEquals(before.getJSONArray("outbounds").toString(),after.getJSONArray("outbounds").toString());assertEquals(before.getJSONArray("inbounds").get(0).toString(),after.getJSONArray("inbounds").get(0).toString());
 }
 @Test public void fullXraySingleRemoteUsesItsActualTagNotAssumedProxy()throws Exception {
  JSONObject root=base();root.getJSONArray("outbounds").getJSONObject(0).put("tag","actual-node");ReadinessConfig.Plan p=ReadinessConfig.prepare(root.toString(),profile("full-xray"),false,21000);
  assertTrue(p.pinned);assertEquals("actual-node",new JSONObject(p.config).getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"));
 }
 @Test public void ambiguousFullXrayIsNotRandomlySelectedOrRewritten()throws Exception {
  JSONObject root=base();root.getJSONArray("outbounds").put(new JSONObject().put("tag","other").put("protocol","trojan"));String text=root.toString();
  ReadinessConfig.Plan p=ReadinessConfig.prepare(text,profile("full-xray"),false,21000);assertFalse(p.pinned);assertEquals(text,p.config);
 }
 @Test public void directNamedProxyOrDuplicateTagCannotBecomeRemoteProof()throws Exception {
  JSONObject root=base();root.getJSONArray("outbounds").getJSONObject(0).put("protocol","freedom");assertFalse(ReadinessConfig.prepare(root.toString(),profile("vless"),false,21000).pinned);
  root=base();root.getJSONArray("outbounds").getJSONObject(1).put("tag","proxy");assertFalse(ReadinessConfig.prepare(root.toString(),profile("vless"),false,21000).pinned);
 }
 @Test public void outerNativeRelayIsNotProofOfInnerRemoteRouting()throws Exception {
  for(String protocol:new String[]{"hy2","hysteria2","tuic","full-singbox","full-clash"}){
   assertFalse(ReadinessConfig.prepare(base().toString(),profile(protocol),false,21000).pinned);
   assertTrue(ReadinessConfig.prepare(base().toString(),profile(protocol),true,21000).pinned);
  }
 }
 @Test public void simpleNativeProfileIsProvableButDirectOrRulesAreNot()throws Exception {
  JSONObject root=new JSONObject("{\"outbounds\":[{\"type\":\"hysteria2\",\"tag\":\"proxy\"}],\"route\":{\"final\":\"proxy\"}}");
  assertTrue(ReadinessConfig.nativeRemoteOnly(root,"hysteria2"));
  root.getJSONObject("route").put("rules",new JSONArray().put(new JSONObject().put("action","direct")));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-singbox"));
  root.getJSONObject("route").remove("rules");root.getJSONArray("outbounds").put(new JSONObject().put("type","direct").put("tag","direct"));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-singbox"));
 }
 @Test public void exactInjectedNativeDnsRulesDoNotMakeHttpsDirect()throws Exception {
  JSONObject root=new JSONObject("{\"outbounds\":[{\"type\":\"socks\",\"tag\":\"node\"}],\"route\":{\"final\":\"node\",\"rules\":[{\"action\":\"sniff\"},{\"action\":\"hijack-dns\",\"protocol\":\"dns\"}]}}");
  assertTrue(ReadinessConfig.nativeRemoteOnly(root,"full-singbox"));root.getJSONObject("route").getJSONArray("rules").getJSONObject(1).put("protocol","tls");assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-singbox"));
 }
 private JSONObject clash()throws Exception{return new JSONObject("{\"proxies\":[{\"name\":\"node\",\"type\":\"trojan\"}],\"proxy-groups\":[{\"name\":\"group\",\"type\":\"select\",\"proxies\":[\"node\"]}],\"rules\":[\"MATCH,group\"]}");}
 @Test public void nativeClashGroupWithoutDirectCanBeProvenWithoutChangingRouting()throws Exception {
  JSONObject root=clash();String original=root.toString();assertTrue(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));assertEquals(original,root.toString());
  root.getJSONArray("proxy-groups").getJSONObject(0).getJSONArray("proxies").put("DIRECT");assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
 }
 @Test public void nativeClashImplicitDirectAndUnknownProviderAreNotProof()throws Exception {
  JSONObject root=clash();root.put("rules",new JSONArray().put("DOMAIN,example.org,group"));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
  root=clash();root.put("mode","global");assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
  root=clash();root.getJSONArray("proxy-groups").getJSONObject(0).put("use",new JSONArray().put("provider"));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
 }
 @Test public void nativeClashCyclesSubrulesAndDuplicateNamesFailClosed()throws Exception {
  JSONObject root=clash();root.getJSONArray("proxy-groups").getJSONObject(0).put("proxies",new JSONArray().put("group"));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
  root=clash();root.put("rules",new JSONArray().put("SUB-RULE,(NETWORK,TCP),group").put("MATCH,group"));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
  root=clash();root.getJSONArray("proxies").put(new JSONObject().put("name","group").put("type","trojan"));assertFalse(ReadinessConfig.nativeRemoteOnly(root,"full-clash"));
 }
}
