package com.parvaz.tunnel.core;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.SSLException;

/** Bounded subscription transport. Never overrides TLS trust or hostname checks.
 * Explicit HTTP input is retained for legacy panels (it is NOT confidential).
 * Once HTTPS is used, downgrading to HTTP is forbidden.
 */
public final class SubscriptionHttpClient {
    public static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    public static final int MAX_REDIRECTS = 5;
    private static final long BUDGET_NANOS = 60_000_000_000L;

    public enum Error {
        INVALID_URL, INSECURE_REDIRECT, REDIRECT_LIMIT, REDIRECT_LOOP,
        HTTP_STATUS, TOO_LARGE, EMPTY_RESPONSE, HTML_RESPONSE, UNSUPPORTED_ENCODING,
        TLS_FAILURE, TIMEOUT, NETWORK_FAILURE
    }

    /** Messages contain neither the secret URL nor remote exception text. */
    public static final class FetchException extends IOException {
        public final Error error;
        public final int httpStatus;
        public String stage="UNKNOWN",route="UNKNOWN";
        public int elapsedSeconds;
        public String diagnostic(){return "STAGE_"+stage+"; ROUTE_"+route+"; SECONDS_"+elapsedSeconds;}
        FetchException(Error error) { this(error, 0); }
        FetchException(Error error, int status) {
            super("Subscription: " + error.name() + (status == 0 ? "" : " (HTTP " + status + ")"));
            this.error = error;
            this.httpStatus = status;
        }
    }

    public static final class Response {
        public final String body;
        public final String userinfo;
        Response(String body, String userinfo) { this.body = body; this.userinfo = userinfo; }
    }

