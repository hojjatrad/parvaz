package com.parvaz.tunnel.core;
/** Row states are not tunnel readiness. Only a measured positive value is milliseconds. */
public final class LatencyResult {
 public static final int UNTESTED=-1,FAILED=-2,TESTING=-3,UNCONFIRMED=-4,BUSY=-5,CANCELLED=-6;
 private LatencyResult(){}
 public static int scoped(int value,boolean currentNetwork){return currentNetwork||value==CANCELLED?value:UNCONFIRMED;}
 public static int restored(int value){return value==TESTING?UNTESTED:value;}
 public static int measured(long value){
  if(value>0)return (int)Math.min(Integer.MAX_VALUE,value);
  if(value==ProbeAdmission.BUSY)return BUSY;
  if(value==-2)return UNCONFIRMED; // VerifiedProbe.UNKNOWN; not a measured failure.
  return FAILED;
 }
}
