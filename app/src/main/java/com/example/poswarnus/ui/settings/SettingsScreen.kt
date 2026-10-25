package com.example.poswarnus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poswarnus.ui.components.ArrowBackGlyph
import com.example.poswarnus.ui.components.BackspaceGlyph
import com.example.poswarnus.ui.components.TrainingBanner
import com.example.poswarnus.ui.navigation.isWideWindow
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.BackgroundCream
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCream)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (state.trainingEnabled) TrainingBanner()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(SurfaceWhite)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isWideWindow()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Kembali" },
                ) {
                    ArrowBackGlyph(tint = TextPrimary, modifier = Modifier.size(24.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "Pengaturan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        HorizontalDivider(color = LineDefault, thickness = 1.dp)

        // The whole row toggles, so the 48dp+ row is the touch target rather than the small switch.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceWhite)
                .toggleable(
                    value = state.trainingEnabled,
                    role = Role.Switch,
                    onValueChange = viewModel::onTrainingToggle,
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mode Pelatihan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = "Latihan tanpa menyimpan transaksi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            Switch(
                checked = state.trainingEnabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AccentOrange,
                    checkedThumbColor = SurfaceWhite,
                    uncheckedTrackColor = SurfaceWhite,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedBorderColor = LineStrong,
                ),
            )
        }
        HorizontalDivider(color = LineDefault, thickness = 1.dp)
    }

    state.pinDialog?.let { pinDialog ->
        AlertDialog(
            onDismissRequest = { if (!pinDialog.submitting) viewModel.onPinCancel() },
            shape = RoundedCornerShape(10.dp),
            containerColor = SurfaceWhite,
            title = { Text("Masukkan PIN Pemilik", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = {
                Column {
                    Text("Pesanan saat ini akan dikosongkan.", fontSize = 15.sp, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    PinDisplay(digitCount = pinDialog.pinInput.length, isError = pinDialog.error != null)
                    pinDialog.error?.let {
                        Text(
                            it,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    PinKeypad(
                        disabled = pinDialog.submitting,
                        onDigit = { digit -> viewModel.onPinInputChange(pinDialog.pinInput + digit) },
                        onBackspace = { viewModel.onPinInputChange(pinDialog.pinInput.dropLast(1)) },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onPinCancel, enabled = !pinDialog.submitting, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::onPinSubmit,
                    enabled = !pinDialog.submitting && pinDialog.pinInput.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    if (pinDialog.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentOrange,
                        )
                    } else {
                        Text("Nyalakan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                    }
                }
            },
        )
    }
}

/** Bordered box showing the PIN as masked dots, one per digit typed — never the digits themselves. */
@Composable
private fun PinDisplay(digitCount: Int, isError: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (isError) MaterialTheme.colorScheme.error else LineStrong
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = if (digitCount == 0) "PIN kosong" else "PIN terisi $digitCount digit"
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(digitCount) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(TextPrimary))
            }
        }
    }
}

private val PinKeypadKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")

/** Numeric keypad for the PIN dialog, built in-app so the PIN is never typed through the system keyboard. */
@Composable
private fun PinKeypad(disabled: Boolean, onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PinKeypadKeys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f).height(48.dp))
                    } else {
                        val shape = RoundedCornerShape(8.dp)
                        val isBackspace = key == "⌫"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(shape)
                                .background(SurfaceWhite)
                                .border(1.dp, LineDefault, shape)
                                .clickable(enabled = !disabled, role = Role.Button) {
                                    if (isBackspace) onBackspace() else onDigit(key)
                                }
                                .semantics { contentDescription = if (isBackspace) "Hapus" else key },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBackspace) {
                                BackspaceGlyph(tint = TextPrimary, modifier = Modifier.size(22.dp))
                            } else {
                                Text(key, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
