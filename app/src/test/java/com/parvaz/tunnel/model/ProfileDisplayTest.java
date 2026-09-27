package com.parvaz.tunnel.model;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProfileDisplayTest {
 @Test public void ipv6DisplayUsesUnambiguousBracketsWithoutMutatingAddress(){for(String host:new String[]{"2001:db8::1","fe80::1%test0"}){Profile p=new Profile();p.address=host;p.port=443;assertEquals("["+host+"]:443",p.displayAddress());assertEquals(host,p.address);}}
 @Test public void alreadyBracketedAddressIsNotDoubleWrapped(){Profile p=new Profile();p.address="[2001:db8::1]";p.port=8443;assertEquals("[2001:db8::1]:8443",p.displayAddress());}
 @Test public void ipv4HostnamesAndNullKeepExistingFormatting(){for(String host:new String[]{"192.0.2.1","example.invalid",null}){Profile p=new Profile();p.address=host;p.port=443;assertEquals((host==null?"":host)+":443",p.displayAddress());}}
}
