import com.parvaz.tunnel.config.*;
import com.parvaz.tunnel.core.ProtocolSupport;
import com.parvaz.tunnel.model.Profile;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Characterization tests: reproduce defects, NOT assertions of correct behavior.
 * Does not exercise Android Uri or any network/native/VPN functionality. */
public class ParserAudit {
    static int count;
    static void observed(String name, boolean condition) {
        if (!condition) throw new AssertionError("Not reproduced: " + name);
        count++;
        System.out.println("REPRODUCED: " + name);
    }
    public static void main(String[] args) {
        String block = "proxies:\n  - name: demo\n    type: ss\n    server: example.invalid\n    port: 443\n    cipher: aes-128-gcm\n    password: dummy\n";
        List<Profile> ss = LinkParser.parseMany(block);
        observed("Clash SS keeps protocol 'ss', whereas builder dispatch expects 'shadowsocks'", ss.size()==1 && ss.get(0).protocol.equals("ss"));
        observed("Clash inline mapping imports zero nodes", LinkParser.parseMany("proxies:\n  - {name: demo, type: ss, server: example.invalid, port: 443, cipher: aes-128-gcm, password: dummy}\n").isEmpty());
        List<Profile> trojan = LinkParser.parseMany("proxies:\n  - name: demo\n    type: trojan\n    server: example.invalid\n    port: 443\n    password: dummy\n");
        observed("Clash Trojan without optional tls field loses implicit TLS", trojan.size()==1 && trojan.get(0).security.isEmpty());
        List<Profile> tuic = LinkParser.parseMany("{\"outbounds\":[{\"type\":\"tuic\",\"tag\":\"demo\",\"server\":\"example.invalid\",\"server_port\":443,\"uuid\":\"11111111-1111-4111-8111-111111111111\",\"password\":\"dummy\"}]}");
        observed("Sing-box TUIC password not mapped to quicKey", tuic.size()==1 && tuic.get(0).quicKey.isEmpty());
        String raw = "{\"outbounds\":[{\"protocol\":\"socks\",\"settings\":{\"servers\":[{\"address\":\"example.invalid\",\"port\":1080}]}}]}";
        observed("Valid single Xray JSON baseline accepted", LinkParser.parseMany(raw).size()==1);
        observed("JSON config array not imported", LinkParser.parseMany("["+raw+"]").isEmpty());
        String b64=Base64.getEncoder().encodeToString(block.getBytes(StandardCharsets.UTF_8));
        observed("Base64-wrapped Clash not redispatched to YAML parser", LinkParser.parseMany(b64).isEmpty());
        List<Profile> fake=LinkParser.parseMany("{\"data_limit\":1000,\"used_traffic\":10,\"expire\":2000000000}");
        observed("Panel metadata JSON accepted as custom server despite no outbound", fake.size()==1 && fake.get(0).protocol.equals("custom") && fake.get(0).address.equals("custom"));
        observed("Custom JSON excluded from ProtocolSupport.isSupported", !ProtocolSupport.isSupported(LinkParser.parseMany(raw).get(0)));
        List<Profile> grpc=LinkParser.parseMany("proxies:\n  - name: demo\n    type: vless\n    server: example.invalid\n    port: 443\n    uuid: 11111111-1111-4111-8111-111111111111\n    network: grpc\n    grpc-opts:\n      grpc-service-name: demo-service\n");
        observed("Clash gRPC service name lost", grpc.size()==1 && grpc.get(0).serviceName.isEmpty());
        System.out.println("TOTAL: " + count + " observations confirmed. This is NOT an Android integration test.");
    }
}
