package com.parvaz.tunnel.core;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;

/** Android 10's archive parser only collects certificates when GET_SIGNATURES is set,
 * even when GET_SIGNING_CERTIFICATES was requested. Request both; verify the modern
 * current signer and permanent certificate normally. This is not a signature bypass.
 */
public final class ArchiveSignatures {
    private ArchiveSignatures(){}
    public static int flags(){return PackageManager.GET_SIGNATURES|(Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:0);}
    public static PackageInfo read(PackageManager manager,File file){return manager.getPackageArchiveInfo(file.getAbsolutePath(),flags());}
}
