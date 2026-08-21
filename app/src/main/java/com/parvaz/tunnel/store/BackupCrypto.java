package com.parvaz.tunnel.store;

import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Password-based encryption for exported backups.
 *
 * <p>A backup file is the single most sensitive artefact the app produces: it contains
 * every server address plus the UUIDs, passwords and REALITY keys needed to use them.
 * Users share these over Telegram and email and store them in cloud drives, so a
 * plaintext export is a credential leak waiting to happen.
 *
 * <p>The scheme is AES-256-GCM with a PBKDF2-HMAC-SHA256-derived key. GCM is
 * authenticated, so a wrong password or a corrupted file fails loudly at decrypt time
 * instead of silently yielding garbage. Salt and IV are random per export and stored in
 * the envelope, which is versioned so future formats stay readable.
 *
 * <p>Envelope (JSON-free, so it cannot be confused with a plaintext backup):
 * <pre>PARVAZ-ENC-1:&lt;base64 salt&gt;:&lt;base64 iv&gt;:&lt;base64 ciphertext&gt;</pre>
 */
public final class BackupCrypto {

    private BackupCrypto() {
    }

    /** Magic prefix identifying an encrypted export. */
    public static final String MAGIC = "PARVAZ-ENC-1";

    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;          // GCM standard nonce length
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;

    /**
     * PBKDF2 rounds. High enough to make offline guessing expensive, low enough that a
     * budget phone finishes in well under a second.
     */
    private static final int ITERATIONS = 120000;

    /** True when the text looks like output of {@link #encrypt}. */
    public static boolean isEncrypted(String text) {
        return text != null && text.trim().startsWith(MAGIC + ":");
    }

    /**
     * Encrypts a backup document.
     *
     * @param plaintext the JSON backup
     * @param password  user-chosen password; must not be empty
     * @return the envelope string
     * @throws Exception if the password is empty or crypto is unavailable
     */
    public static String encrypt(String plaintext, char[] password) throws Exception {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("empty password");
        }
        SecureRandom random = new SecureRandom();

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(utf8(plaintext));

        return MAGIC + ":" + b64(salt) + ":" + b64(iv) + ":" + b64(ciphertext);
    }

    /**
     * Decrypts an envelope produced by {@link #encrypt}.
     *
     * @throws IllegalArgumentException if the envelope is malformed
     * @throws Exception                if the password is wrong or the file was tampered
     *                                  with (GCM tag mismatch)
     */
    public static String decrypt(String envelope, char[] password) throws Exception {
        if (envelope == null) {
            throw new IllegalArgumentException("null backup");
        }
        String trimmed = envelope.trim();
        String[] parts = trimmed.split(":");
        if (parts.length != 4 || !MAGIC.equals(parts[0])) {
            throw new IllegalArgumentException("not an encrypted Parvaz backup");
        }

        byte[] salt = unb64(parts[1]);
        byte[] iv = unb64(parts[2]);
        byte[] ciphertext = unb64(parts[3]);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = cipher.doFinal(ciphertext);

        try {
            return new String(plain, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new String(plain);
        }
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static byte[] utf8(String s) throws UnsupportedEncodingException {
        return (s == null ? "" : s).getBytes("UTF-8");
    }

    private static String b64(byte[] raw) {
        return Base64.encodeToString(raw, Base64.NO_WRAP);
    }

    private static byte[] unb64(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
