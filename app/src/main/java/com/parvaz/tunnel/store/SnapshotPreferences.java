package com.parvaz.tunnel.store;
import android.content.SharedPreferences;import java.util.*;
/** Immutable tuning input: experimental candidate values never enter global preferences. */
public final class SnapshotPreferences implements SharedPreferences {
 private final Map<String,Object> values=new HashMap<>();
 public SnapshotPreferences(Map<String,?> base,Map<String,?> overrides){values.putAll(base);values.putAll(overrides);for(String key:new ArrayList<>(values.keySet()))if(values.get(key) instanceof Set)values.put(key,Collections.unmodifiableSet(new HashSet<>((Set<?>)values.get(key))));}
 public Map<String,?> getAll(){return new HashMap<>(values);}public boolean contains(String k){return values.containsKey(k);}
 public String getString(String k,String d){return values.containsKey(k)?(String)values.get(k):d;}
 @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return values.containsKey(k)?(Set<String>)values.get(k):d;}
 public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}public long getLong(String k,long d){return values.containsKey(k)?(Long)values.get(k):d;}
 public float getFloat(String k,float d){return values.containsKey(k)?(Float)values.get(k):d;}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}
 public Editor edit(){throw new UnsupportedOperationException("Immutable probe settings");}public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
}
