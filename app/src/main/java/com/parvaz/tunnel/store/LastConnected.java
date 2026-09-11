package com.parvaz.tunnel.store;
import android.content.Context;
import android.content.SharedPreferences;
import com.parvaz.tunnel.model.Profile;
import org.json.*;
/** Durable last VERIFIED live connection, separate from the user's tentative row
 * selection. A manual ping or local core start is never a successful connection. */
public final class LastConnected {
 public static final String KEY="last_verified_connection";
 private LastConnected(){}
 static boolean remember(SharedPreferences prefs,Profile p){
  try{
   String record=new JSONObject().put("schema",1).put("id",p.id).put("source",p.subscriptionId).put("identity",ProfileIdentity.fingerprint(p)).toString();
   if(!record.equals(prefs.getString(KEY,"")))return prefs.edit().putString(KEY,record).commit();
   return true;
  }catch(JSONException invalid){return false;}
 }
 public static Profile resolve(ProfileStore store,SharedPreferences prefs){
  String value=prefs.getString(KEY,"");if(value.isEmpty()||value.length()>4096)return null;
  try{
   com.parvaz.tunnel.config.LinkParser.checkJsonDepth(value);
   JSONObject record=new JSONObject(value);if(record.optInt("schema",0)!=1)return null;
   Profile p=store.getActiveById(record.getString("id"));
   return p!=null&&record.getString("source").equals(p.subscriptionId)&&record.getString("identity").equals(ProfileIdentity.fingerprint(p))?p:null;
  }catch(JSONException|IllegalArgumentException invalid){return null;}
 }
 /** Cold launch / STOP / boot only; never call while picking a different row. */
 public static boolean restore(Context context){
  ProfileStore store=ProfileStore.f(context);SharedPreferences prefs=context.getApplicationContext().getSharedPreferences("parvaz_prefs",0);
  synchronized(store){Profile p=resolve(store,prefs);return p!=null&&prefs.edit().putString("selected_profile",p.id).commit();}
 }
}
