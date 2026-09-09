package com.parvaz.tunnel.core;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated, bounded HTTP proxy bridge. No listener on all interfaces, no SOCKS exposure. */
public final class LanProxyBridge implements AutoCloseable {
    private final ServerSocket listener;
    private final int upstreamPort;
    private final byte[] expected;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final Semaphore slots=new Semaphore(16);
    private final ExecutorService workers=Executors.newFixedThreadPool(16),reverse=Executors.newFixedThreadPool(16);
    private final AtomicBoolean running=new AtomicBoolean(true);
    public LanProxyBridge(InetAddress bind,int port,int targetPort,String username,String password)throws IOException {
        if(bind.isAnyLocalAddress()||!(bind.isSiteLocalAddress()||bind.isLoopbackAddress())||password.length()<16||port==targetPort&&port!=0)throw new IllegalArgumentException("Unsafe LAN listener");
        upstreamPort=targetPort;expected=(username+":"+password).getBytes(StandardCharsets.US_ASCII);
        listener=new ServerSocket();try{listener.setReuseAddress(false);listener.bind(new InetSocketAddress(bind,port),16);}catch(IOException e){listener.close();workers.shutdownNow();reverse.shutdownNow();throw e;}
        Thread accept=new Thread(this::accept,"parvaz-lan-accept");accept.setDaemon(true);accept.start();
    }
    public int port(){return listener.getLocalPort();}
    public boolean isRunning(){return running.get()&&!listener.isClosed();}
    private void accept(){
        try{while(running.get()){
            Socket client=listener.accept();
            if(!slots.tryAcquire()){client.close();continue;}
            sockets.add(client);
            try{workers.execute(()->serve(client));}catch(RejectedExecutionException e){sockets.remove(client);client.close();slots.release();}
        }}catch(IOException ignored){}finally{close();}
    }
    private void serve(Socket client){Socket target=null;Future<?> reply=null;
        try{
            client.setSoTimeout(5000);InputStream input=client.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int matched=0;
            byte[] end={13,10,13,10};
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(matched<4){long remaining=deadline-System.nanoTime();if(remaining<=0)throw new IOException("Header timeout");client.setSoTimeout((int)Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining)));int b=input.read();if(b<0)return;header.write(b);if(header.size()>16384)throw new IOException("Header limit");matched=(byte)b==end[matched]?matched+1:((byte)b==13?1:0);}
            String[] lines=header.toString("ISO-8859-1").split("\r\n");
            String[] request=lines[0].split(" ");if(request.length!=3||!request[2].startsWith("HTTP/1."))throw new IOException("Invalid request");
            String auth=null;StringBuilder clean=new StringBuilder(lines[0]).append("\r\n");
            for(int i=1;i<lines.length;i++){
                String line=lines[i];int colon=line.indexOf(':');if(colon<=0)throw new IOException("Invalid header");
                String name=line.substring(0,colon).trim();
                if(name.equalsIgnoreCase("Proxy-Authorization")){if(auth!=null)throw new IOException("Repeated credentials");auth=line.substring(colon+1).trim();}
                else clean.append(line).append("\r\n");
            }
            byte[] supplied=null;
            try{if(auth!=null&&auth.regionMatches(true,0,"Basic ",0,6))supplied=Base64.getDecoder().decode(auth.substring(6).trim());}catch(IllegalArgumentException ignored){}
            if(supplied==null||!MessageDigest.isEqual(expected,supplied)){
                client.getOutputStream().write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"Parvaz LAN\"\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));return;
            }
            if(!(request[0].equals("CONNECT")||request[1].startsWith("http://")))throw new IOException("Not an HTTP proxy request");
            target=new Socket();sockets.add(target);target.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),upstreamPort),3000);
            target.setSoTimeout(120000);client.setSoTimeout(120000);
            target.getOutputStream().write(clean.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1));
            final Socket upstream=target;reply=reverse.submit(()->pipe(upstream,client));pipe(client,target);
        }catch(IOException|RejectedExecutionException ignored){}finally{drop(client);drop(target);if(reply!=null)reply.cancel(true);slots.release();}
    }
    private void pipe(Socket src,Socket dst){try{byte[] buffer=new byte[16384];int n;while(running.get()&&(n=src.getInputStream().read(buffer))!=-1){dst.getOutputStream().write(buffer,0,n);dst.getOutputStream().flush();}}catch(IOException ignored){}finally{drop(src);drop(dst);}}
    private void drop(Socket s){if(s!=null){sockets.remove(s);try{s.close();}catch(IOException ignored){}}}
    @Override public void close(){if(!running.getAndSet(false))return;try{listener.close();}catch(IOException ignored){}for(Socket s:sockets)drop(s);workers.shutdownNow();reverse.shutdownNow();Arrays.fill(expected,(byte)0);}
}
