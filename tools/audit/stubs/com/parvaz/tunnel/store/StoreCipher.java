package com.parvaz.tunnel.store;
import android.content.*;
/** Host store tests only. Crypto tested separately on Android; never included in the app. */
public final class StoreCipher {
 public static StoreCipher open(Context c,SharedPreferences p){return new StoreCipher();}
 public String encode(String name,String value){return value;}
 public String decode(String name,String value){return value;}
}
