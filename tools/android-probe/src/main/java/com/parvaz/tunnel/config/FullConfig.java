package com.parvaz.tunnel.config;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
/** Probe fixture loader ONLY. Production full-config validation is tested in app's JVM suite. */
public final class FullConfig {
 public static boolean isFull(String p){return p.startsWith("full-");}
 public static JSONObject root(Profile p)throws JSONException{return new JSONObject(p.rawJson);}
}
