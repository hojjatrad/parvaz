package com.parvaz.probe;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.parvaz.tunnel.core.ExternalCore;
import com.parvaz.tunnel.model.Profile;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class NativeEngineTest {
 @Test public void packagedEnginesStartAuthenticateAndStop()throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  for(String kind:new String[]{"full-singbox","full-clash"}){
   Profile p=new Profile();p.protocol=kind;p.rawJson=kind.equals("full-singbox")?"{\"outbounds\":[{\"type\":\"direct\"}]}":"{\"proxies\":[],\"rules\":[\"MATCH,DIRECT\"]}";
   ExternalCore core=ExternalCore.start(context,p,null);assertTrue(core.isRunning());core.close();assertFalse(core.isRunning());
   android.util.Log.i("ParvazProbe","NATIVE_ENGINE_OK "+kind+" SDK="+android.os.Build.VERSION.SDK_INT);
  }
 }
}
