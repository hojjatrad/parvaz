package android.content;
/** Minimal test interface, not Android persistence. */
public interface SharedPreferences {
 String getString(String key,String fallback);
 int getInt(String key,int fallback);
 boolean getBoolean(String key,boolean fallback);
 default Editor edit(){throw new UnsupportedOperationException();}
 interface Editor { Editor putString(String key,String value); void apply(); }
}
