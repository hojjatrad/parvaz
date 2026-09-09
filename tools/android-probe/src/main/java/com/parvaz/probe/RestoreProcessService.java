package com.parvaz.probe;
import android.app.Service;
import android.content.*;
import android.os.*;
import com.parvaz.tunnel.store.RestoreJournal;
import com.parvaz.tunnel.store.StoreCipher;
import java.io.File;
import org.json.JSONObject;

/** Isolated fixture backend with real production journal, AtomicFile, preferences
 * and Android Keystore. NOT a full BackupManager/ProfileStore integration probe. */
public final class RestoreProcessService extends Service {
 private static final String PLAN="{\"value\":\"new\",\"id\":\"stable-node\",\"secret\":\"probe-private\"}";
 private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper(),this::handle));
 @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
 private SharedPreferences records(){return getSharedPreferences("restore-probe-records",0);}
 private SharedPreferences settings(){return getSharedPreferences("restore-probe-settings",0);}
 private void commit(SharedPreferences.Editor edit){if(!edit.commit())throw new IllegalStateException("Fixture commit failed");}
 private static void die(){android.os.Process.killProcess(android.os.Process.myPid());throw new AssertionError("killProcess returned");}
 private boolean handle(Message request){
  Bundle result=new Bundle();
  try{
   if(request.what==9){die();return true;}
   if(request.what==1){
    for(String suffix:new String[]{"",".new",".bak"})new File(RestoreJournal.path(this)+suffix).delete();
    commit(records().edit().clear());commit(settings().edit().clear());
    StoreCipher cipher=StoreCipher.open(this,records());
    commit(records().edit().putString("profiles",cipher.encode("profiles","{\"value\":\"old\"}")));
    commit(settings().edit().putString("value","old"));
   }else{
    int phase=request.getData().getInt("phase",0);
    StoreCipher cipher=StoreCipher.open(this,records(),RestoreJournal.hasState(this));
    File path=new File(RestoreJournal.path(this).getPath()){
     @Override public boolean delete(){
      if(phase==4)die(); // finish(): verified DONE has been written, cleanup begins.
      if(phase==5)return false; // Real retained DONE, not an in-memory marker.
      return super.delete();
     }
    };
    RestoreJournal journal=new RestoreJournal(path,new RestoreJournal.Codec(){
     public String seal(String plain){return cipher.encode("backup_restore_journal",plain);}
     public String open(String encrypted){return cipher.decodeRequired("backup_restore_journal",encrypted);}
    });
    RestoreJournal.Replay replay=canonical->{
     if(phase==1)die(); // Verified PENDING, before any data writes.
     commit(records().edit().putString("profiles",cipher.encode("profiles",canonical)));
     if(phase==2)die(); // Records acknowledged, settings still old.
     commit(settings().edit().putString("value",new JSONObject(canonical).getString("value")));
     if(phase==3)die(); // Both acknowledged, DONE not written yet.
    };
    if(request.what==2)journal.execute(PLAN,replay);
    else if(request.what==3){
     // Simulate later legitimate edits with deletion still failing.
     journal.recover(replay);
     commit(records().edit().putString("profiles",cipher.encode("profiles","{\"value\":\"later\"}")));
     commit(settings().edit().putString("value","later"));
    }else if(request.what==4)journal.recover(replay);
    else throw new IllegalArgumentException("Unknown fixture command");
   }
   StoreCipher cipher=StoreCipher.open(this,records());
   result.putString("records",new JSONObject(cipher.decodeRequired("profiles",records().getString("profiles",""))).getString("value"));
   result.putString("settings",settings().getString("value",""));
   result.putBoolean("journal",RestoreJournal.hasState(this));
   result.putInt("pid",android.os.Process.myPid());
  }catch(Exception failure){result.putString("error",failure.getClass().getSimpleName());}
  Message reply=Message.obtain();reply.setData(result);try{request.replyTo.send(reply);}catch(RemoteException ignored){}
  return true;
 }
}
