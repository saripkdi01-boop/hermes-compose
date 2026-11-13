package id.sukiapps.hermes.tool

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * calc — hitung ekspresi aritmetika (masterprompt §5.3: `calc`).
 *
 * Murni Kotlin, tanpa Android, tanpa jaringan — bisa di-unit-test di JVM.
 * Parser recursive-descent sendiri (BUKAN eval/script engine): hanya
 * angka, `+ - * / % ^`, tanda kurung, unary minus, dan fungsi `sqrt` /
 * `abs`. Tidak ada variabel, tidak ada pemanggilan lain, tidak ada akses
 * file/jaringan. Input dibatasi 500 karakter; hasil non-finite ditolak.
 * Gagal = [ToolResult] error yang jelas, bukan throw, bukan crash.
 */
class CalcTool : HermesTool {

    override val id: String = "calc"
    override val name: String = "Kalkulator"
    override val description: String =
        "Hitung ekspresi aritmetika dan kembalikan hasilnya. " +
            "Gunakan untuk verifikasi angka (total, selisih, persen, konversi) " +
            "agar jawaban tidak salah hitung. Mendukung + - * / % ^ (pangkat), " +
            "tanda kurung, dan fungsi sqrt() / abs(). " +
            "Contoh: \"2+3*4\", \"(150000-120000)/120000*100\", \"sqrt(144)\"."
    override val inputSchema: JSONObject = toolInputSchema(
        JSONObject().put(
            "expression",
            stringProp("Ekspresi aritmetika, contoh: \"2+3*4\". Maks 500 karakter."),
        ),
        listOf("expression"),
    )
    override val capabilities: Set<ToolCapability> = setOf(ToolCapability.READ_ONLY)

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val expr = args.optString("expression", "").trim()
        if (expr.isEmpty()) {
            return ToolResult("Error: parameter 'expression' wajib diisi", true)
        }
        if (expr.length > 500) {
            return ToolResult("Error: ekspresi terlalu panjang (maks 500 karakter)", true)
        }
        return try {
            val value = Parser(expr).parse()
            if (!value.isFinite()) {
                ToolResult("Error: hasil tidak berhingga (periksa pembagi / pangkat)", true)
            } else {
                ToolResult("HASIL: $expr = ${format(value)}")
            }
        } catch (e: CalcError) {
            ToolResult("Error: ${e.message}", true)
        } catch (e: Exception) {
            ToolResult("Error: ekspresi tidak bisa dihitung (${e.message})", true)
        }
    }

    private fun format(v: Double): String {
        if (v == kotlin.math.floor(v) && abs(v) < 9_007_199_254_740_992.0) {
            return v.toLong().toString()
        }
        // Desimal: potong nol berlebih, maks 10 digit di belakang koma.
        // Locale.US eksplisit — format() tanpa locale memakai koma desimal
        // di locale Indonesia ("2,5") dan merusak hasil.
        val s = "%.10f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
        return if (s.isEmpty() || s == "-0") "0" else s
    }

    private class CalcError(message: String) : Exception(message)

    /** Parser recursive-descent: expr → term ((+|-) term)* ; dst. */
    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            skipWs()
            if (pos < s.length) {
                throw CalcError("karakter tak dikenal '${s[pos]}' pada posisi $pos")
            }
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                skipWs()
                when {
                    consume('+') -> v += term()
                    consume('-') -> v -= term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = power()
            while (true) {
                skipWs()
                when {
                    consume('*') -> v *= power()
                    consume('/') -> {
                        val d = power()
                        if (d == 0.0) throw CalcError("pembagian dengan nol")
                        v /= d
                    }
                    consume('%') -> {
                        val d = power()
                        if (d == 0.0) throw CalcError("modulo dengan nol")
                        v %= d
                    }
                    else -> return v
                }
            }
        }

        /** Pangkat asosiatif-kanan: 2^3^2 = 2^(3^2). */
        private fun power(): Double {
            val base = unary()
            skipWs()
            if (consume('^')) {
                val exp = power()
                if (abs(exp) > 1000) throw CalcError("pangkat terlalu besar (maks |1000|)")
                val r = base.pow(exp)
                if (!r.isFinite()) throw CalcError("hasil pangkat tidak berhingga")
                return r
            }
            return base
        }

        private fun unary(): Double {
            skipWs()
            if (consume('-')) return -unary()
            if (consume('+')) return unary()
            return primary()
        }

        private fun primary(): Double {
            skipWs()
            if (consume('(')) {
                val v = expr()
                skipWs()
                if (!consume(')')) throw CalcError("kurung tutup ')' hilang")
                return v
            }
            if (pos < s.length && s[pos].isLetter()) {
                val name = readName()
                skipWs()
                if (!consume('(')) throw CalcError("fungsi '$name' butuh tanda kurung")
                val arg = expr()
                skipWs()
                if (!consume(')')) throw CalcError("kurung tutup ')' hilang setelah '$name'")
                return when (name) {
                    "sqrt" -> {
                        if (arg < 0) throw CalcError("sqrt dari bilangan negatif")
                        sqrt(arg)
                    }
                    "abs" -> abs(arg)
                    else -> throw CalcError("fungsi '$name' tidak dikenal (hanya sqrt, abs)")
                }
            }
            return number()
        }

        private fun number(): Double {
            skipWs()
            val start = pos
            var dots = 0
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) {
                if (s[pos] == '.') dots++
                pos++
            }
            if (pos == start || dots > 1) {
                throw CalcError(
                    if (pos < s.length) "angka tidak valid dekat '${s[pos]}' pada posisi $pos"
                    else "ekspresi berakhir sebelum angka",
                )
            }
            val v = s.substring(start, pos).toDoubleOrNull()
                ?: throw CalcError("angka tidak valid: '${s.substring(start, pos)}'")
            if (!v.isFinite()) throw CalcError("angka terlalu besar")
            return v
        }

        private fun readName(): String {
            val start = pos
            while (pos < s.length && s[pos].isLetter()) pos++
            return s.substring(start, pos)
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun consume(c: Char): Boolean {
            if (pos < s.length && s[pos] == c) {
                pos++
                return true
            }
            return false
        }
    }
}
