package com.parvaz.tunnel.store;
import android.content.Context;
import java.io.File;
/** Host refresh tests do not exercise backup I/O. Never shipped in the app. */
public final class RestoreJournal {
 public interface Codec {String seal(String p)throws Exception;String open(String e)throws Exception;}
 public interface Replay {void apply(String p)throws Exception;}
 public RestoreJournal(File path,Codec codec){}
 public static File path(Context c){return new File("unused-host-journal");}
 public static boolean hasState(Context c){return false;}
 public boolean hasState(){return false;}
 public void recover(Replay r){throw new UnsupportedOperationException("Use Android journal tests");}
 public void execute(String p,Replay r){throw new UnsupportedOperationException("Use Android journal tests");}
}
