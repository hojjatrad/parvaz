package com.parvaz.tunnel.core;
import android.app.Application;
import android.content.*;
import android.os.Looper;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import com.parvaz.tunnel.MainActivity;
import com.parvaz.tunnel.store.ProfileStore;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class ClipboardHttpRegressionTest {
    Context context;ServerSocket server;Thread serverThread;ActivityController<MainActivity> controller;
    @Before public void setup()throws Exception{
        context=ApplicationProvider.getApplicationContext();ProfileStore.d=null;
        context.getSharedPreferences("parvaz_store",0).edit().clear().commit();
        context.getSharedPreferences("parvaz_prefs",0).edit().clear().commit();
        context.getSharedPreferences("parvaz_update",0).edit().putLong("last_check",System.currentTimeMillis()).commit();
        server=new ServerSocket();server.bind(new InetSocketAddress("127.0.0.1",0));
    }
    @After public void cleanup()throws Exception{if(controller!=null)controller.destroy();if(server!=null)server.close();if(serverThread!=null)serverThread.join(2000);ProfileStore.d=null;}
    private MainActivity paste(String body)throws Exception{return paste(body,null);}
    private MainActivity paste(String body,String urlOverride)throws Exception{
        serverThread=new Thread(()->{
            while(!server.isClosed())try(Socket client=server.accept()) {
                client.setSoTimeout(5000);
                java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));
                String line;int size=0;
                while((line=reader.readLine())!=null&&!line.isEmpty()){size+=line.length();if(size>8192)throw new java.io.IOException("header limit");}
                byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                client.getOutputStream().write(bytes);client.getOutputStream().flush();
            }catch(java.io.IOException ignored){if(server.isClosed())return;}
        },"test-loopback-http");serverThread.setDaemon(true);serverThread.start();
        controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("sub",urlOverride==null?"http://127.0.0.1:"+server.getLocalPort()+"/secret-token":urlOverride));
        activity.new F().onClick(null,1);
        long deadline=System.currentTimeMillis()+15000;
        while(activity.L.f343a.getString("last_import_report","").isEmpty()&&System.currentTimeMillis()<deadline){
            Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse("Import callback must finish",activity.L.f343a.getString("last_import_report","").isEmpty());return activity;
    }
    @Test public void actualClipboardThroughHttpToServerList()throws Exception{
        String link="vless://11111111-1111-4111-8111-111111111111@test.invalid:443?security=tls#demo";
        MainActivity activity=paste(org.json.JSONObject.quote(java.util.Base64.getEncoder().encodeToString(link.getBytes("UTF-8"))));
        assertEquals(1,activity.z.getItemCount());assertEquals(View.VISIBLE,activity.pageServers.getVisibility());
        assertTrue(activity.L.f343a.getString("last_import_report","").contains("recognized=1"));
    }
    @Test public void clipboardUsesOwnActiveProxyWithoutOriginDns()throws Exception {
        server.close();server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress("127.0.0.1",10809));
        TunnelVpnService.serviceRunning=true;
        try {
            MainActivity activity=paste("vless://11111111-1111-4111-8111-111111111111@test.invalid:443?security=tls#proxy-test","http://no-system-dns.invalid/secret-token");
            assertEquals(1,activity.z.getItemCount());
            assertTrue(activity.L.f343a.getString("last_import_report","").contains("recognized=1"));
        }finally{TunnelVpnService.serviceRunning=false;}
    }
    @Test public void zeroResultShowsPersistentSafeFailureReport()throws Exception{
        MainActivity activity=paste("not-a-config-private-body");String report=activity.L.f343a.getString("last_import_report","");
        assertEquals(0,activity.z.getItemCount());assertTrue(report.contains("failed=1"));assertTrue(report.contains("NO_VALID_CONFIGURATIONS"));
        assertFalse(report.contains("secret-token"));assertFalse(report.contains("127.0.0.1"));assertFalse(report.contains("private-body"));
        assertNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
        assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog().isShowing());
    }
}
