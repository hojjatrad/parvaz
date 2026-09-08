package com.parvaz.tunnel.config;

import com.parvaz.tunnel.model.Profile;
import org.json.*;
import java.util.*;
import static com.parvaz.tunnel.config.ConfigFields.*;

/** Bounded, explicitly limited Sing-box server extraction; not a full Sing-box runtime. */
public final class SingBoxParser {
    private SingBoxParser() {}
    public static boolean isSingBox(String text) {
        if(text==null||!text.trim().startsWith("{")) return false;
        if(text.length()>LinkParser.MAX_INPUT_CHARS) throw new IllegalArgumentException("JSON input too large");
        LinkParser.checkJsonDepth(text);
        try {
            JSONObject root=JsonInput.object(text);
            for(String section:new String[]{"outbounds","endpoints"}) {
                JSONArray a=root.optJSONArray(section);
                if(a!=null) for(int i=0;i<a.length();i++) {
                    JSONObject o=a.optJSONObject(i);
                    if(o!=null&&o.has("type")&&!o.has("protocol")) return true;
                }
            }
        } catch(JSONException ignored) { }
        return false;
    }
    public static ArrayList<Profile> parse(String json){return parseDetailed(json).profiles;}
    public static ImportResult parseDetailed(String json) {
        ImportResult result=new ImportResult();
        if(json==null||json.trim().isEmpty())return result;
        if(json.length()>LinkParser.MAX_INPUT_CHARS)throw new IllegalArgumentException("JSON input too large");
        LinkParser.checkJsonDepth(json);
        try {
            JSONObject root=JsonInput.object(json);
            if(root.has("route")||root.has("dns")||root.has("inbounds"))result.warn("SERVER_EXTRACTION_ONLY",0);
            int position=0;
            for(String section:new String[]{"outbounds","endpoints"}) {
                if(!root.has(section))continue;
                JSONArray nodes=root.optJSONArray(section);
                if(nodes==null){result.fail("INVALID_OUTBOUND_LIST");continue;}
                if(nodes.length()>LinkParser.MAX_PROFILES)throw new IllegalArgumentException("Too many subscription profiles");
                for(int i=0;i<nodes.length();i++) {
                    position++;
                    try {
                        JSONObject o=nodes.optJSONObject(i);
                        if(o==null)throw new Invalid("INVALID_PROXY_NODE");
                        String type=text(o,"type","");
                        if(Arrays.asList("direct","block","dns","selector","urltest").contains(type)){result.warn("HELPER_NOT_IMPORTED",position);continue;}
                        Profile p=map(o,"endpoints".equals(section));result.add(p);
                        if(p.allowInsecure)result.warn("TLS_VERIFICATION_DISABLED",position);
                        if(!ProtocolNames.hasBuilder(p.protocol))result.warn("CORE_UNSUPPORTED",position);
                    }catch(Invalid e){result.reject(e.code,position);}
                }
            }
        }catch(JSONException e){result.fail("INVALID_JSON");}
        return result;
    }

