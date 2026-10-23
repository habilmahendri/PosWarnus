package com.example.poswarnus.ui.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.Option
import com.example.poswarnus.data.catalog.OptionGroup
import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.catalog.SelectionMode
import com.example.poswarnus.ui.components.CheckGlyph
import com.example.poswarnus.ui.components.CloseGlyph
import com.example.poswarnus.ui.components.MinusGlyph
import com.example.poswarnus.ui.components.PlusGlyph
import com.example.poswarnus.ui.dashboard.formatRupiah
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.AccentOrangeDark
import com.example.poswarnus.ui.theme.AccentTint
import com.example.poswarnus.ui.theme.ErrorRed
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary

private val ControlShape = RoundedCornerShape(6.dp)
private val SideColumnBackground = Color(0xFFFAF9F6)
private val Tnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun MenuDetailDialog(
    state: DialogUiState,
    onToggle: (groupId: String, optionId: String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 600.dp
            val shape = RoundedCornerShape(10.dp)
            val sizeModifier = if (wide) {
                Modifier.size(minOf(840.dp, maxWidth - 32.dp), minOf(660.dp, maxHeight - 32.dp))
            } else {
                Modifier.fillMaxSize().padding(16.dp)
            }
            Surface(modifier = sizeModifier, shape = shape, color = SurfaceWhite, shadowElevation = 16.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (wide) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .width(296.dp)
                                    .fillMaxSize()
                                    .background(SideColumnBackground)
                                    .verticalScroll(rememberScrollState()),
                            ) { ItemHeader(state.item, 4f / 3f) }
                            VerticalLine()
                            Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                                TitleBar(onDismiss)
                                Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                                    OptionsBody(state.draft, onToggle, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp))
                                }
                            }
                        }
                    } else {
                        TitleBar(onDismiss)
                        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            ItemHeader(state.item, 16f / 9f)
                            OptionsBody(state.draft, onToggle, Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp))
                        }
                    }
                    Footer(state, wide, onIncrement, onDecrement, onConfirm, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun VerticalLine() {
    Box(modifier = Modifier.width(1.dp).fillMaxSize().background(LineDefault))
}

@Composable
private fun TitleBar(onDismiss: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Atur pesanan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ControlShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "Tutup" },
                contentAlignment = Alignment.Center,
            ) { CloseGlyph(tint = TextPrimary, modifier = Modifier.size(20.dp)) }
        }
        HorizontalDivider(color = LineDefault)
    }
}

