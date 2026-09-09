package com.parvaz.tunnel.core;

import android.content.Context;
import java.net.*;
import java.util.*;
import java.security.SecureRandom;

/** Explicit private-interface HTTP proxy, not transparent tethering. Credentials are per session. */
public final class HotspotProxyManager {
    public static final int LAN_HTTP_PORT=10819;
    private static LanProxyBridge bridge;
    private static String password="",address="";
    private HotspotProxyManager(){}
    public static synchronized boolean isRunning(){return bridge!=null&&bridge.isRunning();}
    public static synchronized String password(){return password;}
    public static synchronized String boundAddress(){return address;}
    public static synchronized boolean start(Context context){
        if(isRunning())return true;stop();
        if(!TunnelVpnService.serviceRunning)return false;
        String selected=context.getSharedPreferences("parvaz_prefs",0).getString("lan_bind_address","");
        if(!addresses().contains(selected))return false;
        byte[] random=new byte[24];new SecureRandom().nextBytes(random);
        String secret=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        try{LanProxyBridge candidate=new LanProxyBridge(InetAddress.getByName(selected),LAN_HTTP_PORT,10809,"parvaz",secret);
            bridge=candidate;password=secret;address=selected;return true;
        }catch(Exception error){stop();return false;}
    }
    public static synchronized void stop(){if(bridge!=null)bridge.close();bridge=null;password="";address="";}
    public static List<String> addresses(){
        List<String> result=new ArrayList<>();
        try{for(NetworkInterface nif:Collections.list(NetworkInterface.getNetworkInterfaces())){
            String name=nif.getName().toLowerCase(Locale.ROOT);
            if(!nif.isUp()||nif.isLoopback()||name.startsWith("tun")||name.startsWith("ppp")||name.startsWith("rmnet")||name.startsWith("pdp")||name.startsWith("ccmni"))continue;
            for(InetAddress addr:Collections.list(nif.getInetAddresses()))if(addr instanceof Inet4Address&&addr.isSiteLocalAddress()&&!addr.isLoopbackAddress())result.add(addr.getHostAddress());
        }}catch(Exception ignored){}return result;
    }
    public static String getLocalIpAddress(){List<String> list=addresses();return list.isEmpty()?"":list.get(0);}
}
