package com.parvaz.tunnel.core;
import java.io.IOException;
import java.net.*;
import com.parvaz.tunnel.config.XrayConfigBuilder;

/** App traffic must explicitly enter its own proxy: the VPN excludes this UID to avoid loops.
 * With no Parvaz tunnel, retain Android's selected network/proxy (including another VPN).
 * Never retry a tunnel request directly, change TLS trust or bind the process to a network.
 */
public final class AppNetwork {
    private AppNetwork(){}
    public static final class Route {
        public final String code;
        private final Proxy proxy;
        Route(boolean running){this(running?new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",XrayConfigBuilder.HTTP_PORT)):null,running?"PARVAZ_PROXY":"SYSTEM");}
        Route(Proxy proxy,String code){this.proxy=proxy;this.code=code;}
        public HttpURLConnection open(URL url)throws IOException {
            return (HttpURLConnection)(proxy==null?url.openConnection():url.openConnection(proxy));
        }
    }
    public static Route capture(){return new Route(TunnelVpnService.serviceRunning);}
    public static HttpURLConnection open(URL url)throws IOException{return capture().open(url);}
}
