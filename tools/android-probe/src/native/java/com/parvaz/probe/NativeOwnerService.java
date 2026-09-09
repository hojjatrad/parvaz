package com.parvaz.probe;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import com.parvaz.tunnel.core.ExternalCore;
import com.parvaz.tunnel.model.Profile;

/** Test helper only, never included in the production APK. Owns a child in a separate app process. */
public final class NativeOwnerService extends Service {
    public static final int START=1, CRASH=2, READY=3, ERROR=4;
    private HandlerThread worker;
    private Messenger messenger;
    private ExternalCore core;
    @Override public void onCreate() {
        super.onCreate();
        worker=new HandlerThread("fixture-native-owner");worker.start();
        messenger=new Messenger(new Handler(worker.getLooper(),message->{
            if(message.what==CRASH) {
                // Deliberately bypass onDestroy()/ExternalCore.close(): exercise the native parent guard.
                android.os.Process.killProcess(android.os.Process.myPid());return true;
            }
            if(message.what!=START || message.replyTo==null)return true;
            Message reply=Message.obtain();Bundle data=new Bundle();
            try {
                if(core!=null)core.close();
                String kind=message.getData().getString("kind");
                if(!"full-singbox".equals(kind)&&!"full-clash".equals(kind))throw new IllegalArgumentException("Fixture kind");
                Profile p=new Profile();p.protocol=kind;
                p.rawJson=kind.equals("full-singbox")?"{\"outbounds\":[{\"type\":\"direct\"}]}":"{\"proxies\":[],\"rules\":[\"MATCH,DIRECT\"]}";
                core=ExternalCore.start(this,p,null);
                reply.what=READY;data.putInt("pid",android.os.Process.myPid());data.putInt("port",core.port);
                data.putString("username",core.username);data.putString("password",core.password);
            } catch(Exception e) {
                reply.what=ERROR;data.putString("error",e.getClass().getSimpleName());
            }
            reply.setData(data);
            try {message.replyTo.send(reply);}catch(RemoteException ignored){if(core!=null)core.close();}
            return true;
        }));
    }
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    @Override public void onDestroy(){
        if(core!=null)core.close();
        if(worker!=null)worker.quitSafely();
        super.onDestroy();
    }
}
