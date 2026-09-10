package com.parvaz.tunnel.core;
import java.util.*;
/** Detached, in-memory resolver policy. Order is significant: DNS/search priority
 * must not be sorted away. Null is not the literal domain "null". Never log it. */
final class ResolverIdentity {
 private final List<String> servers;
 private final String domains,privateDnsName;
 ResolverIdentity(List<String> servers,String domains,String privateDnsName){
  this.servers=Collections.unmodifiableList(new ArrayList<>(servers));
  this.domains=domains;this.privateDnsName=privateDnsName;
 }
 @Override public boolean equals(Object other){
  if(this==other)return true;if(!(other instanceof ResolverIdentity))return false;
  ResolverIdentity that=(ResolverIdentity)other;
  return servers.equals(that.servers)&&Objects.equals(domains,that.domains)&&Objects.equals(privateDnsName,that.privateDnsName);
 }
 @Override public int hashCode(){return Objects.hash(servers,domains,privateDnsName);}
 @Override public String toString(){return "ResolverIdentity[redacted]";}
}
