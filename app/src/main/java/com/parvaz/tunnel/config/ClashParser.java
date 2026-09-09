package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.events.*;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import static com.parvaz.tunnel.config.ConfigFields.*;

/** Safe, bounded YAML server extraction. This does NOT execute Clash routing/groups/providers. */
public final class ClashParser {
    private ClashParser() {}
    private static final Pattern MARKER = Pattern.compile("(?m)^[ \\t]*(?:---[ \\t]+)?[\"']?proxies[\"']?[ \\t]*:");
    public static boolean isClash(String text) {
        return text != null && (MARKER.matcher(text).find() || (text.trim().startsWith("{") && text.contains("proxies")));
    }
    public static ArrayList<Profile> parse(String yaml) { return parseDetailed(yaml).profiles; }

    public static ImportResult parseDetailed(String text) {
        ImportResult result=new ImportResult();
        if(text==null||text.trim().isEmpty()) return result;
        if(text.length()>LinkParser.MAX_INPUT_CHARS) throw new IllegalArgumentException("YAML input too large");
        try {
            LoaderOptions options=new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setAllowRecursiveKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(32);
            options.setCodePointLimit(LinkParser.MAX_INPUT_CHARS);
            DumperOptions dumper=new DumperOptions();
            // Keep plain scalars as strings: YAML 1.1 must NOT turn password 'yes' or
            // a leading-zero Reality short-id into a boolean/octal number.
            Resolver scalars=new Resolver(){ @Override protected void addImplicitResolvers() {} };
            Yaml yaml=new Yaml(new SafeConstructor(options),new Representer(dumper),dumper,options,scalars);
            int events=0, depth=0;
            for(Event event:yaml.parse(new StringReader(text))) {
                if(++events>100000) throw new Invalid("YAML_LIMIT");
                if(event instanceof AliasEvent) throw new Invalid("YAML_ALIAS_NOT_ALLOWED");
                if(event instanceof CollectionStartEvent && ++depth>32) throw new Invalid("YAML_DEPTH_LIMIT");
                if(event instanceof CollectionEndEvent) depth--;
            }
            Object loaded=yaml.load(text); // SafeConstructor rejects arbitrary Java tags.
            if(!(loaded instanceof Map)) throw new Invalid("INVALID_YAML_ROOT");
            Map<?,?> root=(Map<?,?>)loaded;
            Object proxies=root.get("proxies");
            if(!(proxies instanceof List)) throw new Invalid("MISSING_PROXY_LIST");
            if(root.containsKey("rules")||root.containsKey("proxy-groups")||root.containsKey("dns")||root.containsKey("proxy-providers")) result.warn("SERVER_EXTRACTION_ONLY",0);
            List<?> entries=(List<?>)proxies;
            if(entries.size()>LinkParser.MAX_PROFILES) throw new IllegalArgumentException("Too many subscription profiles");
            for(int i=0;i<entries.size();i++) {
                Object entry=entries.get(i);
                try {
                    if(!(entry instanceof Map)) throw new Invalid("INVALID_PROXY_NODE");
                    Profile profile=map(new JSONObject((Map<?,?>)entry));
                    result.add(profile);
                    if(profile.allowInsecure)result.warn("TLS_VERIFICATION_DISABLED",i+1);
                    if(!ProtocolNames.hasEngine(profile.protocol)) result.warn("CORE_UNSUPPORTED",i+1);
                } catch(Invalid e){result.reject(e.code,i+1);}
                  catch(JSONException e){result.reject("INVALID_PROXY_NODE",i+1);}
            }
        } catch(Invalid e){result.fail(e.code);}
          catch(org.yaml.snakeyaml.error.YAMLException e){result.fail("INVALID_OR_UNSAFE_YAML");}
        return result;
    }

