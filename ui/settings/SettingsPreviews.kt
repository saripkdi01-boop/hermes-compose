package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import id.sukiapps.hermes.ui.theme.HermesTheme
import id.sukiapps.hermes.ui.theme.LocalSettingPalette

/**
 * SettingsPreviews — @Preview light & dark untuk komponen KOMP Fase E
 * (Fase H: Tahap 7 — "@Preview light+dark" untuk setiap komponen).
 *
 * Hanya untuk Android Studio / CI lint — tidak dipakai saat runtime.
 * Semua warna dibaca dari LocalSettingPalette (tanpa hardcode).
 */

@Composable
private fun PreviewColumn(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    HermesTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Preview(name = "StatusChip Light", showBackground = true)
@Composable
private fun PreviewStatusChipsLight() {
    PreviewColumn(darkTheme = false) {
        FeatureStatusChip(status = FeatureStatus.Ready)
        FeatureStatusChip(status = FeatureStatus.Pending)
        FeatureStatusChip(status = FeatureStatus.Error("Base URL tidak valid"))
        FeatureStatusChip(status = FeatureStatus.Working)
    }
}

@Preview(name = "StatusChip Dark", showBackground = true)
@Composable
private fun PreviewStatusChipsDark() {
    PreviewColumn(darkTheme = true) {
        FeatureStatusChip(status = FeatureStatus.Ready)
        FeatureStatusChip(status = FeatureStatus.Error("API key ditolak (401)"))
    }
}

@Preview(name = "SettingsRow Light", showBackground = true)
@Composable
private fun PreviewSettingsRowLight() {
    PreviewColumn(darkTheme = false) {
        val palette = LocalSettingPalette.current
        SettingsRow(
            icon = Icons.Default.SmartToy,
            iconTint = palette.accent,
            title = "Model AI",
            subtitle = "gpt-4o-mini",
            status = FeatureStatus.Ready,
            onClick = {},
        )
        SettingsRow(
            icon = Icons.Default.Dns,
            iconTint = palette.statusError,
            title = "Koneksi",
            subtitle = "Server tidak bisa dijangkau",
            status = FeatureStatus.Error("Timeout 15 dtk"),
            onClick = {},
        )
    }
}

@Preview(name = "SettingsRow Dark", showBackground = true)
@Composable
private fun PreviewSettingsRowDark() {
    PreviewColumn(darkTheme = true) {
        val palette = LocalSettingPalette.current
        SettingsRow(
            icon = Icons.Default.Key,
            iconTint = palette.accent,
            title = "API Key",
            subtitle = "Tersimpan, belum diverifikasi",
            status = FeatureStatus.Pending,
            onClick = {},
        )
    }
}

@Preview(name = "TestButton States", showBackground = true)
@Composable
private fun PreviewTestButtonStates() {
    PreviewColumn(darkTheme = false) {
        TestButton(state = TestState.Idle, onTest = {})
        TestButton(state = TestState.Loading, onTest = {})
        TestButton(state = TestState.Success(142L), onTest = {})
        TestButton(state = TestState.Error("Waktu habis. Cek base URL / token."), onTest = {})
    }
}

@Preview(name = "SecretField + HoldToConfirm", showBackground = true)
@Composable
private fun PreviewSecretAndDanger() {
    PreviewColumn(darkTheme = false) {
        SecretField(
            hasValue = true,
            isDirty = false,
            onSave = {},
            onDelete = {},
            label = "API Key",
        )
        DangerZoneCard {
            HoldToConfirmButton(
                label = "Hapus API key",
                onConfirm = {},
            )
        }
    }
}

@Preview(name = "FeaturedCard Dark", showBackground = true)
@Composable
private fun PreviewFeaturedCardDark() {
    PreviewColumn(darkTheme = true) {
        SettingsFeaturedCard(
            modelName = "gpt-4o-mini",
            connectorsReady = 2,
            connectorsTotal = 5,
            apiKeySaved = true,
            overallStatus = FeatureStatus.Pending,
            setupProgress = 0.6f,
            setupLabel = "3 dari 4 wajib siap",
            onClick = {},
        )
    }
}
