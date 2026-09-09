package com.parvaz.tunnel.store;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Device-bound AES-GCM for profile/URL records. No plaintext fallback or silent key rotation. */
public final class StoreCipher {
    private static final String PREFIX="PARVAZ-GCM-1:",ALIAS="com.parvaz.tunnel.records.v1";
    private final SecretKey key;
    StoreCipher(SecretKey key){this.key=key;}
    public static synchronized StoreCipher open(Context context,SharedPreferences prefs){
        boolean encrypted=false;for(String name:new String[]{"profiles","subs"})encrypted|=prefs.getString(name,"").startsWith(PREFIX);
        try{
            StoreCipher cipher=new StoreCipher(loadKey(!encrypted));
            SharedPreferences.Editor edit=prefs.edit();boolean migrate=false;
            for(String name:new String[]{"profiles","subs"}){
                String value=prefs.getString(name,null);if(value==null)continue;
                if(value.startsWith(PREFIX))cipher.decode(name,value); // Validate before any store may overwrite unreadable data.
                else{edit.putString(name,cipher.encode(name,value));migrate=true;}
            }
            if(migrate&&!edit.commit())throw new IllegalStateException("Record migration commit failed");
            return cipher;
        }catch(Exception error){throw new IllegalStateException("Secure records unavailable; restore an encrypted backup. Existing records were not erased.",error);}
    }
    public static SecretKey loadKey(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        if(!create)throw new java.security.KeyStoreException("Record key unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    public String encode(String name,String plain){try{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD(aad(name));
        byte[] encrypted=c.doFinal(plain.getBytes(StandardCharsets.UTF_8)),iv=c.getIV(),all=new byte[iv.length+encrypted.length];
        System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(encrypted,0,all,iv.length,encrypted.length);
        return PREFIX+Base64.encodeToString(all,Base64.NO_WRAP);
    }catch(Exception error){throw new IllegalStateException("Record encryption failed",error);}}
    public String decode(String name,String value){
        if(!value.startsWith(PREFIX))return value; // Only defaults or already-validated legacy input during migration.
        try{byte[] all=Base64.decode(value.substring(PREFIX.length()),Base64.NO_WRAP);if(all.length<28)throw new IllegalArgumentException();
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,all,0,12));c.updateAAD(aad(name));
            byte[] plain=c.doFinal(all,12,all.length-12);return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(plain)).toString();
        }catch(Exception error){throw new IllegalStateException("Record authentication failed",error);}
    }
    private static byte[] aad(String name){return ("com.parvaz.tunnel.records.v1/"+name).getBytes(StandardCharsets.UTF_8);}
}
