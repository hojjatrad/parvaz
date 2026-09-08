package android.content;
/** Minimal read-only test seam used by the actual config builder. */
public interface SharedPreferences {
 String getString(String key,String fallback);
 int getInt(String key,int fallback);
 boolean getBoolean(String key,boolean fallback);
}