@Composable
private fun ItemHeader(item: MenuItem, imageRatio: Float) {
    val imageModifier = Modifier.fillMaxWidth().aspectRatio(imageRatio)
    if (item.imageUrl != null) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
        )
    } else {
        Box(modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item.name, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        item.description?.let {
            Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = TextSecondary)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Harga dasar", fontSize = 14.sp, color = TextSecondary)
            Text(formatRupiah(item.price), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, style = Tnum)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsBody(draft: OrderDraft?, onToggle: (String, String) -> Unit, modifier: Modifier) {
    if (draft == null) {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AccentOrange, strokeWidth = 3.dp)
        }
        return
    }
    Column(modifier = modifier) {
        draft.groups.sortedBy { it.sortOrder }.forEach { group ->
            val chosen = draft.selected[group.id].orEmpty()
            Column(modifier = Modifier.padding(top = 20.dp)) {
                GroupHeader(group)
                if (group.kind == OptionKind.VARIANT) {
                    val variants = group.options.sortedBy { it.sortOrder }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                    ) {
                        variants.forEach { option ->
                            VariantCard(draft.variantPrice(option), option, option.id in chosen, Modifier.weight(1f).widthIn(min = 96.dp)) {
                                onToggle(group.id, option.id)
                            }
                        }
                    }
                } else {
                    HorizontalDivider(color = LineDefault, modifier = Modifier.padding(top = 2.dp))
                    group.options.sortedBy { it.sortOrder }.forEach { option ->
                        ModifierRow(option, group.selection, option.id in chosen) { onToggle(group.id, option.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: OptionGroup) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(group.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        if (group.isRequired) {
            Text(
                "Wajib",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentOrangeDark,
                modifier = Modifier.background(AccentTint, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else {
            Text("Opsional", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
        }
        Text(
            if (group.selection == SelectionMode.MULTI) "Boleh lebih dari satu" else "Pilih 1",
            fontSize = 13.sp,
            color = TextSecondary,
        )
    }
}

private fun deltaText(delta: Long): String = if (delta != 0L) "+${formatRupiah(delta)}" else ""

@Composable
private fun VariantCard(price: Long, option: Option, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border = if (selected) BorderStroke(2.dp, AccentOrange) else BorderStroke(1.dp, LineStrong)
    Box(
        modifier = modifier
            .heightIn(min = 76.dp)
            .clip(ControlShape)
            .background(if (selected) AccentTint else SurfaceWhite)
            .border(border, ControlShape)
            .selectable(selected = selected, enabled = option.isAvailable, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().alpha(if (option.isAvailable) 1f else 0.4f).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(option.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(formatRupiah(price), fontSize = 15.sp, color = TextPrimary, style = Tnum)
            val third = if (selected) "Dipilih" else deltaText(option.priceDelta)
            if (third.isNotEmpty()) {
                Text(third, fontSize = 13.sp, color = if (selected) AccentOrangeDark else TextSecondary, style = Tnum)
            }
        }
        if (!option.isAvailable) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { HabisTag() }
        }
    }
}

@Composable
private fun HabisTag() {
    Text("Habis", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
}

@Composable
private fun ModifierRow(option: Option, mode: SelectionMode, selected: Boolean, onClick: () -> Unit) {
    val enabled = option.isAvailable
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)
        .let {
            if (mode == SelectionMode.MULTI) {
                it.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
            } else {
                it.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            }
        }
        .padding(horizontal = 4.dp, vertical = 4.dp)
    Column {
        Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Indicator(selected, if (mode == SelectionMode.MULTI) ControlShape else CircleShape)
                Text(
                    option.name,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (enabled) {
                    Text(deltaText(option.priceDelta), fontSize = 15.sp, color = TextSecondary, style = Tnum, textAlign = TextAlign.End)
                }
            }
            if (!enabled) HabisTag()
        }
        HorizontalDivider(color = LineDefault)
    }
}

@Composable
private fun Indicator(selected: Boolean, shape: Shape) {
    val base = Modifier.size(24.dp).clip(shape)
    Box(
        modifier = if (selected) base.background(AccentOrange) else base.background(SurfaceWhite).border(2.dp, LineStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            if (shape == CircleShape) {
                Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
            } else {
                CheckGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Footer(
    state: DialogUiState,
    wide: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = state.draft
    val isEdit = state.target is DialogTarget.Edit
    val confirmLabel = if (isEdit) "Simpan Perubahan" else "Tambah ke Pesanan"
    val stepper: @Composable () -> Unit = { Stepper(draft?.quantity ?: 1, draft != null, onIncrement, onDecrement) }
    val summary: @Composable (Modifier) -> Unit = { m -> TotalSummary(draft, m) }
    val cancel: @Composable (Modifier) -> Unit = { m ->
        OutlinedButton(
            onClick = onDismiss,
            modifier = m.heightIn(min = 48.dp),
            shape = ControlShape,
            border = BorderStroke(1.dp, LineStrong),
        ) { Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }
    }
    val confirm: @Composable (Modifier) -> Unit = { m ->
        Button(
            onClick = onConfirm,
            enabled = draft != null && draft.canSubmit,
            modifier = m.heightIn(min = 48.dp),
            shape = ControlShape,
            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
        ) { Text(confirmLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
    Column(modifier = Modifier.fillMaxWidth().background(SurfaceWhite)) {
        HorizontalDivider(color = LineDefault)
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                stepper()
                summary(Modifier.weight(1f))
                cancel(Modifier)
                confirm(Modifier)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    stepper()
                    summary(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    cancel(Modifier.weight(1f))
                    confirm(Modifier.weight(2f))
                }
            }
        }
    }
}

@Composable
private fun TotalSummary(draft: OrderDraft?, modifier: Modifier) {
    val missing = draft?.missingRequiredGroups.orEmpty()
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (missing.isNotEmpty()) {
            Text("Pilih ${missing.joinToString(", ") { it.name }}", fontSize = 13.sp, color = ErrorRed, textAlign = TextAlign.End)
        } else {
            Text("Total", fontSize = 13.sp, color = TextSecondary)
        }
        Text(
            text = if (draft != null) formatRupiah(draft.lineTotal) else "-",
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            style = Tnum,
        )
    }
}

@Composable
private fun Stepper(quantity: Int, enabled: Boolean, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("Kurangi jumlah", enabled && quantity > 1, onDecrement) { MinusGlyph(it, Modifier.size(20.dp)) }
        Text(
            quantity.toString(),
            modifier = Modifier.widthIn(min = 40.dp),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            style = Tnum,
        )
        StepButton("Tambah jumlah", enabled, onIncrement) { PlusGlyph(it, Modifier.size(20.dp)) }
    }
}

@Composable
private fun StepButton(description: String, enabled: Boolean, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    val tint = if (enabled) TextPrimary else LineStrong
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(ControlShape)
            .border(1.dp, LineStrong, ControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { glyph(tint) }
}
