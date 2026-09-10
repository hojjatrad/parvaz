package com.parvaz.tunnel.core;
/** Pure monotonic hysteresis. Slow but responsive is degraded, not disconnected. */
final class SwitchPolicy {
 static long backoff(int failures){return Math.min(300000L,30000L << Math.min(4,Math.max(0,failures-1)));}
 static boolean better(long current,long candidate){return current>0&&candidate>0&&current-candidate>=100&&candidate<=current*0.75;}
 static boolean qualityDue(long now,long connectedAt,long lastSearch,int slowSamples,boolean received){return !received&&slowSamples>=3&&now-connectedAt>=90000&&now-lastSearch>=300000;}
 static double cost(long delay,int historyScore,double jitter){return Math.max(1,delay)+Math.max(0,jitter)*1.5+Math.max(0,100-historyScore)*5;}
}
