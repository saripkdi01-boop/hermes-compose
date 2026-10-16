package id.sukiapps.hermes;

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
        Cipher c = Cipher.getInstance(TRANS);
        // v9.4.1: JANGAN suplai IV sendiri — Android Keystore melarang
        // caller-provided IV ("Caller-provided IV not permitted") bila kunci
        // dibuat dengan setRandomizedEncryptionRequired(true). Biarkan cipher
        // yang generate IV, lalu ambil via getIV(). Pola ini juga valid untuk
        // kunci software JCE (unit test JVM). Format blob tidak berubah:
        // IV 12 byte || ciphertext+tag — decrypt() tak perlu diubah.
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = c.getIV();
        if (iv == null || iv.length != IV_LEN) {
            throw new IllegalStateException("IV tak valid");
        }
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
