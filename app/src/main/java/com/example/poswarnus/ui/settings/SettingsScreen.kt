package com.example.poswarnus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poswarnus.ui.components.ArrowBackGlyph
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(SurfaceWhite)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Kembali" },
            ) {
                ArrowBackGlyph(tint = TextPrimary, modifier = Modifier.size(24.dp))
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
                .clickable { viewModel.onTrainingToggle(!state.trainingEnabled) }
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
                onCheckedChange = viewModel::onTrainingToggle,
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

    if (state.confirmingEnable) {
        AlertDialog(
            onDismissRequest = viewModel::cancelEnable,
            shape = RoundedCornerShape(10.dp),
            containerColor = SurfaceWhite,
            title = { Text("Nyalakan Mode Pelatihan?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = {
                Text(
                    "Pesanan saat ini akan dikosongkan dan penjualan tidak disimpan.",
                    fontSize = 15.sp,
                    color = TextSecondary,
                )
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelEnable, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEnable, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Nyalakan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                }
            },
        )
    }
}
