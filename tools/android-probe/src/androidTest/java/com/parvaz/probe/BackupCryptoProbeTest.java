package com.parvaz.probe;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.parvaz.tunnel.store.BackupCrypto;
import com.parvaz.tunnel.store.BackupInput;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.*;
import javax.crypto.spec.*;
import static org.junit.Assert.*;

/** Disposable fixture passwords only. Exercises real Android crypto providers, not signing keys. */
@RunWith(AndroidJUnit4.class)
public class BackupCryptoProbeTest {
 private static final String PASSWORD="fixture-only-password";
 private static final String TEXT="{\"profiles\":[],\"subscriptions\":[],\"label\":\"پشتیبان آزمایشی\"}";
 @Test public void v2RoundTripUsesAndroidProviders()throws Exception {
  String encrypted=BackupCrypto.encrypt(TEXT,PASSWORD.toCharArray());
  assertTrue(encrypted.startsWith("PARVAZ-ENC-2:PBKDF2-SHA256:210000:"));
  assertFalse(encrypted.contains("پشتیبان"));
  assertEquals(TEXT,BackupCrypto.decrypt(encrypted,PASSWORD.toCharArray()));
  assertEquals(encrypted,BackupInput.read(new ByteArrayInputStream(encrypted.getBytes(StandardCharsets.UTF_8))));
  android.util.Log.i("ParvazProbe","BACKUP_ANDROID_V2_ROUNDTRIP_OK SDK="+android.os.Build.VERSION.SDK_INT);
 }
 private void reject(String encrypted,String password)throws Exception {
  boolean rejected=false;
  try{BackupCrypto.decrypt(encrypted,password.toCharArray());}
  catch(java.security.GeneralSecurityException|IllegalArgumentException expected){rejected=true;}
  assertTrue("Malformed or unauthenticated envelope accepted",rejected);
 }
 @Test public void wrongPasswordTamperingAndInvalidMetadataAreRejected()throws Exception {
  String encrypted=BackupCrypto.encrypt(TEXT,PASSWORD.toCharArray());
  reject(encrypted,"wrong-fixture-password");
  String[] fields=encrypted.split(":",-1);
  byte[] body=android.util.Base64.decode(fields[5],android.util.Base64.NO_WRAP);body[body.length-1]^=1;
  fields[5]=android.util.Base64.encodeToString(body,android.util.Base64.NO_WRAP);reject(String.join(":",fields),PASSWORD);
  reject(encrypted.replace(":210000:",":220000:"),PASSWORD);
  reject(encrypted.replace(":210000:",":119999:"),PASSWORD);
  reject(encrypted.replace(":210000:",":1000001:"),PASSWORD);
  reject(encrypted.replace("PBKDF2-SHA256","unknown-kdf"),PASSWORD);
  reject(encrypted.replace("PARVAZ-ENC-2","PARVAZ-ENC-999"),PASSWORD);
  fields=encrypted.split(":",-1);fields[3]="AA==";reject(String.join(":",fields),PASSWORD);
  android.util.Log.i("ParvazProbe","BACKUP_ANDROID_AUTH_METADATA_REJECTION_OK SDK="+android.os.Build.VERSION.SDK_INT);
 }
 private String legacy(String algorithm,byte[] plain)throws Exception {
  byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
  PBEKeySpec spec=new PBEKeySpec(PASSWORD.toCharArray(),salt,120000,256);
  byte[] key=SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();spec.clearPassword();
  try{
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
   return "PARVAZ-ENC-1:"+b64(salt)+":"+b64(iv)+":"+b64(cipher.doFinal(plain));
  }finally{java.util.Arrays.fill(key,(byte)0);}
 }
 private String b64(byte[] bytes){return android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);}
 @Test public void bothLegacyKdfsWorkButAuthenticatedInvalidUtf8IsRejected()throws Exception {
  for(String algorithm:new String[]{"PBKDF2WithHmacSHA256","PBKDF2WithHmacSHA1"}){
   assertEquals(TEXT,BackupCrypto.decrypt(legacy(algorithm,TEXT.getBytes(StandardCharsets.UTF_8)),PASSWORD.toCharArray()));
  }
  String malformed=legacy("PBKDF2WithHmacSHA256",new byte[]{(byte)0xc3,0x28});
  boolean rejected=false;try{BackupCrypto.decrypt(malformed,PASSWORD.toCharArray());}catch(java.nio.charset.CharacterCodingException expected){rejected=true;}
  assertTrue("Authenticated invalid UTF-8 accepted",rejected);
  android.util.Log.i("ParvazProbe","BACKUP_ANDROID_LEGACY_AND_UTF8_OK SDK="+android.os.Build.VERSION.SDK_INT);
 }
}
