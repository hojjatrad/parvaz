package android.net;
/** Compile-only stub. URI paths are deliberately NOT tested by this harness. */
public class Uri {
 public static String decode(String s){throw new UnsupportedOperationException("Android URI decoding required");}
 public static Uri parse(String s) { throw new UnsupportedOperationException("Android Uri not available in audit harness"); }
 public String getHost() { throw new UnsupportedOperationException(); }
 public int getPort() { throw new UnsupportedOperationException(); }
 public String getEncodedUserInfo(){throw new UnsupportedOperationException("URI parsing needs Android");}
 public String getUserInfo() { throw new UnsupportedOperationException(); }
 public String getFragment() { throw new UnsupportedOperationException(); }
 public String getQueryParameter(String s) { throw new UnsupportedOperationException(); }
}
