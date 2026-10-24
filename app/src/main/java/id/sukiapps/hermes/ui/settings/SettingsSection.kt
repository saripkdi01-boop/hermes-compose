package id.sukiapps.hermes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sukiapps.hermes.ui.theme.LocalSettingPalette
import id.sukiapps.hermes.ui.theme.LocalSettingsDensity

/**
 * Komponen pengantar Pengaturan v15 — dipakai bersama oleh semua section
 * hasil split dari SettingsScreen.kt. Semua ukuran mengikuti
 * LocalSettingsDensity (compact default) supaya layar tetap padat.
 */

/** Teks bantuan kecil di bawah field / tombol. */
@Composable
fun SettingsHelperText(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalSettingPalette.current
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = palette.textSecondary,
        modifier = modifier.fillMaxWidth()
    )
}

/** Sub-judul di dalam section (mis. "Termux", "Supabase & Vercel"). */
@Composable
fun SettingsSubHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalSettingPalette.current
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = palette.textTertiary,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Slider padat: label + nilai rata pada satu baris, slider menyatu ke tepi.
 * Tinggi track ramping (24dp) agar hemat ruang vertikal.
 */
@Composable
fun SettingsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSettingPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent
            )
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.border
            )
        )
    }
}

/**
 * Baris toggle padat: judul (+ subjudul opsional) di kiri, switch di kanan.
 * Tinggi minimal mengikuti density.touchTarget agar target sentuh aman.
 * Warna switch diperbaiki supaya tidak "hantu" di tema terang maupun gelap.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val palette = LocalSettingPalette.current
    val density = LocalSettingsDensity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = density.touchTarget)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = palette.textPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = palette.textSecondary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.textOnAccent,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.surfaceVariant,
                uncheckedBorderColor = palette.border
            )
        )
    }
}

/**
 * Judul grup besar di LazyColumn: huruf kapital, rapat, warna redup.
 * Mengganti judul "Card" lama yang memakan ~28dp per grup.
 */
@Composable
fun SettingsSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalSettingPalette.current
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = palette.textTertiary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 6.dp)
    )
}

/** Baris dua tombol aksi (kiri tonal, kanan filled) dengan tinggi 48dp. */
@Composable
fun SettingsActionRow(
    leftLabel: String,
    onLeftClick: () -> Unit,
    rightLabel: String,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftEnabled: Boolean = true,
    rightEnabled: Boolean = true
) {
    ActionBarPair(
        leftLabel = leftLabel,
        onLeftClick = onLeftClick,
        rightLabel = rightLabel,
        onRightClick = onRightClick,
        leftEnabled = leftEnabled,
        rightEnabled = rightEnabled,
        modifier = modifier
    )
}

/** Baris informasi status tunggal (mis. "Diperbarui: ...", "Terhubung ✓"). */
@Composable
fun SettingsStatusText(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val palette = LocalSettingPalette.current
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = if (isError) palette.statusError else palette.textSecondary,
        modifier = modifier.fillMaxWidth()
    )
}

/** Vertikal gap konsisten antar item di dalam satu section. */
@Composable
fun SettingsFieldGap() {
    Spacer(Modifier.height(LocalSettingsDensity.current.fieldGap))
}

/** Vertikal gap kecil antar tombol/aksi. */
@Composable
fun SettingsItemGap() {
    Spacer(Modifier.height(LocalSettingsDensity.current.itemGap))
}

/** Dipakai MaterialTheme hanya untuk tipografi default di dialog turunan. */
internal fun settingsThemeTypography() = MaterialTheme.typography
