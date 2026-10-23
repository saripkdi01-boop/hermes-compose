package id.sukiapps.hermes.data.audit

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * DevMode — Mode Developer (Sprint 1 Gate 2 slice 2).
 *
 * Bila AKTIF: PermissionGate melewati approval LOW/MEDIUM otomatis
 * (tercatat via="devmode"); HIGH tetap selalu popup.
 * Toggle dilindungi PIN 6 digit (disimpan sebagai hash SHA-256 + salt,
 * TIDAK pernah plaintext) dan — saat mengaktifkan dari Pengaturan —
 * konfirmasi kredensial perangkat (Keyguard; bisa sidik jari/PIN HP).
 */
object DevMode {

    private const val FILE = "permission_gate"
    private const val K_ENABLED = "devmode_enabled"
    private const val K_HASH = "devmode_pin_hash"
    private const val K_SALT = "devmode_pin_salt"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(K_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_ENABLED, on).apply()
    }

    fun hasPin(ctx: Context): Boolean =
        !prefs(ctx).getString(K_HASH, null).isNullOrEmpty()

    fun setPin(ctx: Context, pin: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit()
            .putString(K_SALT, salt)
            .putString(K_HASH, hash(salt, pin))
            .apply()
    }

    fun verifyPin(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString(K_SALT, null) ?: return false
        val expected = p.getString(K_HASH, null) ?: return false
        val actual = hash(salt, pin)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8)
        )
    }

    private fun hash(salt: String, pin: String): String {
        val d = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
}
