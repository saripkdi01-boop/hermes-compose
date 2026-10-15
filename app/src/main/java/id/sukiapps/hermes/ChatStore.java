package id.sukiapps.hermes;

import android.content.Context;
import java.io.File;

/**
 * Shim kompatibilitas minimal untuk ApkShareProvider.java yang dipertahankan
 * verbatim dari codebase legacy. Hanya berisi helper direktori privat yang
 * dipakai provider; seluruh logika ChatStore lama digantikan oleh
 * data.local.ChatRepository (Room).
 */
public final class ChatStore {

    private ChatStore() {}

    /** Direktori privat file APK hasil analisis (v5.1). */
    static File sharedApksDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "shared_apks");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Direktori privat file backup (v6.0). */
    static File backupsDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "backups");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }
}
