package com.parvaz.tunnel.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ResolverIdentityTest {
 private ResolverIdentity key(List<String> servers,String domains,String name){return new ResolverIdentity(servers,domains,name);}
 @Test public void missingDomainIsNotLiteralNull(){assertNotEquals(key(Arrays.asList("1.1.1.1"),null,null),key(Arrays.asList("1.1.1.1"),"null",null));}
 @Test public void missingPrivateDnsNameIsNotLiteralNull(){assertNotEquals(key(Collections.emptyList(),null,null),key(Collections.emptyList(),null,"null"));}
 @Test public void inputMutationCannotChangeSnapshot(){List<String> servers=new ArrayList<>(Arrays.asList("1.1.1.1"));ResolverIdentity before=key(servers,"search.invalid",null);servers.add("8.8.8.8");assertEquals(before,key(Arrays.asList("1.1.1.1"),"search.invalid",null));}
 @Test public void resolverPriorityAndIpv6ScopeStaySignificant(){assertNotEquals(key(Arrays.asList("1.1.1.1","8.8.8.8"),null,null),key(Arrays.asList("8.8.8.8","1.1.1.1"),null,null));assertNotEquals(key(Arrays.asList("fe80::1%2"),null,null),key(Arrays.asList("fe80::1%3"),null,null));}
 @Test public void diagnosticStringDoesNotExposePolicy(){String value=key(Arrays.asList("192.0.2.1"),"private.invalid","resolver.invalid").toString();assertEquals("ResolverIdentity[redacted]",value);}
}