    private static Profile map(JSONObject o,boolean endpoint) {
        String type=ProtocolNames.canonical(required(o,"type"));
        String allowed="type tag server server_port port";
        if("wireguard".equals(type)) allowed=endpoint?"type tag private_key address peers mtu system":"type tag server server_port port private_key local_address peer_public_key pre_shared_key reserved mtu";
        else {
            allowed+=" tls transport";
            switch(type) {
                case "vless":allowed+=" uuid flow encryption";break;
                case "vmess":allowed+=" uuid security alter_id";break;
                case "trojan":allowed+=" password";break;
                case "shadowsocks":allowed+=" password method";break;
                case "socks":allowed+=" username password version";break;
                case "http":allowed+=" username password";break;
                case "hysteria2":allowed+=" password obfs";break;
                case "tuic":allowed+=" uuid password congestion_control udp_relay_mode";break;
                default:throw new Invalid("UNSUPPORTED_PROTOCOL");
            }
        }
        keys(o,allowed);
        if(endpoint&&!"wireguard".equals(type))throw new Invalid("UNSUPPORTED_ENDPOINT");
        Profile p=LinkParser.newProfile();p.protocol=type;
        if("wireguard".equals(type)) {
            p.uuid=required(o,"private_key");p.wgMtu=integer(o,"mtu",1420,576,9000);p.network="";
            JSONObject peer=o;
            if(endpoint) {
                if(bool(o,"system",false))throw new Invalid("SYSTEM_ENDPOINT_NOT_MAPPED");
                JSONArray peers=o.optJSONArray("peers");
                if(peers==null||peers.length()!=1||peers.optJSONObject(0)==null)throw new Invalid("WIREGUARD_REQUIRES_ONE_PEER");
                peer=peers.optJSONObject(0);keys(peer,"address port public_key pre_shared_key reserved");
                p.address=required(peer,"address");p.port=integer(peer,"port",51820,1,65535);
                p.publicKey=required(peer,"public_key");p.localAddress=list(o,"address","");
            }else {
                p.address=required(o,"server");p.port=integer(o,o.has("server_port")?"server_port":"port",51820,1,65535);
                p.publicKey=required(o,"peer_public_key");p.localAddress=list(o,"local_address","");
            }
            p.presharedKey=text(peer,"pre_shared_key","");p.reserved=list(peer,"reserved","");
        }else {
            p.address=required(o,"server").trim();p.port=integer(o,o.has("server_port")?"server_port":"port",443,1,65535);
            p.uuid=text(o,"uuid",text(o,"password",""));
            p.encryption=text(o,"method",text(o,"security",text(o,"encryption","vmess".equals(type)?"auto":"none")));
            p.alterId=integer(o,"alter_id",0,0,65535);p.flow=text(o,"flow","");
            if("http".equals(type)||"socks".equals(type)) {
                p.uuid=text(o,"username","");p.quicKey=text(o,"password","");
                if(o.has("version")&&!text(o,"version","5").equals("5"))throw new Invalid("SOCKS_VERSION_UNSUPPORTED");
            }
            JSONObject tls=object(o,"tls");keys(tls,"enabled server_name insecure alpn utls reality");
            if(bool(tls,"enabled",false)) {
                p.security="tls";p.sni=text(tls,"server_name",p.address);p.allowInsecure=bool(tls,"insecure",false);p.alpn=list(tls,"alpn","");
                JSONObject utls=object(tls,"utls");keys(utls,"enabled fingerprint");
                if(bool(utls,"enabled",false))p.fingerprint=text(utls,"fingerprint","chrome");
                JSONObject reality=object(tls,"reality");keys(reality,"enabled public_key short_id");
                if(bool(reality,"enabled",false)){p.security="reality";p.publicKey=required(reality,"public_key");p.shortId=text(reality,"short_id","");}
            }
            if(("hysteria2".equals(type)||"tuic".equals(type))&&o.has("transport"))throw new Invalid("UNSUPPORTED_TRANSPORT");
            JSONObject t=object(o,"transport");
            if(t.length()>0) {
                p.network=text(t,"type","tcp");
                if("grpc".equals(p.network)){keys(t,"type service_name");p.serviceName=text(t,"service_name","");}
                else if("http".equals(p.network)){keys(t,"type path host");p.network="h2";p.path=text(t,"path","/");p.host=list(t,"host","");}
                else if("ws".equals(p.network)){keys(t,"type path headers");p.path=text(t,"path","/");p.host=hostHeader(object(t,"headers"));}
                else if("httpupgrade".equals(p.network)){keys(t,"type path host headers");p.path=text(t,"path","/");p.host=text(t,"host",hostHeader(object(t,"headers")));}
                else throw new Invalid("UNSUPPORTED_TRANSPORT");
            }
            if("tuic".equals(type)){p.network="udp";p.quicKey=required(o,"password");p.mode=text(o,"congestion_control","bbr");p.headerType=text(o,"udp_relay_mode","native");}
            if("hysteria2".equals(type)){p.network="udp";JSONObject obfs=object(o,"obfs");keys(obfs,"type password");p.mode=text(obfs,"type","");p.host=text(obfs,"password","");}
        }
        p.remark=text(o,"tag",p.address);transport(p);credentials(p);return p.normalize();
    }
}