    private static Profile map(JSONObject o) throws JSONException {
        String protocol=ProtocolNames.canonical(required(o,"type"));
        String allowed="name type server port udp";
        if("wireguard".equals(protocol)) allowed+=" private-key public-key pre-shared-key preshared-key ip ipv6 reserved mtu";
        else {
            allowed+=" tls servername sni skip-cert-verify alpn";
            if(!"hysteria2".equals(protocol)&&!"tuic".equals(protocol)) allowed+=" network client-fingerprint fingerprint fp ws-opts grpc-opts h2-opts http-upgrade-opts";
            switch(protocol) {
                case "vless":allowed+=" uuid encryption flow reality-opts";break;
                case "vmess":allowed+=" uuid cipher encryption alterId alterid";break;
                case "trojan":allowed+=" password flow reality-opts";break;
                case "shadowsocks":allowed+=" password cipher encryption";break;
                case "socks":allowed+=" username password version";break;
                case "http":allowed+=" username password";break;
                case "hysteria2":allowed+=" password obfs obfs-password";break;
                case "tuic":allowed+=" uuid password congestion-controller udp-relay-mode";break;
                default:throw new Invalid("UNSUPPORTED_PROTOCOL");
            }
        }
        keys(o,allowed);
        Profile p=LinkParser.newProfile();
        p.protocol=protocol;
        p.address=required(o,"server").trim();
        p.port=integer(o,"port",443,1,65535);
        p.remark=text(o,"name",p.address);
        p.uuid=text(o,"uuid",text(o,"password",""));
        p.encryption=text(o,"cipher",text(o,"encryption","none"));
        p.alterId=integer(o,o.has("alterId")?"alterId":"alterid",0,0,65535);
        p.network=text(o,"network","tcp").toLowerCase(Locale.US);
        p.security=bool(o,"tls","trojan".equals(p.protocol)||"hysteria2".equals(p.protocol)||"tuic".equals(p.protocol))?"tls":"";
        p.sni=text(o,"servername",text(o,"sni",p.address));
        p.path="/";p.host=p.sni;
        p.allowInsecure=bool(o,"skip-cert-verify",false);
        p.fingerprint=text(o,"client-fingerprint",text(o,"fp","chrome"));
        if(o.has("fingerprint")) throw new Invalid("CERTIFICATE_PIN_NOT_MAPPED");
        p.alpn=list(o,"alpn","");
        p.flow=text(o,"flow","");
        if(o.has("udp")&&!bool(o,"udp",true)) throw new Invalid("UDP_DISABLE_NOT_MAPPED");
        if(o.has("reality-opts")) {
            if(!bool(o,"tls",true)) throw new Invalid("REALITY_TLS_MISMATCH");
            JSONObject r=object(o,"reality-opts");keys(r,"public-key short-id");
            p.security="reality";p.publicKey=required(r,"public-key");p.shortId=text(r,"short-id","");
        }
        for(String key:new String[]{"ws-opts","grpc-opts","h2-opts","http-upgrade-opts"}) {
            String wanted=key.equals("ws-opts")?"ws":key.equals("grpc-opts")?"grpc":key.equals("h2-opts")?"h2":"httpupgrade";
            if(o.has(key)) {
                if(!p.network.equals(wanted)) throw new Invalid("TRANSPORT_OPTIONS_MISMATCH");
                JSONObject t=object(o,key);
                if("grpc".equals(wanted)){keys(t,"grpc-service-name");p.serviceName=text(t,"grpc-service-name","");}
                else if("h2".equals(wanted)){keys(t,"host path");p.host=list(t,"host","");p.path=text(t,"path","/");}
                else{keys(t,"path headers host");p.path=text(t,"path","/");p.host=text(t,"host",hostHeader(object(t,"headers")));}
            }
        }
        if("socks".equals(p.protocol)||"http".equals(p.protocol)) {
            p.uuid=text(o,"username","");p.quicKey=text(o,"password","");
            if(o.has("version")&&!text(o,"version","5").equals("5")) throw new Invalid("SOCKS_VERSION_UNSUPPORTED");
        } else if("wireguard".equals(p.protocol)) {
            p.uuid=required(o,"private-key");p.publicKey=required(o,"public-key");
            p.presharedKey=text(o,"pre-shared-key",text(o,"preshared-key",""));
            String v4=cidr(text(o,"ip",""),false),v6=cidr(text(o,"ipv6",""),true);
            p.localAddress=v4.isEmpty()?v6:v6.isEmpty()?v4:v4+","+v6;
            p.reserved=list(o,"reserved","");p.wgMtu=integer(o,"mtu",1420,576,9000);
            p.security="";p.network="";
        } else if("hysteria2".equals(p.protocol)) {
            p.network="udp";p.mode=text(o,"obfs","");p.host=text(o,"obfs-password","");
        } else if("tuic".equals(p.protocol)) {
            p.network="udp";p.quicKey=required(o,"password");p.mode=text(o,"congestion-controller","bbr");p.headerType=text(o,"udp-relay-mode","native");
        }
        transport(p);credentials(p);
        return p.normalize();
    }
}
