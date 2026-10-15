package id.sukiapps.hermes;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * v6.0 — Penyimpanan API key terenkripsi.
 * Kunci AES-256 disimpan di AndroidKeyStore (tidak bisa diekstrak);
 * ciphertext (Base64) disimpan di SharedPreferences privat.
 * Migrasi otomatis: plaintext lama (K_API) dienkripsi lalu dihapus.
 * Key TIDAK PERNAH di-log di mana pun.
 */
public class KeyVault {

    private static final String ANDROID_KS = "AndroidKeyStore";
    private static final String ALIAS = "hermes_api_key_v1";
    private static final String K_ENC = "api_key_enc_v1";

    /**
     * v7.0 — penanda kegagalan Keystore di device tertentu.
     * Bila true, KeySetup menampilkan peringatan jujur dan memakai
     * penyimpanan biasa (tetap privat di aplikasi) — tanpa crash.
     */
    public static volatile boolean broken = false;
    public static volatile String lastError = "";

    /**
     * v7.1 — cache dalam memori proses. Akses AndroidKeyStore (binder IPC,
     * apalagi generate key pertama) bisa makan ratusan ms–detik di HP low-end;
     * JANGAN panggil getApiKey() berulang di main thread — pakai
     * getApiKeyCached() agar Keystore disentuh maksimal sekali per proses.
     */
    private static volatile String memKey = null;
    private static volatile boolean memKeyLoaded = false;

    /** Versi cached dari getApiKey(): aman dipanggil berulang kali. */
    public static String getApiKeyCached(Context ctx) {
        if (memKeyLoaded) return memKey == null ? "" : memKey;
        String k = getApiKey(ctx);
        memKey = k;
        memKeyLoaded = true;
        return k == null ? "" : k;
    }

    /** Hapus cache (dipakai setelah key diubah/dihapus). */
    public static void invalidateCache() {
        memKeyLoaded = false;
        memKey = null;
    }

    /** Simpan API key (enkripsi). Menghapus plaintext lama bila ada. */
    public static void putApiKey(Context ctx, String plain) throws Exception {
        SecretKey key = getOrCreateKey();
        byte[] blob = KeyCipher.encrypt(key,
                plain == null ? new byte[0] : plain.getBytes("UTF-8"));
        SharedPreferences p = Ui.prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        e.putString(K_ENC, Base64.encodeToString(blob, Base64.NO_WRAP));
        e.remove(Ui.K_API); // hapus plaintext lama
        e.apply();
        // v7.1: segarkan cache memori.
        memKey = plain;
        memKeyLoaded = true;
    }

    /**
     * Ambil API key (dekripsi). Bila belum ada versi terenkripsi tapi
     * plaintext lama ada -> migrasi otomatis lalu kembalikan plaintext.
     * v7.0: bila Keystore gagal di device ini -> tandai broken, pakai
     * plaintext bila ada (dengan peringatan di KeySetup), jangan crash.
     */
    public static String getApiKey(Context ctx) {
        try {
            SharedPreferences p = Ui.prefs(ctx);
            String enc = p.getString(K_ENC, null);
            if (enc != null && !enc.isEmpty()) {
                try {
                    byte[] blob = Base64.decode(enc, Base64.DEFAULT);
                    SecretKey key = getOrCreateKey();
                    return new String(KeyCipher.decrypt(key, blob), "UTF-8");
                } catch (Exception ke) {
                    markBroken(shortMsg(ke));
                    // Jatuh ke plaintext bila ada.
                }
            }
            String plain = p.getString(Ui.K_API, "");
            if (plain != null && !plain.isEmpty()) {
                try {
                    putApiKey(ctx, plain); // migrasi sekali jalan
                } catch (Exception ignored) {}
                return plain;
            }
            return "";
        } catch (Exception e) {
            try {
                String f = Ui.prefs(ctx).getString(Ui.K_API, "");
                return f == null ? "" : f;
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /**
     * v7.0 — fallback penyimpanan biasa bila Keystore rusak di device ini.
     * Dipakai KeySetupActivity dengan peringatan eksplisit ke user.
     */
    public static void putApiKeyPlain(Context ctx, String plain) {
        SharedPreferences.Editor e = Ui.prefs(ctx).edit();
        e.putString(Ui.K_API, plain == null ? "" : plain);
        e.remove(K_ENC);
        e.apply();
        broken = true;
        // v7.1: segarkan cache memori.
        memKey = plain;
        memKeyLoaded = true;
    }

    private static void markBroken(String reason) {
        broken = true;
        lastError = reason == null ? "" : reason;
    }

    private static String shortMsg(Exception e) {
        try {
            String m = e.getMessage();
            if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
            return m.length() > 120 ? m.substring(0, 120) : m;
        } catch (Exception ignored) {
            return "";
        }
    }

    /** true bila ada key (terenkripsi maupun plaintext lama). */
    public static boolean hasKey(Context ctx) {
        try {
            SharedPreferences p = Ui.prefs(ctx);
            String enc = p.getString(K_ENC, null);
            if (enc != null && !enc.isEmpty()) return true;
            String pl = p.getString(Ui.K_API, "");
            return pl != null && !pl.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Hapus key tersimpan (terenkripsi + plaintext). */
    public static void clearKey(Context ctx) {
        try {
            Ui.prefs(ctx).edit().remove(K_ENC).remove(Ui.K_API).apply();
        } catch (Exception ignored) {}
        invalidateCache(); // v7.1
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KS);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
}
