package android.net;
/** Compile-only stub. URI paths are deliberately NOT tested by this harness. */
public class Uri {
 public static Uri parse(String s) { throw new UnsupportedOperationException("Android Uri not available in audit harness"); }
 public String getHost() { throw new UnsupportedOperationException(); }
 public int getPort() { throw new UnsupportedOperationException(); }
 public String getUserInfo() { throw new UnsupportedOperationException(); }
 public String getFragment() { throw new UnsupportedOperationException(); }
 public String getQueryParameter(String s) { throw new UnsupportedOperationException(); }
}