    // Package-private seam: tests can supply fake transports without trusting remote hosts.
    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }

    private SubscriptionHttpClient() {}

    public static Response fetch(String input) throws IOException {
        return fetch(input,0);
    }

    static Response fetch(String input, ConnectionFactory factory) throws IOException {
        return fetch(input,factory,0);
    }
    public static Response fetch(String input,int format) throws IOException {
        AppNetwork.Route route=AppNetwork.capture();
        return fetch(input,route::open,format,route.code);
    }
    static Response fetch(String input,ConnectionFactory factory,int format) throws IOException {
        return fetch(input,factory,format,"TEST");
    }
    private static final class Trace {String stage="OPEN";final long start=System.nanoTime();}
    static Response fetch(String input,ConnectionFactory factory,int format,String route)throws IOException {
        Trace trace=new Trace();
        try {return fetchInternal(input,factory,format,trace);}
        catch(IOException|IllegalArgumentException e) {
            FetchException failure;
            if(e instanceof FetchException)failure=(FetchException)e;
            else if(e instanceof SSLException)failure=new FetchException(Error.TLS_FAILURE);
            else if(e instanceof SocketTimeoutException)failure=new FetchException(Error.TIMEOUT);
            else failure=new FetchException(Error.NETWORK_FAILURE);
            failure.stage=trace.stage;failure.route=route;
            failure.elapsedSeconds=(int)((System.nanoTime()-trace.start)/1_000_000_000L);
            throw failure;
        }
    }

    private static Response fetchInternal(String input, ConnectionFactory factory,int format,Trace trace) throws IOException {
        URL current = checkedUrl(input);
        long deadline = System.nanoTime() + BUDGET_NANOS;
        Set<String> visited = new HashSet<>();
        String userinfo = null;
        for (int hop = 0; ; hop++) {
            if (!visited.add(current.toExternalForm())) throw new FetchException(Error.REDIRECT_LOOP);
            trace.stage="OPEN";
            HttpURLConnection conn = factory.open(current);
            try {
                conn.setConnectTimeout(remaining(deadline, 15000));
                conn.setReadTimeout(remaining(deadline, 45000));
                conn.setInstanceFollowRedirects(false);
                conn.setUseCaches(false);
                // Same URL and token; never guess new endpoint paths or weaken TLS.
                String[] agents={"v2rayNG/1.10.11","Clash.Meta/1.19.0","sing-box/1.12.0"};
                String[] accepts={"text/plain,application/json,application/yaml,text/yaml,*/*;q=0.5","application/yaml,text/yaml,text/plain,*/*;q=0.5","application/json,text/plain,*/*;q=0.5"};
                if(format<0||format>=agents.length)throw new FetchException(Error.INVALID_URL);
                conn.setRequestProperty("User-Agent",agents[format]);
                conn.setRequestProperty("Accept",accepts[format]);
                conn.setRequestProperty("Accept-Encoding", "gzip");
                trace.stage="CONNECT_OR_HEADERS";
                int status = conn.getResponseCode();
                remaining(deadline, 45000);
                if (isRedirect(status)) {
                    if (hop >= MAX_REDIRECTS) throw new FetchException(Error.REDIRECT_LIMIT);
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) throw new FetchException(Error.INVALID_URL);
                    URL next;
                    try { next = checkedUrl(new URL(current, location.trim()).toExternalForm()); }
                    catch (IOException e) { throw new FetchException(Error.INVALID_URL); }
                    if ("https".equalsIgnoreCase(current.getProtocol()) && !"https".equalsIgnoreCase(next.getProtocol())) {
                        throw new FetchException(Error.INSECURE_REDIRECT);
                    }
                    // Never carry quota headers across origins (or from HTTP into HTTPS).
                    userinfo = sameOrigin(current, next) ? headerUserinfo(conn, userinfo) : null;
                    current = next;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new FetchException(Error.HTTP_STATUS, status);
                userinfo = headerUserinfo(conn, userinfo);
                if (conn.getContentLengthLong() > MAX_BODY_BYTES) throw new FetchException(Error.TOO_LARGE);
                String encoding = conn.getContentEncoding();
                if (encoding != null && !encoding.isEmpty() && !"identity".equalsIgnoreCase(encoding)
                        && !"gzip".equalsIgnoreCase(encoding)) throw new FetchException(Error.UNSUPPORTED_ENCODING);
                trace.stage="BODY";
                String body;
                try (InputStream raw = new LimitedStream(conn.getInputStream());
                     InputStream decoded = "gzip".equalsIgnoreCase(encoding) ? new GZIPInputStream(raw) : raw;
                     ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[16384];
                    int count;
                    while (true) {
                        conn.setReadTimeout(remaining(deadline, 45000));
                        count = decoded.read(buf);
                        if (count < 0) break;
                        if (bytes.size() > MAX_BODY_BYTES - count) throw new FetchException(Error.TOO_LARGE);
                        bytes.write(buf, 0, count);
                    }
                    remaining(deadline, 45000);
                    body = new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
                }
                if (body.startsWith("\uFEFF")) body = body.substring(1).trim();
                if (body.isEmpty()) throw new FetchException(Error.EMPTY_RESPONSE);
                String prefix = body.substring(0, Math.min(1024, body.length())).toLowerCase(Locale.US);
                // Some panels wrongly label plain links text/html: inspect the body, not just MIME.
                if (prefix.startsWith("<") && (prefix.contains("<html") || prefix.contains("<!doctype html")
                        || prefix.contains("<head") || prefix.contains("<body"))) {
                    throw new FetchException(Error.HTML_RESPONSE);
                }
                return new Response(body, userinfo);
            } finally {
                conn.disconnect();
            }
        }
    }

    private static int remaining(long deadline, int maximum) throws FetchException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0 || Thread.currentThread().isInterrupted()) throw new FetchException(Error.TIMEOUT);
        return (int) Math.min(maximum, Math.max(1, nanos / 1_000_000L));
    }

    private static URL checkedUrl(String input) throws FetchException {
        try {
            if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException();
            URL url = new URL(input.trim());
            String scheme = url.getProtocol();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || url.getHost().isEmpty() || url.getUserInfo() != null
                    || url.getPort() == 0 || url.getPort() > 65535 || url.getPort() < -1) throw new IllegalArgumentException();
            // Validate whitespace/control characters and discard fragments (not sent over HTTP).
            url.toURI();
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile());
        } catch (Exception e) {
            throw new FetchException(Error.INVALID_URL);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean sameOrigin(URL a, URL b) {
        return a.getProtocol().equalsIgnoreCase(b.getProtocol()) && a.getHost().equalsIgnoreCase(b.getHost())
                && (a.getPort() < 0 ? a.getDefaultPort() : a.getPort()) == (b.getPort() < 0 ? b.getDefaultPort() : b.getPort());
    }

    private static String headerUserinfo(HttpURLConnection conn, String fallback) {
        for (String name : new String[]{"subscription-userinfo", "x-userinfo", "user-info", "subscription-info"}) {
            String value = conn.getHeaderField(name);
            if (value != null && value.length() <= 4096) return value;
        }
        return fallback;
    }

    /** Bound both compressed input and expanded output. */
    private static final class LimitedStream extends FilterInputStream {
        private int count;
        LimitedStream(InputStream in) { super(in); }
        private void consumed(int n) throws FetchException {
            if (n > 0 && (count += n) > MAX_BODY_BYTES) throw new FetchException(Error.TOO_LARGE);
        }
        @Override public int read() throws IOException {
            int result = in.read();
            if (result >= 0) consumed(1);
            return result;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int result = in.read(b, off, Math.min(len, MAX_BODY_BYTES - count + 1));
            consumed(result);
            return result;
        }
    }
}
