package com.parvaz.probe;
import android.content.*;
import android.os.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import com.parvaz.tunnel.store.RestoreJournal;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class RestoreProcessDeathTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private final class Connection implements ServiceConnection,AutoCloseable {
  final CountDownLatch ready=new CountDownLatch(1);volatile IBinder binder;Messenger remote;boolean bound;
  Connection()throws Exception {
   bound=context.bindService(new Intent(context,RestoreProcessService.class),this,Context.BIND_AUTO_CREATE);assertTrue(bound);
   assertTrue("Service bind timeout",ready.await(20,TimeUnit.SECONDS));remote=new Messenger(binder);
  }
  @Override public void onServiceConnected(ComponentName name,IBinder service){if(binder==null){binder=service;ready.countDown();}}
  @Override public void onServiceDisconnected(ComponentName name){}
  Bundle call(int command,int phase)throws Exception {
   CountDownLatch completed=new CountDownLatch(1);Bundle[] data={null};Message m=Message.obtain();m.what=command;
   Bundle args=new Bundle();args.putInt("phase",phase);m.setData(args);
   m.replyTo=new Messenger(new Handler(Looper.getMainLooper(),reply->{data[0]=reply.getData();completed.countDown();return true;}));
   remote.send(m);assertTrue("Fixture reply timeout",completed.await(20,TimeUnit.SECONDS));
   assertFalse("Fixture error: "+data[0].getString("error"),data[0].containsKey("error"));return data[0];
  }
  void killAt(int command,int phase)throws Exception {
   CountDownLatch dead=new CountDownLatch(1);IBinder.DeathRecipient recipient=dead::countDown;binder.linkToDeath(recipient,0);
   Message m=Message.obtain();m.what=command;Bundle data=new Bundle();data.putInt("phase",phase);m.setData(data);
   remote.send(m);assertTrue("Expected OS process death",dead.await(20,TimeUnit.SECONDS));
  }
  @Override public void close(){if(bound){context.unbindService(this);bound=false;}}
 }
 private void atPhase(int phase)throws Exception {
  int pid;
  try(Connection connection=new Connection()){
   pid=connection.call(1,0).getInt("pid");connection.killAt(2,phase);
  }
  String encrypted=new String(Files.readAllBytes(RestoreJournal.path(context).toPath()),StandardCharsets.UTF_8);
  assertTrue(encrypted.startsWith("PARVAZ-GCM-1:"));assertFalse(encrypted.contains("probe-private"));
  try(Connection reopened=new Connection()){
   Bundle result=reopened.call(4,0);assertNotEquals("Not a cold process",pid,result.getInt("pid"));
   assertEquals("new",result.getString("records"));assertEquals("new",result.getString("settings"));assertFalse(result.getBoolean("journal"));
   assertEquals("new",reopened.call(4,0).getString("records"));
  }
 }
 @Test public void coldRecoveryAfterPendingCommit()throws Exception{atPhase(1);}
 @Test public void coldRecoveryAfterRecordCommit()throws Exception{atPhase(2);}
 @Test public void coldRecoveryAfterSettingsCommit()throws Exception{atPhase(3);}
 @Test public void coldRecoveryAfterDoneBeforeDelete()throws Exception{atPhase(4);}
 @Test public void retainedDoneCannotRollBackLaterEditsAfterColdRestart()throws Exception {
  int pid;
  try(Connection connection=new Connection()){
   pid=connection.call(1,0).getInt("pid");assertTrue(connection.call(2,5).getBoolean("journal"));
   Bundle later=connection.call(3,5);assertEquals("later",later.getString("records"));assertTrue(later.getBoolean("journal"));
   connection.killAt(9,0);
  }
  try(Connection reopened=new Connection()){
   Bundle result=reopened.call(4,0);assertNotEquals(pid,result.getInt("pid"));assertEquals("later",result.getString("records"));assertEquals("later",result.getString("settings"));
  }
 }
}
