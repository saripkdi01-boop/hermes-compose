package id.sukiapps.hermes;

import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * v6.0 — Inti kripto AES/GCM murni JCE (tanpa Android).
 * Format blob: IV 12 byte || ciphertext+tag.
 * Dipakai KeyVault (AndroidKeyStore di device) dan unit test JVM.
 */
public class KeyCipher {

    private static final String TRANS = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    public static byte[] encrypt(SecretKey key, byte[] plain) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance(TRANS);
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal(plain);
        byte[] out = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, out, 0, IV_LEN);
        System.arraycopy(ct, 0, out, IV_LEN, ct.length);
        return out;
    }

    public static byte[] decrypt(SecretKey key, byte[] blob) throws Exception {
        if (blob == null || blob.length <= IV_LEN) {
            throw new IllegalArgumentException("blob terlalu pendek");
        }
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(blob, IV_LEN, blob.length);
        Cipher c = Cipher.getInstance(TRANS);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return c.doFinal(ct);
    }
}
