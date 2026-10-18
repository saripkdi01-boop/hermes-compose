package id.sukiapps.hermes.ui.settings

import id.sukiapps.hermes.data.ConnectorId
import id.sukiapps.hermes.data.ConnectorState
import id.sukiapps.hermes.data.PrefKey
import id.sukiapps.hermes.data.SettingsRepository
import id.sukiapps.hermes.net.ActivityLogger
import id.sukiapps.hermes.net.ConnectorTester
import id.sukiapps.hermes.net.FailureReason
import id.sukiapps.hermes.net.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Wiring Fase G: status Hub dihitung dari SettingsRepository (state nyata,
 * bukan Map statis), dan "Uji ulang" memanggil ConnectorTester lalu
 * menyimpan hasilnya via repo + mencatat ke ActivityLogger.
 *
 * Bukan AndroidX ViewModel (tanpa dependensi tambahan) — dibuat sekali
 * via rememberSettingsWiring() di graph.
 */
class SettingsWiring(
    private val repo: SettingsRepository,
    private val tester: ConnectorTester,
    private val logger: ActivityLogger,
    private val scope: CoroutineScope,
) {
    private val _statuses =
        MutableStateFlow<Map<String, FeatureStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, FeatureStatus>> = _statuses.asStateFlow()

    private val _modelName = MutableStateFlow<String?>(null)
    val modelName: StateFlow<String?> = _modelName.asStateFlow()

    private val _apiKeySaved = MutableStateFlow(false)
    val apiKeySaved: StateFlow<Boolean> = _apiKeySaved.asStateFlow()

    /** Error hasil tes terakhir per fitur — dibersihkan bila konfigurasi berubah. */
    private val testErrors = mutableMapOf<String, String>()
    private var lastConfigKey = ""

    init {
        scope.launch {
            combine(
                repo.connection, repo.model, repo.connectors, repo.persona
            ) { _, _, _, _ -> Unit }.collect { refresh() }
        }
        refresh()
    }

    private fun configKey(): String {
        val c = repo.connection.value
        return "${c.baseUrl}|${repo.hasSecret(PrefKey.SEC_API_KEY)}|" +
            "${repo.model.value.modelId}|${repo.persona.value.systemPrompt.hashCode()}"
    }

    private fun isValidBaseUrl(url: String): Boolean {
        val u = url.trim()
        return (u.startsWith("https://") || u.startsWith("http://")) &&
            !u.contains(" ") && u.length > 10
    }

    /** Hitung ulang semua status dari state repo. Aman dipanggil dari thread mana pun. */
    fun refresh() {
        val key = configKey()
        if (key != lastConfigKey) {
            testErrors.clear()
            lastConfigKey = key
        }
        val conn = repo.connection.value
        val model = repo.model.value
        val conns = repo.connectors.value
        val persona = repo.persona.value
        val map = mutableMapOf<String, FeatureStatus>()

        map["connection"] = testErrors["connection"]?.let { FeatureStatus.Error(it) }
            ?: when {
                conn.baseUrl.isBlank() -> FeatureStatus.Pending
                isValidBaseUrl(conn.baseUrl) -> FeatureStatus.Ready
                else -> FeatureStatus.Error("URL tidak valid")
            }
        val keySaved = repo.hasSecret(PrefKey.SEC_API_KEY)
        map["apikey"] = testErrors["apikey"]?.let { FeatureStatus.Error(it) }
            ?: if (keySaved) FeatureStatus.Ready else FeatureStatus.Pending
        map["model"] = testErrors["model"]?.let { FeatureStatus.Error(it) }
            ?: if (model.modelId.isBlank()) FeatureStatus.Pending
            else FeatureStatus.Ready
        map["parameters"] = FeatureStatus.Ready
        map["persona"] =
            if (persona.systemPrompt.isBlank() && persona.presets.isEmpty())
                FeatureStatus.Pending else FeatureStatus.Ready
        map["soul"] = FeatureStatus.Unknown
        map["appearance"] = FeatureStatus.Ready
        map["tts"] = if (repo.getPrefString("tts_enabled", "false") == "true")
            FeatureStatus.Ready else FeatureStatus.Pending
        map["backup"] = FeatureStatus.Unknown
        map["audit"] = FeatureStatus.Unknown

        for ((fid, cid) in FEATURE_CONNECTORS) {
            val cs = conns[cid] ?: ConnectorState()
            map[fid] = when {
                !cs.configured -> FeatureStatus.Pending
                cs.lastTestOk -> FeatureStatus.Ready
                cs.lastTestMessage.isNotBlank() ->
                    FeatureStatus.Error(cs.lastTestMessage)
                else -> FeatureStatus.Pending
            }
        }

        _statuses.value = map
        _modelName.value = model.modelId.ifBlank { null }
        _apiKeySaved.value = keySaved
    }

    /**
     * Uji ulang satu fitur — HANYA dipicu ketukan user (aturan: konektor
     * manual-trigger). Hasil disimpan ke repo + activity log.
     */
    fun retest(featureId: String) {
        if (featureId !in SUPPORTED_TESTS) return
        scope.launch(Dispatchers.IO) {
            setOne(featureId, FeatureStatus.Working)
            val result: TestResult? = try {
                runTest(featureId)
            } catch (e: Exception) {
                TestResult.Fail(0, FailureReason.NETWORK, "Gagal: ${e.message}", null)
            }
            when (result) {
                is TestResult.Ok -> {
                    testErrors.remove(featureId)
                    val cid = FEATURE_CONNECTORS[featureId]
                    if (cid != null) {
                        repo.saveConnectorTest(cid, true, result.detail)
                    } else {
                        setOne(featureId, FeatureStatus.Ready)
                    }
                    logger.log(
                        ActivityLogger.ActivityEvent.TestResult(
                            featureId, true, result.ms, null
                        )
                    )
                }
                is TestResult.Fail -> {
                    testErrors[featureId] = result.message
                    val cid = FEATURE_CONNECTORS[featureId]
                    if (cid != null) {
                        repo.saveConnectorTest(cid, false, result.message)
                    } else {
                        setOne(featureId, FeatureStatus.Error(result.message))
                    }
                    logger.log(
                        ActivityLogger.ActivityEvent.TestResult(
                            featureId, false, result.ms, result.reason.name
                        )
                    )
                }
                null -> setOne(featureId, FeatureStatus.Unknown)
            }
            refresh()
        }
    }

    private suspend fun runTest(featureId: String): TestResult? {
        val baseUrl = repo.connection.value.baseUrl.trim().trimEnd('/')
        return when (featureId) {
            "connection" -> tester.testConnection(baseUrl)
            "apikey" -> tester.testApiKey(baseUrl, repo.getApiKey())
            "model" -> tester.testModel(
                baseUrl,
                repo.getApiKey(),
                repo.model.value.modelId.ifBlank { "gpt-4o-mini" }
            )
            "github" -> tester.testGithub(repo.getSecret(PrefKey.SEC_GH_PAT).orEmpty())
            "telegram" -> tester.testTelegram(
                repo.getSecret(PrefKey.SEC_TG_BOT_TOKEN).orEmpty(),
                repo.getPrefString(PrefKey.SEC_TG_CHAT_ID)
            )
            "upin" -> tester.testUpin(
                repo.getPrefString("upin_url", "http://127.0.0.1:8766"),
                repo.getSecret(PrefKey.SEC_UPIN_SECRET).orEmpty()
            )
            "termux" -> tester.checkTermux(
                repo.getPrefString(
                    "termux_bin",
                    "/data/data/com.termux/files/usr/bin/bash"
                )
            )
            else -> null
        }
    }

    private fun setOne(featureId: String, s: FeatureStatus) {
        _statuses.value = _statuses.value + (featureId to s)
    }

    companion object {
        /** Fitur yang dipetakan ke ConnectorId repo. */
        val FEATURE_CONNECTORS: Map<String, ConnectorId> = mapOf(
            "github" to ConnectorId.GITHUB,
            "telegram" to ConnectorId.TELEGRAM,
            "termux" to ConnectorId.TERMUX,
            "upin" to ConnectorId.UPIN,
            "serverless" to ConnectorId.SERVERLESS,
        )

        /** Fitur yang punya uji nyata di ConnectorTester. */
        val SUPPORTED_TESTS: Set<String> =
            (setOf("connection", "apikey", "model") + FEATURE_CONNECTORS.keys) - "serverless"
    }
}
