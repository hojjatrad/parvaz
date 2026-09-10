package com.parvaz.tunnel.core;
/** Failed endpoint probes are weak evidence. Received proxy bytes clear strikes;
 * mere transmitted bytes are not acknowledgement that a peer answered. */
final class HealthPolicy {
 static final class Decision {
  final int strikes;final boolean restart;
  Decision(int strikes,boolean restart){this.strikes=strikes;this.restart=restart;}
 }
 static Decision evaluate(boolean coreAlive,boolean received,long delay,int threshold,int previous,int requestedStrikes){
  if(!coreAlive)return new Decision(1,true);
  if(received||delay>=0)return new Decision(0,false);
  if(delay==VerifiedProbe.UNKNOWN)return new Decision(Math.max(0,previous),false);
  int strikes=Math.min(1000,Math.max(0,previous)+1);
  return new Decision(strikes,strikes>=Math.max(3,Math.min(1000,requestedStrikes)));
 }
}
