package com.parvaz.tunnel.core;
/** A positive stored number is history unless accompanied by current process/network proof. */
public final class LatencyStamp {
 public final String source,target;public final long measuredAt;
 private final long epoch,monotonic;
 private LatencyStamp(String source,String target){this.source=source;this.target=target;measuredAt=System.currentTimeMillis();monotonic=System.nanoTime();epoch=NetworkEpoch.current();}
 public static long networkRevision(){return NetworkEpoch.current();}
 public static LatencyStamp manual(String target){return new LatencyStamp("manual HTTPS RTT",target==null?"not recorded":target);}
 public static LatencyStamp live(){return new LatencyStamp("live HTTPS RTT","not recorded");}
 public boolean fresh(){long age=System.nanoTime()-monotonic;return NetworkEpoch.owns(epoch)&&age>=0&&age<=300000000000L;}
}
