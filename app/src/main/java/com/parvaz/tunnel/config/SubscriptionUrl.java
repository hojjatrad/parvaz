package com.parvaz.tunnel.config;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;

/** Classifies user-supplied references only. Never fetch arbitrary URLs found inside configs. */
public final class SubscriptionUrl {
    private SubscriptionUrl() {}
    public static String unwrap(String input) {
        String text=input==null?"":input.trim();
        if(text.startsWith("\uFEFF"))text=text.substring(1).trim();
        for(int depth=0;depth<3;depth++) {
            String lower=text.toLowerCase(Locale.ROOT);
            if(lower.startsWith("hiddify://import/")){text=text.substring(17);continue;}
            if(lower.startsWith("v2rayng://")||lower.startsWith("hiddify://")||lower.startsWith("sing-box://")||lower.startsWith("clash://")) {
                try {
                    String query=new URI(text).getRawQuery();
                    if(query!=null)for(String pair:query.split("&"))if(pair.startsWith("url=")) {
                        text=URLDecoder.decode(pair.substring(4),"UTF-8");break;
                    }
                }catch(Exception e){return text;}
            }
            break;
        }
        return text.trim();
    }
    public static String normalize(String input) {
        try {
            String text=unwrap(input);
            int fragment=text.indexOf('#');if(fragment>=0)text=text.substring(0,fragment);
            if(text.length()>16384)throw new IllegalArgumentException();
            URI u=new URI(text);
            String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT);
            if(!scheme.equals("https")&&!scheme.equals("http"))throw new IllegalArgumentException();
            if(u.getHost()==null||u.getRawUserInfo()!=null||u.getPort()==0||u.getPort()>65535||u.getPort()<-1)throw new IllegalArgumentException();
            String host=u.getHost().toLowerCase(Locale.ROOT);
            int port=u.getPort();
            if((scheme.equals("https")&&port==443)||(scheme.equals("http")&&port==80))port=-1;
            String path=u.getRawPath();if(path==null||path.isEmpty())path="/";
            // Do not decode tokens, reorder queries, or collapse case-sensitive paths.
            return scheme+"://"+host+(port<0?"":":"+port)+path+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        }catch(Exception e){throw new IllegalArgumentException("INVALID_SUBSCRIPTION_URL");}
    }
    public static boolean isReference(String text) {
        String s=unwrap(text).toLowerCase(Locale.ROOT);
        return s.startsWith("https://")||s.startsWith("http://");
    }
    public static boolean valid(String text) {try{normalize(text);return true;}catch(IllegalArgumentException e){return false;}}
}
