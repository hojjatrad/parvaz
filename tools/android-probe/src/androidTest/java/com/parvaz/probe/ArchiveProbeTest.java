package com.parvaz.probe;
import android.content.Context;
import android.content.pm.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ArchiveProbeTest {
    @Test public void realPublishedArchiveParsing()throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        for(File dir:new File[]{c.getCacheDir(),c.getExternalCacheDir()}) {
            if(dir==null)continue;
            for(String name:new String[]{"parvaz-1.21.apk.part","parvaz-1.21.apk"}) {
                File file=new File(dir,name);
                try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("published.apk");OutputStream out=new FileOutputStream(file)) {
                    byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                PackageInfo info=c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
                String result=(info==null?"NULL":info.packageName+" version="+info.versionName+" code="+info.getLongVersionCode()+" signed="+(info.signingInfo!=null));
                android.util.Log.i("ParvazProbe",dir.getName()+" "+name+" "+result);
                System.out.println("ARCHIVE "+dir+" "+name+" "+result);
                if(name.endsWith(".apk")) {assertNotNull(info);assertEquals("com.parvaz.tunnel",info.packageName);assertEquals("1.21",info.versionName);assertNotNull(info.signingInfo);}
                file.delete();
            }
        }
    }
}
