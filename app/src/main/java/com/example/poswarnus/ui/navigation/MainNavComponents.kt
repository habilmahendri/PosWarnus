package com.example.poswarnus.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.poswarnus.ui.components.BrandMark
import com.example.poswarnus.ui.components.HistoryGlyph
import com.example.poswarnus.ui.components.LogoutGlyph
import com.example.poswarnus.ui.components.SettingsGlyph
import com.example.poswarnus.ui.components.StorefrontGlyph
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.AccentOrangeDark
import com.example.poswarnus.ui.theme.AccentTint
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary

private const val LogoutLabel = "Keluar"
private val RailBaseWidth = 72.dp
private const val RailMaxFontScale = 1.5f

@Composable
private fun DestinationGlyph(destination: MainDestination, tint: Color, modifier: Modifier) {
    when (destination) {
        MainDestination.KASIR -> StorefrontGlyph(tint = tint, modifier = modifier)
        MainDestination.RIWAYAT -> HistoryGlyph(tint = tint, modifier = modifier)
        MainDestination.PENGATURAN -> SettingsGlyph(tint = tint, modifier = modifier)
    }
}

@Composable
fun MainNavRail(
    current: MainDestination?,
    onSelect: (MainDestination) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val railWidth = RailBaseWidth * LocalDensity.current.fontScale.coerceIn(1f, RailMaxFontScale)
    Row(
        modifier = modifier
            .background(SurfaceWhite)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
            )
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BrandMark(background = AccentOrange, foreground = SurfaceWhite, modifier = Modifier.size(32.dp))
            Spacer(Modifier.size(12.dp))
            MainDestination.entries.forEach { destination ->
                RailItem(
                    label = destination.label,
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    role = Role.Tab,
                ) { tint -> DestinationGlyph(destination, tint, Modifier.size(24.dp)) }
            }
            Spacer(Modifier.weight(1f))
            RailItem(label = LogoutLabel, selected = false, onClick = onLogout, role = Role.Button) { tint ->
                LogoutGlyph(tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(LineDefault),
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role,
    glyph: @Composable (Color) -> Unit,
) {
    val tint = if (selected) AccentOrangeDark else TextSecondary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) AccentTint else Color.Transparent, RoundedCornerShape(10.dp))
            .selectable(selected = selected, onClick = onClick, role = role)
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        glyph(tint)
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
fun MainNavDrawerContent(
    current: MainDestination?,
    onSelect: (MainDestination) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SurfaceWhite)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(background = AccentOrange, foreground = SurfaceWhite, modifier = Modifier.size(32.dp))
        }
        MainDestination.entries.forEach { destination ->
            DrawerRow(
                label = destination.label,
                selected = destination == current,
                onClick = { onSelect(destination) },
                role = Role.Tab,
            ) { tint -> DestinationGlyph(destination, tint, Modifier.size(24.dp)) }
        }
        Spacer(Modifier.size(8.dp))
        DrawerRow(label = LogoutLabel, selected = false, onClick = onLogout, role = Role.Button) { tint ->
            LogoutGlyph(tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun DrawerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role,
    glyph: @Composable (Color) -> Unit,
) {
    val tint = if (selected) AccentOrangeDark else TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentTint else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = role)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        glyph(tint)
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
fun LogoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(10.dp),
        containerColor = SurfaceWhite,
        title = { Text("Keluar?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
        text = {
            Text(
                "Pesanan yang belum dibayar akan dihapus.",
                fontSize = 15.sp,
                color = TextSecondary,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(LogoutLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
            }
        },
    )
}
