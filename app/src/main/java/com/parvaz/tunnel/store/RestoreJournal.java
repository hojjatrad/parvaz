package com.parvaz.tunnel.store;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONObject;

/** Encrypted write-ahead intent, replayed before exposing restored data.
 * Caller serializes operations (ProfileStore's monitor in production).
 * A durable DONE record prevents replay if best-effort file deletion fails.
 * This is process-crash recovery, not a hardware power-loss guarantee. */
public final class RestoreJournal {
    public static final String FILE_NAME="backup-restore-journal";
    private static final int MAX_BACKUP_BYTES=16*1024*1024,MAX_FILE_BYTES=32*1024*1024;
    public interface Codec {String seal(String plain)throws Exception;String open(String encrypted)throws Exception;}
    public interface Replay {void apply(String canonicalBackup)throws Exception;}
    private final AtomicFile file;private final Codec codec;
    public RestoreJournal(File path,Codec codec){this.file=new AtomicFile(path);this.codec=codec;}
    public static File path(Context context){return new File(context.getNoBackupFilesDir(),FILE_NAME);}
    public static boolean hasState(Context context){return hasState(path(context));}
    private static boolean hasState(File path){return path.exists()||new File(path+".bak").exists()||new File(path+".new").exists();}
    public boolean hasState(){return hasState(file.getBaseFile());}
    public void execute(String canonical,Replay replay)throws Exception {
        recover(replay); // Never replace unfinished work with another restore.
        if(canonical.getBytes(StandardCharsets.UTF_8).length>MAX_BACKUP_BYTES)throw new IOException("Backup size limit");
        JSONObject intent=new JSONObject().put("version",1).put("state","pending").put("id",UUID.randomUUID().toString()).put("backup",new JSONObject(canonical));
        writeVerified(intent);
        replay.apply(canonical);
        finish(intent.getString("id"));
    }
    public void recover(Replay replay)throws Exception {
        if(!hasState())return;
        byte[] encrypted;
        try{encrypted=readBytes();}catch(FileNotFoundException missing){
            // An interrupted first startWrite may leave only an uncommitted .new.
            File base=file.getBaseFile();
            if(base.exists()||new File(base+".bak").exists())throw missing;
            cleanup();return;
        }
        JSONObject state=new JSONObject(codec.open(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(encrypted)).toString()));
        if(state.getInt("version")!=1)throw new IOException("Unsupported restore journal");
        String id=state.getString("id");if(id.isEmpty()||id.length()>64)throw new IOException("Invalid restore journal");
        if("done".equals(state.getString("state"))){cleanup();return;}
        if(!"pending".equals(state.getString("state")))throw new IOException("Invalid restore journal state");
        String canonical=state.getJSONObject("backup").toString();
        if(canonical.getBytes(StandardCharsets.UTF_8).length>MAX_BACKUP_BYTES)throw new IOException("Backup size limit");
        replay.apply(canonical);finish(id);
    }
    private void finish(String id)throws Exception {
        writeVerified(new JSONObject().put("version",1).put("state","done").put("id",id));
        cleanup(); // A remaining authenticated DONE file is harmless on the next launch.
    }
    private void cleanup(){file.delete();new File(file.getBaseFile()+".new").delete();}
    private byte[] readBytes()throws IOException {
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(n>MAX_FILE_BYTES-out.size())throw new IOException("Restore journal size limit");out.write(buffer,0,n);}
            return out.toByteArray();
        }
    }
    private void writeVerified(JSONObject value)throws Exception {
        byte[] bytes=codec.seal(value.toString()).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_FILE_BYTES)throw new IOException("Restore journal size limit");
        FileOutputStream out=null;
        try{
            out=file.startWrite();out.write(bytes);out.getFD().sync();file.finishWrite(out);out=null;
            // AtomicFile.finishWrite has a void API: also detect a failed rename.
            if(!Arrays.equals(bytes,readBytes()))throw new IOException("Restore journal commit failed");
        }catch(Exception failure){if(out!=null)file.failWrite(out);throw failure;}
    }
}
