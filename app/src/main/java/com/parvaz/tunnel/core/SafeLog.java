package com.parvaz.tunnel.core;
import java.util.regex.Pattern;
/** Conservative privacy boundary. Never retain free-form connected/switch/error data. */
public final class SafeLog {
 private SafeLog(){}
 private static final Pattern URL=Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s<>]+"),
 SECRET=Pattern.compile("(?i)(password|passwd|token|uuid|secret|authorization|private.?key|public.?key|sni|host)\\s*[=:]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"),
 UUID=Pattern.compile("(?i)\\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b"),
 HOST=Pattern.compile("(?i)(?<![\\w])(?:[a-z0-9_-]+\\.)+[a-z][a-z0-9-]{1,62}\\b"),
 IP=Pattern.compile("(?<![\\w])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w])"),
 IPV6=Pattern.compile("(?i)(?<![\\w])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:.]*(?:%[\\w]+)?");
 public static String message(String value){
  if(value==null)return "";if(value.length()>32768)value=value.substring(0,32768)+" [truncated]";
  StringBuilder safe=new StringBuilder();for(String line:value.split("\\n",-1)){
   String lower=line.toLowerCase(java.util.Locale.ROOT);
   if(lower.contains("connected:")||lower.contains("switching to ")||lower.contains("error:")||lower.contains("failed:"))line="[connection event; private details omitted]";
   line=URL.matcher(line).replaceAll("[url]");line=SECRET.matcher(line).replaceAll("$1=[redacted]");line=UUID.matcher(line).replaceAll("[id]");line=IP.matcher(line).replaceAll("[ip]");line=IPV6.matcher(line).replaceAll("[ipv6]");line=HOST.matcher(line).replaceAll("[host]");
   if(safe.length()>0)safe.append('\n');safe.append(line);
  }return safe.toString();
 }
 public static String stack(Throwable error){
  StringBuilder out=new StringBuilder();int depth=0;for(Throwable t=error;t!=null&&depth++<6;t=t.getCause()){
   out.append(t.getClass().getSimpleName()).append(" (message omitted)\n");int n=0;
   for(StackTraceElement frame:t.getStackTrace()){if(n++>=40)break;out.append("  ").append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber()).append('\n');}
  }return out.toString();
 }
}
