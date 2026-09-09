package com.parvaz.tunnel.store;
import org.robolectric.annotation.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
/** Test-only key provider: production still always uses AndroidKeyStore, with no fallback. */
@Implements(value=StoreCipher.class,isInAndroidSdk=false)
public class ShadowStoreCipher {
 @Implementation public static SecretKey loadKey(boolean create){return new SecretKeySpec(new byte[32],"AES");}
}
