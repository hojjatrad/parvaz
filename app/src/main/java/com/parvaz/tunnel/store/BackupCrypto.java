package com.parvaz.tunnel.store;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Authenticated, versioned backup. New files specify their KDF and work factor.
 * Legacy v1 remains readable; its historical KDF ambiguity is resolved by GCM verification. */
public final class BackupCrypto {
    public static final String MAGIC="PARVAZ-ENC-2";
    private static final String LEGACY="PARVAZ-ENC-1";
    private static final int ITERATIONS=210000,MAX_BYTES=16*1024*1024;
    private BackupCrypto(){}
    public static boolean isEncrypted(String text){return text!=null&&(text.trim().startsWith(MAGIC+":")||text.trim().startsWith(LEGACY+":"));}
    public static String encrypt(String text,char[] password)throws Exception {
        if(password==null||password.length==0||text==null)throw new IllegalArgumentException("Invalid backup input");
        byte[] plain=text.getBytes(StandardCharsets.UTF_8);if(plain.length>MAX_BYTES)throw new IllegalArgumentException("Backup too large");
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        String header=MAGIC+":PBKDF2-SHA256:"+ITERATIONS+":"+b64(salt)+":"+b64(iv);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,derive(password,salt,ITERATIONS,"PBKDF2WithHmacSHA256"),new GCMParameterSpec(128,iv));
        c.updateAAD(header.getBytes(StandardCharsets.US_ASCII));
        try{return header+":"+b64(c.doFinal(plain));}finally{Arrays.fill(plain,(byte)0);}
    }
    public static String decrypt(String envelope,char[] password)throws Exception {
        if(envelope==null||password==null||password.length==0||envelope.length()>MAX_BYTES*2)throw new IllegalArgumentException("Invalid backup");
        String[] parts=envelope.trim().split(":",-1);
        if(parts.length==6&&parts[0].equals(MAGIC)) {
            if(!parts[1].equals("PBKDF2-SHA256"))throw new IllegalArgumentException("Unsupported backup KDF");
            int rounds=Integer.parseInt(parts[2]);if(rounds<120000||rounds>1000000)throw new IllegalArgumentException("Invalid work factor");
            byte[] salt=decode(parts[3],16),iv=decode(parts[4],12),cipher=decode(parts[5],-1);
            String header=String.join(":",Arrays.copyOf(parts,5));
            return decryptWith(cipher,password,salt,iv,rounds,"PBKDF2WithHmacSHA256",header);
        }
        if(parts.length==4&&parts[0].equals(LEGACY)) {
            byte[] salt=decode(parts[1],16),iv=decode(parts[2],12),cipher=decode(parts[3],-1);
            try{return decryptWith(cipher,password,salt,iv,120000,"PBKDF2WithHmacSHA256",null);}
            catch(java.security.GeneralSecurityException e){return decryptWith(cipher,password,salt,iv,120000,"PBKDF2WithHmacSHA1",null);}
        }
        throw new IllegalArgumentException("Unsupported backup format");
    }
    private static String decryptWith(byte[] encrypted,char[] password,byte[] salt,byte[] iv,int rounds,String kdf,String aad)throws Exception{
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,derive(password,salt,rounds,kdf),new GCMParameterSpec(128,iv));
        if(aad!=null)c.updateAAD(aad.getBytes(StandardCharsets.US_ASCII));byte[] plain=c.doFinal(encrypted);
        try{return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(plain)).toString();}finally{Arrays.fill(plain,(byte)0);}
    }
    private static javax.crypto.SecretKey derive(char[] password,byte[] salt,int rounds,String algorithm)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password,salt,rounds,256);byte[] key=null;
        try{key=SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();return new SecretKeySpec(key,"AES");}
        finally{spec.clearPassword();if(key!=null)Arrays.fill(key,(byte)0);}
    }
    private static byte[] decode(String value,int length){
        byte[] result=Base64.decode(value,Base64.NO_WRAP);
        if(length>=0&&result.length!=length||length<0&&(result.length<16||result.length>MAX_BYTES+16))throw new IllegalArgumentException("Invalid backup field");return result;
    }
    private static String b64(byte[] value){return Base64.encodeToString(value,Base64.NO_WRAP);}
}
