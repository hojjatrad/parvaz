import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.core.ProtocolSupport;
import com.parvaz.tunnel.model.Profile;
import com.parvaz.tunnel.store.Prefs;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Positive regression tests over actual parsers/model/outbound builder.
 * Android URI, native-core validation, routing and preferences persistence are NOT tested. */
public class ParserAudit {
    static int count;
    static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        count++;
        System.out.println("PASS: " + name);
    }
    static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
    static void rejects(String name, Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(name, true); return; }
        throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        String block = "proxies:\n  - name: demo\n    type: ss\n    server: example.invalid\n    port: 443\n    cipher: aes-128-gcm\n    password: dummy\n";
        Profile ss = LinkParser.parseMany(block).get(0);
        check("Clash SS canonicalized", ss.protocol.equals("shadowsocks"));
        check("Actual builder accepts Clash SS", XrayConfigBuilder.c(ss, new Prefs()).getString("protocol").equals("shadowsocks"));
        ss.protocol = " SS ";
        check("Actual builder also accepts an unnormalized SS alias", XrayConfigBuilder.c(ss, new Prefs()).getString("protocol").equals("shadowsocks"));
        Profile trojan = LinkParser.parseMany("proxies:\n  - name: demo\n    type: trojan\n    server: example.invalid\n    port: 443\n    password: dummy\n").get(0);
        check("Clash Trojan has implicit TLS", trojan.security.equals("tls"));
        check("Trojan builder emits TLS", XrayConfigBuilder.c(trojan,new Prefs()).getJSONObject("streamSettings").getString("security").equals("tls"));
        Profile tuic = LinkParser.parseMany("{\"outbounds\":[{\"type\":\"tuic\",\"tag\":\"demo\",\"server\":\"example.invalid\",\"server_port\":443,\"uuid\":\"11111111-1111-4111-8111-111111111111\",\"password\":\"dummy\"}]}").get(0);
        check("Sing-box TUIC preserves second credential", tuic.quicKey.equals("dummy"));
        check("TUIC selects the bundled native engine", ProtocolSupport.isSupported(tuic) && !ProtocolSupport.isKnownUnsupported(tuic));
        String raw = "{\"outbounds\":[{\"protocol\":\"socks\",\"settings\":{\"servers\":[{\"address\":\"example.invalid\",\"port\":1080}]}}]}";
        check("Single Xray JSON accepted", LinkParser.parseMany(raw).size()==1);
        check("JSON config array accepted", LinkParser.parseMany("["+raw+","+raw+"]").size()==2);
        check("Mixed JSON array excludes panel metadata", LinkParser.parseMany("[{},"+raw+"]").size()==1);
        check("Base64 Clash redispatched", LinkParser.parseMany(base64(block)).size()==1);
        check("Base64 Xray JSON redispatched", LinkParser.parseMany(base64(raw)).size()==1);
        check("Base64 array redispatched", LinkParser.parseMany(base64("["+raw+"]")).size()==1);
        check("UTF-8 BOM accepted", LinkParser.parseMany("\uFEFF"+raw).size()==1);
        check("Panel metadata is not a fake server", LinkParser.parseMany("{\"data_limit\":1000,\"used_traffic\":10,\"expire\":2000000000}").isEmpty());
        check("Empty JSON rejected", LinkParser.parseMany("{}").isEmpty());
        check("Direct-only raw JSON rejected", LinkParser.parseMany("{\"outbounds\":[{\"protocol\":\"freedom\"}]}").isEmpty());
        check("Missing settings rejected", LinkParser.parseMany("{\"protocol\":\"socks\"}").isEmpty());
        check("Out-of-range raw port rejected", LinkParser.parseMany(raw.replace("1080","70000")).isEmpty());
        Profile custom=LinkParser.parseMany(raw).get(0);
        check("Validated custom is offered by capability filter", ProtocolSupport.isSupported(custom));
        check("Custom builder extracts supported outbound", XrayConfigBuilder.c(custom,new Prefs()).getString("protocol").equals("socks"));
        custom.rawJson="{}";
        check("Legacy invalid custom is not offered", !ProtocolSupport.isSupported(custom));
        rejects("Legacy invalid custom builder fails closed", () -> { try { XrayConfigBuilder.c(custom,new Prefs()); } catch (JSONException e) { throw new AssertionError(e); } });
        Profile http=LinkParser.parseMany("{\"outbounds\":[{\"type\":\"http\",\"server\":\"example.invalid\",\"server_port\":8080,\"username\":\"alice\",\"password\":\"dummy\"}]}").get(0);
        JSONObject built=XrayConfigBuilder.c(http,new Prefs());
        check("HTTP protocol has a matching builder", ProtocolSupport.isSupported(http) && built.getString("protocol").equals("http"));
        JSONObject user=built.getJSONObject("settings").getJSONArray("servers").getJSONObject(0).getJSONArray("users").getJSONObject(0);
        check("HTTP username and password preserved", user.getString("user").equals("alice") && user.getString("pass").equals("dummy"));
        check("Unknown protocol warned", ProtocolSupport.isKnownUnsupported("not-a-protocol"));
        check("Aliases are canonical", ProtocolNames.canonical("SOCKS5").equals("socks") && ProtocolNames.canonical("wg").equals("wireguard") && ProtocolNames.canonical("HY2").equals("hysteria2"));
        check("Xray JSON not misclassified by unrelated server field", !SingBoxParser.isSingBox(raw.substring(0,raw.length()-1)+",\"server\":\"metadata\"}"));
        StringBuilder deep = new StringBuilder();
        for (int i=0;i<70;i++) deep.append('[');
        for (int i=0;i<70;i++) deep.append(']');
        rejects("Excessive JSON nesting rejected", () -> LinkParser.parseMany(deep.toString()));
        rejects("Oversized input rejected", () -> LinkParser.parseMany("x".repeat(LinkParser.MAX_INPUT_CHARS+1)));
        StringBuilder many=new StringBuilder("[");
        for (int i=0;i<=LinkParser.MAX_PROFILES;i++) { if(i>0) many.append(','); many.append(raw); }
        many.append(']');
        rejects("Too many profiles fail without partial return", () -> LinkParser.parseMany(many.toString()));
        String vmess = "vmess://" + base64("{\"v\":\"2\",\"ps\":\"demo\",\"add\":\"example.invalid\",\"port\":\"443\",\"id\":\"11111111-1111-4111-8111-111111111111\",\"aid\":\"0\",\"net\":\"tcp\",\"tls\":\"tls\"}");
        check("Standard VMess JSON link imports", LinkParser.parseMany(vmess).size()==1);
        check("CRLF link list imports both nodes", LinkParser.parseMany(vmess+"\r\n"+vmess).size()==2);
        check("Base64 share-link list imports both nodes", LinkParser.parseMany(base64(vmess+"\n"+vmess)).size()==2);
        check("Unknown line does not discard valid VMess", LinkParser.parseMany("not-a-link\n"+vmess).size()==1);
        System.out.println("PARSER/BUILDER TOTAL: " + count + " regression assertions passed.");
        System.out.println("NOT COVERED/FIXED: full Clash YAML, all Sing-box fields, Android URI decoding, full raw-config routing, native VPN connectivity.");
    }
}
