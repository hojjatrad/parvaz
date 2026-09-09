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
            for(String name:new String[]{"parvaz-1.26.1.apk.part","parvaz-1.26.1.apk"}) {
                File file=new File(dir,name);
                try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("published.apk");OutputStream out=new FileOutputStream(file)) {
                    byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                PackageInfo old=c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo info=com.parvaz.tunnel.core.ArchiveSignatures.read(c.getPackageManager(),file);
                assertNotNull("Fixed archive parser must return metadata",info);
                assertNotNull("Fixed archive parser must collect signing info",info.signingInfo);
                Signature[] signers=info.signingInfo.getApkContentsSigners();assertEquals(1,signers.length);
                byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray());
                StringBuilder fingerprint=new StringBuilder();for(byte value:hash)fingerprint.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                assertEquals("d1383b8f34da5d3299b13634de421487289d82c1bb47ec3bc7f20ae8d02fe500",fingerprint.toString());
                String result="oldSigned="+(old!=null&&old.signingInfo!=null)+"; fixedPinned=true; "+(info==null?"NULL":info.packageName+" version="+info.versionName+" code="+info.getLongVersionCode()+" signed="+(info.signingInfo!=null));
                android.util.Log.i("ParvazProbe",dir.getName()+" "+name+" "+result);
                System.out.println("ARCHIVE "+dir+" "+name+" "+result);
                try(PrintWriter report=new PrintWriter(new FileWriter(new File(c.getFilesDir(),"probe.txt"),true))) {report.println("ARCHIVE "+dir+" "+name+" "+result);}
                if(name.endsWith(".apk")) {assertNotNull(info);assertEquals("com.parvaz.tunnel",info.packageName);assertEquals("1.26.1",info.versionName);assertEquals(28,info.getLongVersionCode());assertNotNull(info.signingInfo);}
                file.delete();
            }
        }
    }
}
