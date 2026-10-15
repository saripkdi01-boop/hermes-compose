package id.sukiapps.hermes;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * v5.1 — Provider berbagi file APK hasil analisis via Intent ACTION_SEND.
 *
 * Pengganti androidx FileProvider (ditolak: menambah dependensi, melanggar
 * batasan "0 dependensi"). Implementasi minimal memakai framework saja:
 * hanya melayani file di dalam getFilesDir()/shared_apks/ (.apk),
 * getFilesDir()/backups/ (.zip, v6.0), dan getCacheDir()/camera/ (.jpg, v7.0),
 * menolak path traversal via canonical-path check.
 *
 * apk/zip = read-only. cam/ = tulis saja (untuk EXTRA_OUTPUT kamera),
 * tetap terproteksi path traversal + ekstensi .jpg.
 *
 * Authorities: id.sukiapps.hermes.fileprovider (exported=false,
 * grantUriPermissions=true di manifest). URI bentuk:
 *   content://id.sukiapps.hermes.fileprovider/apk/<namafile.apk>
 */
public class ApkShareProvider extends ContentProvider {

    public static final String AUTHORITY = "id.sukiapps.hermes.fileprovider";
    private static final int CODE_APK = 1;
    private static final int CODE_ZIP = 2; // v6.0: file backup ZIP
    private static final int CODE_CAM = 3; // v7.0: foto hasil kamera (tulis)
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "apk/*", CODE_APK);
        MATCHER.addURI(AUTHORITY, "zip/*", CODE_ZIP);
        MATCHER.addURI(AUTHORITY, "cam/*", CODE_CAM);
    }

    /** Bangun content:// URI untuk satu file APK di shared_apks/. */
    public static Uri uriFor(File apkFile) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("apk")
                .appendPath(apkFile.getName())
                .build();
    }

    /** v6.0: URI untuk satu file ZIP backup di backups/. */
    public static Uri zipUriFor(File zipFile) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("zip")
                .appendPath(zipFile.getName())
                .build();
    }

    /**
     * v7.0: URI untuk file foto kamera di getCacheDir()/camera/.
     * Dipakai sebagai EXTRA_OUTPUT untuk ACTION_IMAGE_CAPTURE
     * (tanpa permission CAMERA — intent sistem).
     */
    public static Uri camUriFor(android.content.Context ctx, File jpgFile) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("cam")
                .appendPath(jpgFile.getName())
                .build();
    }

    /** Direktori foto kamera (cache privat). */
    public static File cameraDir(android.content.Context ctx) {
        File d = new File(ctx.getCacheDir(), "camera");
        try { d.mkdirs(); } catch (Exception ignored) {}
        return d;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Resolve nama file -> File di shared_apks/, backups/, atau camera/,
     *  tolak di luar direktori. */
    private File resolve(String name) throws FileNotFoundException {
        return resolve(name, false);
    }

    private File resolve(String name, boolean zip) throws FileNotFoundException {
        return resolve(name, zip ? 2 : 1);
    }

    private File resolve(String name, int kind) throws FileNotFoundException {
        try {
            boolean isZip = kind == 2;
            boolean isCam = kind == 3;
            String ext = isZip ? ".zip" : (isCam ? ".jpg" : ".apk");
            if (name == null || name.isEmpty()
                    || !name.toLowerCase().endsWith(ext)
                    || name.contains("/") || name.contains("\\")) {
                throw new FileNotFoundException("nama tidak valid");
            }
            File dir = isZip ? ChatStore.backupsDir(getContext())
                    : isCam ? cameraDir(getContext())
                    : ChatStore.sharedApksDir(getContext());
            File f = new File(dir, name);
            String canonDir = dir.getCanonicalPath() + File.separator;
            if (!f.getCanonicalPath().startsWith(canonDir)) {
                throw new FileNotFoundException("di luar direktori");
            }
            if (!isCam && !f.isFile()) {
                // cam: file boleh belum ada (kamera akan menulis).
                throw new FileNotFoundException("file tidak ditemukan");
            }
            return f;
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException("gagal resolve file");
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        int code = MATCHER.match(uri);
        if (code != CODE_APK && code != CODE_ZIP && code != CODE_CAM)
            throw new FileNotFoundException("uri salah");
        boolean wantWrite = mode != null && mode.contains("w");
        if (wantWrite && code != CODE_CAM)
            throw new FileNotFoundException("read-only");
        File f = resolve(uri.getLastPathSegment(), code);
        int pfdMode = wantWrite
                ? (ParcelFileDescriptor.MODE_WRITE_ONLY
                   | ParcelFileDescriptor.MODE_CREATE
                   | ParcelFileDescriptor.MODE_TRUNCATE)
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, pfdMode);
    }

    @Override
    public String getType(Uri uri) {
        int code = MATCHER.match(uri);
        if (code == CODE_APK) return "application/vnd.android.package-archive";
        if (code == CODE_ZIP) return "application/zip";
        if (code == CODE_CAM) return "image/jpeg";
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        int code = MATCHER.match(uri);
        if (code != CODE_APK && code != CODE_ZIP && code != CODE_CAM) return null;
        try {
            File f = resolve(uri.getLastPathSegment(), code);
            String[] cols = (projection != null) ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor cur = new MatrixCursor(cols);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
                else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
                else row[i] = null;
            }
            cur.addRow(row);
            return cur;
        } catch (Exception e) {
            return null;
        }
    }

    // Operasi tulis tidak didukung — provider ini read-only.
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
