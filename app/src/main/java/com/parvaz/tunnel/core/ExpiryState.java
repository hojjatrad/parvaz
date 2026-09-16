package com.parvaz.tunnel.core;
/** Expiry is independent of quota volume. Zero seconds remaining is expired. */
public final class ExpiryState {
 private ExpiryState(){}
 public static long seconds(long value){return value>10000000000L?value/1000:value;}
 public static long daysRemaining(long expiry,long now){expiry=seconds(expiry);return expiry<=now?-1:(expiry-now)/86400;}
}
