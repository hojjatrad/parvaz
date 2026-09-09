package com.parvaz.tunnel.store;
import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={29,34},application=Application.class)
public class RestoreJournalTest {
 static final class Death extends Error {}
 File path;StoreCipher cipher;RestoreJournal.Codec codec;
 @Before public void setup()throws Exception {
  Context context=ApplicationProvider.getApplicationContext();path=new File(context.getNoBackupFilesDir(),"journal-test");cleanup();
  cipher=StoreCipher.open(context,context.getSharedPreferences("journal-test-key",0));
  codec=new RestoreJournal.Codec(){public String seal(String p){return cipher.encode("backup_restore_journal",p);}public String open(String e){return cipher.decodeRequired("backup_restore_journal",e);}};
 }
 @After public void cleanup(){if(path!=null)for(String suffix:new String[]{"",".new",".bak"})new File(path+suffix).delete();}
 @Test public void pendingIsEncryptedAndReplayedIdempotently()throws Exception {
  RestoreJournal journal=new RestoreJournal(path,codec);int[] writes={0};
  try{journal.execute("{\"password\":\"private-fixture\"}",s->{writes[0]++;throw new Death();});fail();}catch(Death expected){}
  String disk=new String(Files.readAllBytes(path.toPath()),StandardCharsets.UTF_8);
  assertTrue(disk.startsWith("PARVAZ-GCM-1:"));assertFalse(disk.contains("private-fixture"));
  journal.recover(s->{assertTrue(s.contains("private-fixture"));writes[0]++;});
  journal.recover(s->{fail("Completed journal replayed");});assertEquals(2,writes[0]);assertFalse(journal.hasState());
 }
 @Test public void failedDoneWriteRetainsReplayableIntent()throws Exception {
  int[] seals={0},writes={0};
  RestoreJournal journal=new RestoreJournal(path,new RestoreJournal.Codec(){
   public String seal(String p)throws Exception{if(++seals[0]==2)throw new Death();return codec.seal(p);}
   public String open(String e)throws Exception{return codec.open(e);}
  });
  try{journal.execute("{\"value\":1}",s->writes[0]++);fail();}catch(Death expected){}
  new RestoreJournal(path,codec).recover(s->writes[0]++);assertEquals(2,writes[0]);
 }
 @Test public void undeletedDoneDoesNotOverwriteLaterChanges()throws Exception {
  File undeletable=new File(path.getPath()){@Override public boolean delete(){return false;}};
  RestoreJournal journal=new RestoreJournal(undeletable,codec);String[] value={"old"};
  journal.execute("{\"value\":1}",s->value[0]="restored");assertTrue(path.isFile());
  value[0]="later user edit";new RestoreJournal(path,codec).recover(s->value[0]="wrong replay");assertEquals("later user edit",value[0]);
 }
 @Test public void plaintextCommittedJournalIsRetainedAndRejected()throws Exception {
  Files.write(path.toPath(),"{\"version\":1,\"state\":\"done\",\"id\":\"fake\"}".getBytes(StandardCharsets.UTF_8));
  try{new RestoreJournal(path,codec).recover(s->fail());fail();}catch(IllegalStateException expected){}
  assertTrue(path.isFile());
 }
 @Test public void wrongAadIsRetainedAndRejected()throws Exception {
  Files.write(path.toPath(),cipher.encode("profiles","{\"version\":1,\"state\":\"done\",\"id\":\"fake\"}").getBytes(StandardCharsets.UTF_8));
  try{new RestoreJournal(path,codec).recover(s->fail());fail();}catch(IllegalStateException expected){}
  assertTrue(path.isFile());
 }
 @Test public void uncommittedNewFileNeverAppliesAnything()throws Exception {
  Files.write(new File(path+".new").toPath(),"partial encrypted bytes".getBytes(StandardCharsets.UTF_8));
  RestoreJournal journal=new RestoreJournal(path,codec);journal.recover(s->fail());assertFalse(journal.hasState());
 }
 @Test public void cannotWriteIntentDoesNotCallReplay()throws Exception {
  File parent=new File(path+"-blocked");Files.write(parent.toPath(),new byte[]{1});
  try{new RestoreJournal(new File(parent,"intent"),codec).execute("{}",s->fail());fail();}catch(IOException expected){}finally{parent.delete();}
 }
}
