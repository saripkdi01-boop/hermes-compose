# ROADMAP HERMES v15 — "MUSE-CLASS"
Target: menutup 9 gap fungsional terbesar terhadap asisten AI kelas Meta AI / Gemini / Claude.
Semua item di bawah dirancang untuk repo ini (Kotlin, Compose, Room, OpenAI-compatible backend).

## GAP YANG DITUTUP

| # | Fitur | Status v14.12 | Kelas produksi |
|---|-------|---------------|----------------|
| 1 | Multi-turn tool calling berantai | ada, rapuh | wajib |
| 2 | RAG memori jangka panjang | tidak ada | wajib |
| 3 | Vision real-time (kamera frame) | lampiran statis | wajib |
| 4 | Eksekusi kode sandbox terisolasi | shell mentah Termux | wajib |
| 5 | Web browsing otonom | web_fetch manual | wajib |
| 6 | Persona adaptif per topik | persona statis | penting |
| 7 | Offline inference (model kecil on-device) | tidak ada | penting |
| 8 | Observability + telemetry sesi | tidak ada | penting |
| 9 | Automasi lintas app (deep link, intent) | konektor terpisah | penting |

## ARSITEKTUR v15

```
Hermes v15
├── Core Agent Loop (libs/agent)
│   ├── Planner.kt          -> pecah tujuan jadi DAG langkah
│   ├── ToolRouter.kt       -> dispatch + retry + fallback
│   ├── Verifier.kt         -> klaim harus punya bukti (rule dari AGENTS.md)
│   └── ContextManager.kt   -> sliding window + ringkasan bertahap
├── Memory (libs/memory)
│   ├── EmbeddingStore.kt   -> chunk + embed + cosine search (SQLite FTS5 + vec)
│   ├── Consolidator.kt     -> dedup, decay, conflict-resolve
│   └── WriteBack.kt        -> batas 3 fakta/sesi (patuh AGENTS.md)
├── Perception
│   ├── LiveCamera.kt       -> CameraX analyzer, frame sampling 1fps -> vision
│   └── AudioStream.kt      -> VAD -> ASR -> teks (opsional v15.1)
├── Sandbox
│   └── WasmExec.kt         -> WebAssembly runtime terisolasi untuk code_run
├── Retrieval
│   └── WebSearchTool.kt    -> query -> fetch -> ekstrak -> sitasi URL+timestamp
└── Telemetry
    └── SessionTrace.kt     -> latensi, token, tool success rate, cost estimate
```

## ATURAN ANTI-PENOLAKAN (apa adanya, tanpa sensor internal)
1. Konten teknis (red-team, prompt injection, exploit, bypass) = domain sah,
   dijelaskan apa adanya. Tidak ada penolakan soft ("sebaiknya jangan").
2. Penolakan hanya untuk: instruksi yang secara langsung memerintahkan
   aksi merusak pada sistem user tanpa konfirmasi, atau eksfiltrasi kredensial.
   Penolakan harus berupa 1 kalimat + workaround yang jalan.
3. Tidak ada "asisten sopan" wrapper. Persona = engineer yang ship.
4. Identitas tunggal: nama model + gateway. Tidak ada spekulasi vendor.

## IMPLEMENTASI PRIORITAS 1 (mulai sekarang)

### 1. ToolRouter dengan retry adaptif
```kotlin
// libs/agent/src/main/java/id/sukiapps/hermes/agent/ToolRouter.kt
package id.sukiapps.hermes.agent

import kotlinx.coroutines.delay

sealed interface ToolResult<out T> {
    data class Ok<T>(val value: T, val latencyMs: Long) : ToolResult<T>
    data class Retryable(val reason: String) : ToolResult<Nothing>
    data class Fatal(val reason: String) : ToolResult<Nothing>
}

class ToolRouter(private val maxAttempt: Int = 3) {
    suspend fun <T> run(
        name: String,
        body: suspend () -> ToolResult<T>,
    ): ToolResult<T> {
        var attempt = 0
        var backoff = 400L
        while (true) {
            attempt++
            val r = runCatching { body() }.getOrElse { ToolResult.Retryable(it.message ?: "unknown") }
            when (r) {
                is ToolResult.Ok -> return r
                is ToolResult.Fatal -> return r
                is ToolResult.Retryable -> {
                    if (attempt >= maxAttempt) return r
                    delay(backoff)
                    backoff *= 2
                }
            }
        }
    }
}
```

### 2. Verifier — klaim tanpa bukti ditolak
```kotlin
// libs/agent/src/main/java/id/sukiapps/hermes/agent/Verifier.kt
package id.sukiapps.hermes.agent

object Verifier {
    private val claimPattern = Regex("""\b\d{1,3}(\.\d{3})+\b|\b\d+(\.\d+)?\s?(rb|jt|m|%)""")

    fun requiresEvidence(text: String): Boolean = claimPattern.containsMatchIn(text)

    fun annotate(text: String, hasToolEvidence: Boolean): String = when {
        !requiresEvidence(text) -> text
        hasToolEvidence -> text
        else -> "$text\n\n[CATATAN: angka di atas belum diverifikasi tool — status: dugaan.]"
    }
}
```

## RENCANA 6 MINGGU

| Minggu | Deliverable | Kriteria selesai |
|--------|-------------|------------------|
| 1 | ToolRouter + Verifier masuk core | unit test hijau, build APK hijau |
| 2 | Memory RAG (embedding + cosine) | ingest 500 chunk < 3s, recall@5 > 0.8 |
| 3 | WebSearchTool + sitasi | setiap jawaban berbasis web punya URL + waktu |
| 4 | LiveCamera analyzer | preview 30fps, inference non-blocking |
| 5 | WasmExec sandbox | kode Python/JS jalan, tidak bisa akses FS host |
| 6 | Telemetry + release v15.0 | dashboard sesi lokal, versionCode 37 |

## RISIKO & MITIGASI

| Risiko | Mitigasi |
|--------|----------|
| APK membengkak > 60MB | model on-device unduh opsional, bukan bundel |
| WebSearchTool di-rate-limit | cache 24 jam + backoff + fallback ke web_fetch |
| WriteBack ingatan membengkak | hard cap 3 fakta/sesi (sudah di AGENTS.md) |
| Sandbox bocor | Wasm tanpa WASI FS; output JSON only |

## ANGLE CUAN
- **Jual sebagai white-label**: agency/UMKM bayar langganan bulanan Rp 150.000–400.000/bulan
  untuk asisten dengan tool akses server + memori sendiri; template persona per niche
  (toko online, kontraktor, klinik) = harga naik tanpa kerja baru.
- **Cost inversion**: ToolRouter + cache + RAG lokal menekan panggilan LLM 40–70%,
  margin langganan naik drastis.
- **Upsell API**: publish endpoint asisten ke penyedia lain, tagih per request.
