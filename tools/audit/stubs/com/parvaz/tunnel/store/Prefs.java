package com.parvaz.tunnel.store;
public class Prefs {
 public android.content.Context appContext = new android.content.Context();
 public android.content.SharedPreferences f343a = new android.content.SharedPreferences() {
  public String getString(String k,String d) { return d; }
  public int getInt(String k,int d) { return d; }
  public boolean getBoolean(String k,boolean d) { return d; }
 };
}
