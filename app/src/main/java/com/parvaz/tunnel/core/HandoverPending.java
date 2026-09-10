package com.parvaz.tunnel.core;
/** Single pending request. Never carry an old session's cache-reset request into
 * a newly created core, or lose a new request when an older probe completes. */
final class HandoverPending {
 static final class Work {
  final long ticket,session;final boolean resetDns;final Runnable reconnect;
  Work(long t,long s,boolean d,Runnable r){ticket=t;session=s;resetDns=d;reconnect=r;}
  boolean owns(long t,long s){return ticket==t&&session==s;}
 }
 private Work pending;
 synchronized void offer(long ticket,long session,boolean resetDns,Runnable reconnect){
  boolean reset=resetDns||(pending!=null&&pending.owns(ticket,session)&&pending.resetDns);
  pending=new Work(ticket,session,reset,reconnect);
 }
 synchronized Work take(){Work result=pending;pending=null;return result;}
 synchronized void clear(){pending=null;}
}
