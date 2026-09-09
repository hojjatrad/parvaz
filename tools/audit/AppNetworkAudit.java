package com.parvaz.tunnel.core;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class AppNetworkAudit {
    static int count;
    static void check(String text,boolean result){if(!result)throw new AssertionError(text);count++;System.out.println("PASS NETWORK: "+text);}
    public static void main(String[] args)throws Exception {
        TunnelVpnService.serviceRunning=false;check("Inactive tunnel preserves system network",AppNetwork.capture().code.equals("SYSTEM"));
        TunnelVpnService.serviceRunning=true;check("Active own tunnel explicitly selects local proxy",AppNetwork.capture().code.equals("PARVAZ_PROXY"));TunnelVpnService.serviceRunning=false;
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            String[] first={null};Thread worker=new Thread(()->{try(Socket client=server.accept()) {
                client.setSoTimeout(5000);
                BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));first[0]=reader.readLine();
                String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
                client.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nlinks".getBytes(StandardCharsets.US_ASCII));
            }catch(IOException e){throw new RuntimeException(e);}});worker.start();
            AppNetwork.Route route=new AppNetwork.Route(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",server.getLocalPort())),"TEST_PROXY");
            SubscriptionHttpClient.Response response=SubscriptionHttpClient.fetch("http://no-local-dns.invalid/sub",route::open);
            worker.join(6000);
            check("HTTP request actually traverses selected proxy without local origin DNS",response.body.equals("links")&&first[0].contains("http://no-local-dns.invalid/sub"));
        }
        SubscriptionHttpClientTest.Fake timeout=new SubscriptionHttpClientTest.Fake("https://test.invalid/secret-token","");timeout.failure=new SocketTimeoutException("private-secret-token");
        try{SubscriptionHttpClient.fetch(timeout.getURL().toString(),u->timeout);throw new AssertionError();}
        catch(SubscriptionHttpClient.FetchException e){
            check("Timeout identifies request/header stage",e.stage.equals("CONNECT_OR_HEADERS"));
            check("Timeout reports only fixed route/stage/time fields",e.diagnostic().startsWith("STAGE_CONNECT_OR_HEADERS; ROUTE_TEST; SECONDS_")&&!e.toString().contains("secret-token"));
        }
        try(ServerSocket closed=new ServerSocket(0)) {
            int port=closed.getLocalPort();closed.close();
            AppNetwork.Route route=new AppNetwork.Route(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",port)),"TEST_PROXY");
            try{SubscriptionHttpClient.fetch("http://127.0.0.1:1/no-direct-fallback",route::open);throw new AssertionError();}
            catch(SubscriptionHttpClient.FetchException e){check("Unavailable proxy fails without direct retry",e.error==SubscriptionHttpClient.Error.NETWORK_FAILURE);}
        }
        System.out.println("NETWORK TOTAL: "+count+" assertions passed.");
    }
}
