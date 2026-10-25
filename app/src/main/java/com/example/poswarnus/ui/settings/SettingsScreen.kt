package com.example.poswarnus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poswarnus.ui.components.ArrowBackGlyph
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinDialog.pinInput,
                        onValueChange = viewModel::onPinInputChange,
                        singleLine = true,
                        enabled = !pinDialog.submitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinDialog.error != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    pinDialog.error?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
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
                    Text("Nyalakan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                }
            },
        )
    }
}
