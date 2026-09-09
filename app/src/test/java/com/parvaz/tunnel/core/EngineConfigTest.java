package com.parvaz.tunnel.core;
import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={29,34},application=Application.class)
public class EngineConfigTest {
 @Test public void quicCredentialsTlsAndLocalAuthenticationArePreserved()throws Exception {
  Profile hy=LinkParser.parseMany("hy2://test-password@server.invalid:443?sni=test.invalid&obfs=salamander&obfs-password=obfs-secret#demo").get(0);
  JSONObject root=EngineConfig.build(hy,10810,10811,"local","local-secret");JSONObject outbound=root.getJSONArray("outbounds").getJSONObject(0);
  assertEquals("test-password",outbound.getString("password"));assertEquals("obfs-secret",outbound.getJSONObject("obfs").getString("password"));assertFalse(outbound.getJSONObject("tls").getBoolean("insecure"));
  assertEquals("127.0.0.1",root.getJSONArray("inbounds").getJSONObject(0).getString("listen"));assertEquals("local-secret",root.getJSONArray("inbounds").getJSONObject(0).getJSONArray("users").getJSONObject(0).getString("password"));
  Profile tuic=LinkParser.parseMany("tuic://11111111-1111-4111-8111-111111111111:server-password@server.invalid:443").get(0);
  JSONObject t=EngineConfig.build(tuic,10810,10811,"local","local-secret").getJSONArray("outbounds").getJSONObject(0);
  assertEquals("11111111-1111-4111-8111-111111111111",t.getString("uuid"));assertEquals("server-password",t.getString("password"));assertTrue(ProtocolSupport.isSupported(tuic));
 }
 @Test public void uriPasswordsKeepLiteralPlusAndPercentEscapes()throws Exception {
  assertEquals("a+b",LinkParser.parseMany("hy2://a+b@server.invalid:443").get(0).uuid);
  assertEquals("secret%2F+",LinkParser.parseMany("hy2://secret%252F%2B@server.invalid:443").get(0).uuid);
  assertEquals("secret%2F+",LinkParser.parseMany("tuic://11111111-1111-4111-8111-111111111111:secret%252F%2B@server.invalid:443").get(0).quicKey);
  assertEquals("secret%2F+",LinkParser.parseMany("trojan://secret%252F%2B@server.invalid:443").get(0).uuid);
 }
 @Test public void wireguardBase64UserInfoPreservesPlus()throws Exception {
  byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)251);
  String key=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
  String link="wireguard://"+key.replace("/","%2F")+"@server.invalid:51820?publickey="+java.net.URLEncoder.encode(key,"UTF-8");
  assertEquals(key,LinkParser.parseWireguard(link).uuid);
 }
 @Test public void mihomoSerializationIsYamlCompatibleAndLossless()throws Exception {
  JSONObject root=new JSONObject().put("url","https://example.invalid/path").put("password","a\\/b+%2F");
  String text=EngineConfig.serialize(root,"full-clash");
  java.util.Map<?,?> yaml=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions())).load(text);
  assertEquals(root.getString("password"),yaml.get("password"));assertEquals(root.getString("url"),yaml.get("url"));
  assertEquals(root.getString("password"),new JSONObject(text).getString("password"));
 }
 @Test public void fullClashKeepsRulesGroupsAndScalarPasswordsWithoutPublicListeners()throws Exception {
  String yaml="mixed-port: 7890\nallow-lan: true\nexternal-controller: 0.0.0.0:9090\nproxies:\n - name: node\n   type: trojan\n   server: example.invalid\n   port: 443\n   password: yes\nproxy-groups:\n - name: group\n   type: select\n   proxies: [node]\nrules: [\"MATCH,group\"]\n";
  Profile p=FullConfig.parse(yaml);JSONObject original=new JSONObject(p.rawJson);assertEquals("yes",original.getJSONArray("proxies").getJSONObject(0).getString("password"));
  JSONObject built=EngineConfig.build(p,10810,10811,"local","local-secret");assertEquals(original.getJSONArray("rules").toString(),built.getJSONArray("rules").toString());assertTrue(built.has("proxy-groups"));assertFalse(built.getBoolean("allow-lan"));assertFalse(built.has("external-controller"));assertEquals("127.0.0.1",built.getString("bind-address"));assertEquals(10810,built.getInt("mixed-port"));
  assertEquals("001234",new JSONObject(FullConfig.parse(yaml.replace("password: yes","password: 001234")).rawJson).getJSONArray("proxies").getJSONObject(0).getString("password"));
 }
 @Test public void fullImportRejectsMetadataAliasesScriptsAndExternalFiles()throws Exception {
  for(String text:new String[]{"{\"data\":{\"traffic\":12}}","proxies: &p []\nrules: *p","{\"proxies\":[],\"rules\":[\"MATCH,DIRECT\"]}","{\"outbounds\":[{\"type\":\"direct\"}]}","{\"proxies\":[{}],\"rules\":[\"MATCH,DIRECT\"],\"script\":\"run\"}","{\"proxies\":[{}],\"rules\":[\"MATCH,DIRECT\"],\"proxy-providers\":{\"x\":{\"type\":\"file\",\"path\":\"../../private\"}}}"}){
   try{FullConfig.parse(text);fail("Unsafe full input accepted");}catch(Exception expected){assertFalse(expected instanceof org.junit.internal.AssumptionViolatedException);}
  }
 }
 @Test public void fullSingBoxRetainsRoutingAndFullXrayRetainsAllOutbounds()throws Exception {
  Profile sing=FullConfig.parse("{\"outbounds\":[{\"type\":\"socks\",\"tag\":\"node\",\"server\":\"example.invalid\",\"server_port\":1080}],\"route\":{\"final\":\"node\"},\"inbounds\":[{\"type\":\"tun\",\"tag\":\"vpn\"}]}");
  JSONObject built=EngineConfig.build(sing,10810,10811,"local","local-secret");assertEquals("node",built.getJSONObject("route").getString("final"));assertEquals("vpn",built.getJSONArray("inbounds").getJSONObject(0).getString("tag"));
  Profile x=FullConfig.parse("{\"outbounds\":[{\"protocol\":\"socks\",\"tag\":\"node\",\"settings\":{\"servers\":[{\"address\":\"example.invalid\",\"port\":1080}]}},{\"protocol\":\"freedom\",\"tag\":\"direct\"}],\"routing\":{\"rules\":[{\"type\":\"field\",\"domain\":[\"example.org\"],\"outboundTag\":\"direct\"}]}}");
  Profile relay=new Profile();relay.protocol="socks";relay.address="127.0.0.1";relay.port=10810;
  JSONObject xr=new JSONObject(ManagedConfig.xray(x,relay,new Prefs(ApplicationProvider.getApplicationContext()),0,true,true));assertEquals(2,xr.getJSONArray("outbounds").length());assertEquals("direct",xr.getJSONObject("routing").getJSONArray("rules").getJSONObject(0).getString("outboundTag"));assertFalse(xr.has("api"));
 }
}
