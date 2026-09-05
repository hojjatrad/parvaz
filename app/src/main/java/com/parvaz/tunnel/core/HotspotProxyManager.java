package com.parvaz.tunnel.core;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shares the VPN tunnel across local Wi-Fi / Hotspot by bridging LAN clients to the local proxy.
 */
public final class HotspotProxyManager {

    private static final String TAG = "ParvazHotspot";
    public static final int LAN_HTTP_PORT = 10809;
    public static final int LAN_SOCKS_PORT = 10808;

    private static volatile boolean running = false;
    private static ServerSocket httpBridgeSocket;
    private static ExecutorService pool;

    private HotspotProxyManager() {
    }

    public static boolean isRunning() {
        return running;
    }

    /** Starts the LAN proxy bridge. */
    public static synchronized void start(final Context context) {
        if (running) return;
        running = true;
        pool = Executors.newCachedThreadPool();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpBridgeSocket = new ServerSocket(LAN_HTTP_PORT);
                    Log.i(TAG, "LAN Proxy listening on 0.0.0.0:" + LAN_HTTP_PORT);
                    while (running && !httpBridgeSocket.isClosed()) {
                        final Socket client = httpBridgeSocket.accept();
                        pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                forward(client, "127.0.0.1", 10809);
                            }
                        });
                    }
                } catch (Throwable t) {
                    if (running) {
                        Log.w(TAG, "LAN bridge exception", t);
                    }
                }
            }
        }, "lan-proxy-bridge").start();
    }

    /** Stops the LAN proxy bridge. */
    public static synchronized void stop() {
        running = false;
        if (httpBridgeSocket != null) {
            try {
                httpBridgeSocket.close();
            } catch (Throwable ignored) {
            }
            httpBridgeSocket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    private static void forward(Socket client, String targetHost, int targetPort) {
        Socket target = null;
        try {
            target = new Socket(targetHost, targetPort);
            client.setSoTimeout(120000);
            target.setSoTimeout(120000);

            final Socket fClient = client;
            final Socket fTarget = target;

            Thread t1 = new Thread(new Runnable() {
                @Override
                public void run() {
                    pipe(fClient, fTarget);
                }
            });
            Thread t2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    pipe(fTarget, fClient);
                }
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(client);
            closeQuietly(target);
        }
    }

    private static void pipe(Socket src, Socket dst) {
        try {
            InputStream in = src.getInputStream();
            OutputStream out = dst.getOutputStream();
            byte[] buf = new byte[16384];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
                out.flush();
            }
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(src);
            closeQuietly(dst);
        }
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Finds the local IPv4 address (e.g. Hotspot 192.168.43.1 or Wi-Fi IP). */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (nif.isLoopback() || !nif.isUp()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "192.168.43.1";
    }
}
