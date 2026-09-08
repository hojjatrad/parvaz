package com.parvaz.tunnel.core;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.*;
import static com.parvaz.tunnel.core.SubscriptionHttpClient.*;
import com.parvaz.tunnel.core.SubscriptionHttpClient.Error;

/** Fake HTTP policy tests + real loopback TLS handshake tests, with test-only certificate trust.
 * No user endpoint is contacted. Never changes the global hostname verifier or trust manager. */
public class SubscriptionHttpClientTest {
    static int count;
    interface Task { void run() throws Exception; }
    static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        count++;
        System.out.println("PASS: " + name);
    }
    static void rejects(String name, Error error, Task task) throws Exception {
        try { task.run(); }
        catch (FetchException e) {
            check(name, e.error == error);
            check(name + " redacts secrets", !e.toString().contains("secret-token") && e.getCause() == null);
            return;
        }
        throw new AssertionError(name + " did not reject");
    }
    static class Fake extends HttpURLConnection {
        Map<String,String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        byte[] body;
        int status = 200;
        boolean closed, disconnected;
        long length = -1;
        IOException failure;
        Fake(String url, String body) throws Exception {
            super(new URL(url));
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }
        Fake header(String name, String value) { headers.put(name,value); return this; }
        Fake redirect(String location) { status=302; return header("Location",location); }
        @Override public void connect() {}
        @Override public void disconnect() { disconnected=true; }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() throws IOException { if(failure!=null) throw failure; return status; }
        @Override public String getHeaderField(String name) { return headers.get(name); }
        @Override public long getContentLengthLong() { return length; }
        @Override public String getContentEncoding() { return headers.get("Content-Encoding"); }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(body) { @Override public void close() throws IOException { closed=true; super.close(); } };
        }
    }
    static ConnectionFactory sequence(Fake... replies) {
        ArrayDeque<Fake> queue = new ArrayDeque<>(Arrays.asList(replies));
        return url -> {
            Fake next=queue.remove();
            if(!url.toExternalForm().equals(next.getURL().toExternalForm())) throw new AssertionError("Unexpected URL resolution");
            return next;
        };
    }
    static byte[] gzip(byte[] input) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(GZIPOutputStream gzip=new GZIPOutputStream(bytes)) { gzip.write(input); }
        return bytes.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        String url="https://example.invalid/sub/secret-token";
        Fake ok=new Fake(url,"\uFEFF  vmess://dummy\n").header("Subscription-Userinfo","upload=1; download=2; total=3");
        Response response=fetch(url,sequence(ok));
        check("Plain body/BOM decoded",response.body.equals("vmess://dummy"));
        check("Quota header preserved",response.userinfo.contains("total=3"));
        check("Resources disconnected and stream closed",ok.disconnected&&ok.closed);
        check("Redirects disabled in underlying client",!ok.getInstanceFollowRedirects());
        check("Connection/read timeout and no URL caching",ok.getConnectTimeout()>0&&ok.getReadTimeout()>0&&!ok.getUseCaches());
        Fake r1=new Fake(url,"").redirect("../next").header("subscription-userinfo","total=99");
        Fake r2=new Fake("https://example.invalid/next","links");
        check("Relative redirect preserves same-origin quota",fetch(url,sequence(r1,r2)).userinfo.equals("total=99"));
        check("Redirect connection disconnected",r1.disconnected);
        Fake cross=new Fake(url,"").redirect("https://other.invalid/next").header("subscription-userinfo","total=99");
        check("No quota inheritance across origins",fetch(url,sequence(cross,new Fake("https://other.invalid/next","links"))).userinfo==null);
        rejects("HTTPS downgrade",Error.INSECURE_REDIRECT,()->fetch(url,sequence(new Fake(url,"").redirect("http://example.invalid/next"))));
        rejects("Redirect loop",Error.REDIRECT_LOOP,()->fetch(url,sequence(new Fake(url,"").redirect(url))));
        rejects("Missing redirect location",Error.INVALID_URL,()->{Fake f=new Fake(url,"");f.status=302;fetch(url,sequence(f));});
        rejects("File URL",Error.INVALID_URL,()->fetch("file:///secret-token",u->{throw new AssertionError();}));
        rejects("URL user-info",Error.INVALID_URL,()->fetch("https://user:secret-token@example.invalid/",u->{throw new AssertionError();}));
        rejects("Whitespace URL",Error.INVALID_URL,()->fetch("https://example.invalid/secret-token bad",u->{throw new AssertionError();}));
        Fake[] redirects=new Fake[7];
        for(int i=0;i<6;i++) redirects[i]=new Fake("https://example.invalid/"+i,"").redirect("/"+(i+1));
        redirects[6]=new Fake("https://example.invalid/6","links");
        rejects("Redirect hop limit",Error.REDIRECT_LIMIT,()->fetch("https://example.invalid/0",sequence(redirects)));
        Fake[] allowed=new Fake[6];
        for(int i=0;i<5;i++) allowed[i]=new Fake("https://example.invalid/"+i,"").redirect("/"+(i+1));
        allowed[5]=new Fake("https://example.invalid/5","links");
        check("Exactly five redirects allowed",fetch("https://example.invalid/0",sequence(allowed)).body.equals("links"));
        for(int status : new int[]{204,206,304,401,403,429,500}) {
            rejects("HTTP "+status,Error.HTTP_STATUS,()->{Fake f=new Fake(url,"secret-token");f.status=status;fetch(url,sequence(f));});
        }
        rejects("HTML login page",Error.HTML_RESPONSE,()->fetch(url,sequence(new Fake(url,"<!doctype html><html>login</html>"))));
        check("Wrong MIME with actual plain links remains compatible",fetch(url,sequence(new Fake(url,"vmess://dummy").header("Content-Type","text/html"))).body.equals("vmess://dummy"));
        rejects("Empty body",Error.EMPTY_RESPONSE,()->fetch(url,sequence(new Fake(url," \n"))));
        rejects("Advertised oversized body",Error.TOO_LARGE,()->{Fake f=new Fake(url,"x");f.length=MAX_BODY_BYTES+1;fetch(url,sequence(f));});
        rejects("Unadvertised oversized body",Error.TOO_LARGE,()->{Fake f=new Fake(url,"");f.body=new byte[MAX_BODY_BYTES+1];fetch(url,sequence(f));});
        Fake gz=new Fake(url,"").header("Content-Encoding","gzip");gz.body=gzip("links".getBytes(StandardCharsets.UTF_8));
        check("Gzip decoded",fetch(url,sequence(gz)).body.equals("links"));
        Fake bomb=new Fake(url,"").header("Content-Encoding","gzip");bomb.body=gzip(new byte[MAX_BODY_BYTES+1]);
        rejects("Gzip expanded-size limit",Error.TOO_LARGE,()->fetch(url,sequence(bomb)));
        check("Gzip failure cleans resources",bomb.disconnected&&bomb.closed);
        rejects("Unknown content encoding",Error.UNSUPPORTED_ENCODING,()->fetch(url,sequence(new Fake(url,"x").header("Content-Encoding","br"))));
        Fake timeout=new Fake(url,"");timeout.failure=new SocketTimeoutException("secret-token");
        rejects("Timeout sanitized",Error.TIMEOUT,()->fetch(url,sequence(timeout)));
        check("Timeout disconnects",timeout.disconnected);
        Fake network=new Fake(url,"");network.failure=new IOException("secret-token");
        rejects("Network exception sanitized",Error.NETWORK_FAILURE,()->fetch(url,sequence(network)));
        check("Explicit legacy HTTP still allowed (not encrypted)",fetch("http://example.invalid/sub",sequence(new Fake("http://example.invalid/sub","links"))).body.equals("links"));
        // Real TLS tests use the keytool-generated localhost-only certificate supplied by run.sh.
        KeyStore keys=KeyStore.getInstance("PKCS12");
        try(InputStream in=Files.newInputStream(Paths.get(args[0]))) { keys.load(in,"audit-only".toCharArray()); }
        KeyManagerFactory kmf=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keys,"audit-only".toCharArray());
        SSLContext serverContext=SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(),null,null);
        HttpsServer server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/",exchange->{byte[] data="links".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,data.length);try(OutputStream out=exchange.getResponseBody()){out.write(data);}exchange.close();});
        server.start();
        try {
            int port=server.getAddress().getPort();
            rejects("Real untrusted HTTPS certificate",Error.TLS_FAILURE,()->fetch("https://localhost:"+port+"/secret-token"));
            KeyStore trust=KeyStore.getInstance("PKCS12");trust.load(null,null);trust.setCertificateEntry("localhost",keys.getCertificate("localhost"));
            TrustManagerFactory tmf=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tmf.init(trust);
            SSLContext trusted=SSLContext.getInstance("TLS");trusted.init(null,tmf.getTrustManagers(),null);
            ConnectionFactory onlyThisTest=url2->{HttpsURLConnection c=(HttpsURLConnection)url2.openConnection();c.setSSLSocketFactory(trusted.getSocketFactory());return c;};
            check("Real trusted localhost TLS succeeds",fetch("https://localhost:"+port+"/",onlyThisTest).body.equals("links"));
            rejects("Real hostname mismatch with trusted cert",Error.TLS_FAILURE,()->fetch("https://127.0.0.1:"+port+"/secret-token",onlyThisTest));
        } finally { server.stop(0); }
        System.out.println("HTTP/TLS TOTAL: "+count+" assertions passed. TLS tests are desktop JVM, not Android trust-store tests.");
    }
}
