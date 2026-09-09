package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.events.*;
import org.yaml.snakeyaml.nodes.*;
import java.io.StringReader;
import java.util.*;

/** Explicit full-profile import. Ordinary subscription extraction is intentionally unchanged.
 * Android owns listeners/TUN/control interfaces; routing, DNS and outbound groups stay in the engine. */
public final class FullConfig {
 private FullConfig(){}
 public static boolean isFull(String protocol){return Arrays.asList("full-singbox","full-clash","full-xray").contains(protocol);}
 public static Profile parse(String text)throws JSONException {
  if(text==null||text.length()>LinkParser.MAX_INPUT_CHARS)throw new IllegalArgumentException("Input limit");
  JSONObject root=text.trim().startsWith("{")?JsonInput.object(text):yaml(text);LinkParser.checkJsonDepth(root.toString());
  Profile p=LinkParser.newProfile();
  if(root.has("proxies")||root.has("proxy-providers")){
   if(root.optJSONArray("proxies")==null&&root.optJSONObject("proxy-providers")==null)throw new IllegalArgumentException("No proxy configuration");p.protocol="full-clash";
   if(root.optJSONArray("proxies")!=null&&root.optJSONArray("proxies").length()==0&&root.optJSONObject("proxy-providers")==null)throw new IllegalArgumentException("Empty proxy configuration");
   JSONArray rules=root.optJSONArray("rules");if((rules==null||rules.length()==0)&&!root.optString("mode").equals("global"))throw new IllegalArgumentException("Full Clash import needs explicit routing");
  }else if(SingBoxParser.isSingBox(root.toString())){p.protocol="full-singbox";boolean remote=false;
   for(String section:new String[]{"outbounds","endpoints"}){JSONArray list=root.optJSONArray(section);if(list!=null)for(int i=0;i<list.length();i++){JSONObject node=list.optJSONObject(i);if(node!=null&&(node.has("server")||node.has("peers")))remote=true;}}
   if(!remote)throw new IllegalArgumentException("No remote VPN outbound");
  }
  else if(root.has("outbounds")&&CustomOutbound.extract(root)!=null)p.protocol="full-xray";
  else throw new IllegalArgumentException("Not a complete VPN configuration");
  validate(root,0);validateInboundPolicy(root);p.address="full-config";p.port=443;p.rawJson=root.toString();p.remark=p.protocol;return p;
 }
 public static JSONObject root(Profile p)throws JSONException {Profile parsed=parse(p.rawJson);if(!parsed.protocol.equals(p.protocol))throw new IllegalArgumentException("Engine type mismatch");return JsonInput.object(parsed.rawJson);}
 public static String inboundTag(JSONObject root)throws JSONException {
  java.util.Set<String> names=new java.util.LinkedHashSet<>();collectInbound(root,names);
  if(names.size()>1)throw new IllegalArgumentException("Multiple distinct inbound policies cannot be mapped to one Android VPN");
  if(!names.isEmpty())return names.iterator().next();
  JSONArray in=root.optJSONArray("inbounds");return in!=null&&in.length()>0&&in.optJSONObject(0)!=null?in.getJSONObject(0).optString("tag","parvaz"):"parvaz";
 }
 private static void collectInbound(Object value,java.util.Set<String> names)throws JSONException {
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collectInbound(a.get(i),names);}
  if(!(value instanceof JSONObject))return;JSONObject o=(JSONObject)value;Iterator<String> keys=o.keys();
  while(keys.hasNext()){String key=keys.next();Object child=o.get(key);
   if(key.equals("inbound")||key.equals("inboundTag")){if(child instanceof String)names.add((String)child);else if(child instanceof JSONArray)for(int i=0;i<((JSONArray)child).length();i++)names.add(((JSONArray)child).getString(i));}
   else collectInbound(child,names);
  }
 }
 private static void validateInboundPolicy(JSONObject root)throws JSONException {
  inboundTag(root);
  JSONArray rules=root.optJSONArray("rules");if(rules!=null)for(int i=0;i<rules.length();i++){
   String rule=rules.optString(i,"").toUpperCase(Locale.ROOT);
   if(rule.startsWith("PROCESS-")||rule.startsWith("IN-NAME,")||rule.startsWith("IN-TYPE,")||rule.startsWith("IN-PORT,"))throw new IllegalArgumentException("Desktop process/listener rules are not Android VPN rules");
  }
 }
 private static void validate(Object value,int depth)throws JSONException {
  if(depth>32)throw new IllegalArgumentException("Configuration depth");
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)validate(a.get(i),depth+1);}
  if(!(value instanceof JSONObject))return;JSONObject o=(JSONObject)value;
  for(String key:new ArrayList<String>(){{Iterator<String> it=o.keys();while(it.hasNext())add(it.next());}}){
   Object child=o.get(key);String k=key.toLowerCase(Locale.ROOT);
   if(k.matches("(^|.*[-_])script($|[-_].*)")||Arrays.asList("command","post-up","post-down","network_namespace","network-namespace","process_name","process_path","package_name","user_id").contains(k))throw new IllegalArgumentException("Executable or privileged configuration is not allowed");
   boolean file=k.endsWith("_path")||k.endsWith("-path")||k.equals("certificatefile")||k.equals("keyfile")||k.equals("path")&&(o.has("url")||Arrays.asList("file","local","remote").contains(o.optString("type")));
   if(file&&child instanceof String){String path=(String)child;if(path.startsWith("/")||path.contains("..")||path.contains("\\")||path.contains(":"))throw new IllegalArgumentException("External file path is not allowed");}
   if(k.equals("url")&&child instanceof String&&!(child.toString().startsWith("https://")))throw new IllegalArgumentException("Provider URLs require HTTPS");
   validate(child,depth+1);
  }
 }
 private static JSONObject yaml(String text)throws JSONException {
  LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setAllowRecursiveKeys(false);options.setMaxAliasesForCollections(0);options.setNestingDepthLimit(32);options.setCodePointLimit(LinkParser.MAX_INPUT_CHARS);
  Yaml yaml=new Yaml(new SafeConstructor(options));int events=0,depth=0;
  for(Event e:yaml.parse(new StringReader(text))){if(++events>100000||e instanceof AliasEvent)throw new IllegalArgumentException("Unsafe YAML");if(e instanceof CollectionStartEvent&&++depth>32)throw new IllegalArgumentException("YAML depth");if(e instanceof CollectionEndEvent)depth--;}
  if(!(yaml.load(text) instanceof Map))throw new IllegalArgumentException("Invalid YAML root");
  Object result=node(yaml.compose(new StringReader(text)),"");if(!(result instanceof JSONObject))throw new IllegalArgumentException("Invalid YAML root");return (JSONObject)result;
 }
 private static Object node(Node n,String key)throws JSONException {
  if(n instanceof MappingNode){JSONObject result=new JSONObject();for(NodeTuple pair:((MappingNode)n).getValue()){if(!(pair.getKeyNode() instanceof ScalarNode))throw new IllegalArgumentException("Non-scalar key");String k=((ScalarNode)pair.getKeyNode()).getValue();if(result.has(k))throw new IllegalArgumentException("Duplicate key");result.put(k,node(pair.getValueNode(),k));}return result;}
  if(n instanceof SequenceNode){JSONArray result=new JSONArray();for(Node child:((SequenceNode)n).getValue())result.put(node(child,key));return result;}
  ScalarNode scalar=(ScalarNode)n;String text=scalar.getValue(),k=key.toLowerCase(Locale.ROOT);
  boolean secret=k.contains("password")||k.contains("secret")||k.contains("token")||k.endsWith("key")||Arrays.asList("uuid","id","short-id","username","auth","name","tag","type","server","sni","servername","fingerprint").contains(k);
  if(secret||scalar.getScalarStyle()!=DumperOptions.ScalarStyle.PLAIN)return text;
  if(text.equalsIgnoreCase("true")||text.equalsIgnoreCase("false"))return Boolean.parseBoolean(text);
  if(text.matches("-?(0|[1-9][0-9]*)")){try{return Long.parseLong(text);}catch(NumberFormatException ignored){}}
  if(text.matches("-?[0-9]+\\.[0-9]+")){try{return Double.parseDouble(text);}catch(NumberFormatException ignored){}}
  if(Tag.NULL.equals(scalar.getTag()))return JSONObject.NULL;return text;
 }
}
