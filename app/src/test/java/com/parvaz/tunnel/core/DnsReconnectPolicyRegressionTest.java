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
/** Generated-config invariants only, not a physical DNS/IPv6 leak or TTL test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class DnsReconnectPolicyRegressionTest {
 Prefs prefs;Profile profile;
 @Before public void before(){prefs=new Prefs(ApplicationProvider.getApplicationContext());prefs.f343a.edit().clear().putString("routing_mode","global").commit();profile=new Profile();profile.protocol="vless";profile.address="fixture.invalid";profile.port=443;profile.uuid="11111111-2222-3333-4444-555555555555";profile.security="tls";}
 JSONObject config()throws Exception{return new JSONObject(XrayConfigBuilder.b(profile,prefs,null,true,true));}
 JSONObject proxy(JSONObject root)throws Exception{JSONArray list=root.getJSONArray("outbounds");for(int i=0;i<list.length();i++)if(list.getJSONObject(i).optString("tag").equals("proxy"))return list.getJSONObject(i);throw new AssertionError("Missing proxy outbound");}
 @Test public void generatedDnsKeepsCoreCacheAndEncryptedDefaultResolvers()throws Exception{JSONObject dns=config().getJSONObject("dns");assertFalse(dns.getBoolean("disableCache"));JSONArray servers=dns.getJSONArray("servers");assertTrue(servers.length()>0);for(int i=0;i<servers.length();i++)assertTrue(servers.getString(i).startsWith("https://"));}
 @Test public void dnsInboundInGlobalModeIsExplicitlyRoutedToProxy()throws Exception{JSONArray rules=config().getJSONObject("routing").getJSONArray("rules");boolean found=false;for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);JSONArray tags=rule.optJSONArray("inboundTag");if(tags!=null&&tags.toString().contains("dns_inbound")){assertEquals("proxy",rule.getString("outboundTag"));found=true;}}assertTrue(found);}
 @Test public void noForcedMuxOrTlsRelaxationIsIntroducedByDefault()throws Exception{JSONObject proxy=proxy(config());assertFalse(proxy.getJSONObject("mux").getBoolean("enabled"));assertFalse(proxy.getJSONObject("streamSettings").getJSONObject("tlsSettings").getBoolean("allowInsecure"));}
 @Test public void visionStillDisablesMuxEvenIfUserEnabledIt()throws Exception{prefs.f343a.edit().putBoolean("mux_enabled",true).commit();profile.flow="xtls-rprx-vision";assertFalse(proxy(config()).getJSONObject("mux").getBoolean("enabled"));}
 @Test public void nativeRelayDoesNotInheritGlobalXrayMux()throws Exception{prefs.f343a.edit().putBoolean("mux_enabled",true).commit();Profile original=new Profile();original.protocol="hysteria2";Profile relay=new Profile();relay.protocol="socks";relay.address="127.0.0.1";relay.port=10810;JSONObject root=new JSONObject(ManagedConfig.xray(original,relay,prefs,0,true,true));assertFalse(proxy(root).has("mux"));}
}
