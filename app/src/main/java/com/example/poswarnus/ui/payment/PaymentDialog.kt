package com.example.poswarnus.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.poswarnus.ui.cart.CartLine
import com.example.poswarnus.ui.components.ArrowBackGlyph
import com.example.poswarnus.ui.components.BackspaceGlyph
import com.example.poswarnus.ui.components.BrandMark
import com.example.poswarnus.ui.components.CashGlyph
import com.example.poswarnus.ui.components.CheckGlyph
import com.example.poswarnus.ui.dashboard.formatRupiah
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.AccentOrangeDark
import com.example.poswarnus.ui.theme.AccentTint
import com.example.poswarnus.ui.theme.BackgroundCream
import com.example.poswarnus.ui.theme.ErrorRed
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SuccessGreen
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary

private val ControlShape = RoundedCornerShape(6.dp)
private val Tnum = TextStyle(fontFeatureSettings = "tnum")
private val WideBreakpoint = 840.dp
private val SummaryWidth = 360.dp
private val RightColumnWidth = 380.dp
private val NumpadKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", "⌫")

/**
 * Full-screen cash payment (docs: Phase 4 design). Wide: order summary | total, cash and change |
 * quick amounts and numpad. Narrow: one scrolling column. The cash is typed on the in-app numpad,
 * so the system keyboard never opens. The Success state cannot be dismissed; only "Selesai" closes it.
 */
@Composable
fun PaymentDialog(
    state: PaymentUiState,
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onQuickAmount: (Long) -> Unit,
    onExact: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
) {
    if (state is PaymentUiState.Closed) return
    val isSuccess = state is PaymentUiState.Success
    Dialog(
        onDismissRequest = { if (!isSuccess) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSuccess,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundCream) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= WideBreakpoint
                when (state) {
                    is PaymentUiState.Paying ->
                        PayingScreen(wide, state, onDigits, onBackspace, onQuickAmount, onExact, onConfirm, onDismiss)
                    is PaymentUiState.Success -> SuccessScreen(state, onFinish)
                    PaymentUiState.Closed -> Unit
                }
            }
        }
    }
}

@Composable
private fun PayingScreen(
    wide: Boolean,
    state: PaymentUiState.Paying,
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onQuickAmount: (Long) -> Unit,
    onExact: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header()
        HorizontalDivider(color = LineDefault)
        if (wide) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SummaryPane(state, modifier = Modifier.width(SummaryWidth).fillMaxHeight())
                VerticalDivider(color = LineDefault)
                Row(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        MethodCard()
                        TotalBlock(state, modifier = Modifier.padding(top = 24.dp))
                        CashDisplay(state, modifier = Modifier.padding(top = 20.dp))
                        HorizontalDivider(color = LineDefault, modifier = Modifier.padding(vertical = 20.dp))
                        ChangeRow(state)
                    }
                    Column(modifier = Modifier.width(RightColumnWidth).verticalScroll(rememberScrollState())) {
                        SectionLabel("Nominal cepat")
                        QuickAmounts(state, onQuickAmount, onExact, modifier = Modifier.padding(top = 8.dp))
                        Numpad(state.isSaving, onDigits, onBackspace, keyHeight = 64.dp, modifier = Modifier.padding(top = 20.dp))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                TotalBlock(state)
                CashDisplay(state, modifier = Modifier.padding(top = 12.dp))
                ChangeRow(state, modifier = Modifier.padding(top = 12.dp))
                QuickAmounts(state, onQuickAmount, onExact, modifier = Modifier.padding(top = 16.dp))
                Numpad(state.isSaving, onDigits, onBackspace, keyHeight = 56.dp, modifier = Modifier.padding(top = 12.dp))
            }
        }
        BottomBar(wide, state, onConfirm, onDismiss)
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceWhite).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandMark(modifier = Modifier.size(40.dp), background = AccentOrange, foreground = Color.White)
        Text("Pembayaran", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun SummaryPane(state: PaymentUiState.Paying, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(SurfaceWhite)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ringkasan pesanan", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("${state.lines.sumOf { it.quantity }} item", fontSize = 14.sp, color = TextSecondary)
        }
        HorizontalDivider(color = LineDefault)
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.lines.forEach { line ->
                SummaryLine(line)
                HorizontalDivider(color = LineDefault)
            }
        }
        HorizontalDivider(color = LineStrong)
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("Subtotal", formatRupiah(state.totals.subtotal), bold = false)
            SummaryRow(state.totals.taxLine, formatRupiah(state.totals.taxAmount), bold = false)
            SummaryRow("Total", formatRupiah(state.totals.grandTotal), bold = true)
        }
    }
}

@Composable
private fun SummaryLine(line: CartLine) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(line.name, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(formatRupiah(line.lineTotal), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = Tnum)
        }
        if (line.summary.isNotEmpty()) {
            Text(line.summary, fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("${line.quantity} × ${formatRupiah(line.unitPrice)}", fontSize = 13.sp, color = TextSecondary, style = Tnum)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = if (bold) TextPrimary else TextSecondary)
        Text(
            value,
            fontSize = if (bold) 24.sp else 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) TextPrimary else TextSecondary,
            style = Tnum,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
}

/** Cash is the only method in this app; the card shows it as the selected one. */
@Composable
private fun MethodCard() {
    SectionLabel("Metode pembayaran")
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AccentTint)
            .border(2.dp, AccentOrange, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(AccentOrange),
            contentAlignment = Alignment.Center,
        ) { CashGlyph(tint = Color.White, modifier = Modifier.size(24.dp)) }
        Text("Tunai", modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AccentOrangeDark)
        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(AccentOrange), contentAlignment = Alignment.Center) {
            CheckGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TotalBlock(state: PaymentUiState.Paying, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel("Total tagihan")
        Text(
            formatRupiah(state.totals.grandTotal),
            modifier = Modifier.semantics { contentDescription = "Total tagihan ${formatRupiah(state.totals.grandTotal)}" },
            fontSize = 44.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            style = Tnum,
        )
    }
}

@Composable
private fun CashDisplay(state: PaymentUiState.Paying, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel("Uang diterima")
        val text = formatRupiah(state.cash)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(ControlShape)
                .background(SurfaceWhite)
                .border(1.dp, LineStrong, ControlShape)
                .padding(horizontal = 20.dp)
                .semantics { contentDescription = "Uang diterima $text" },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.cash == 0L) TextSecondary else TextPrimary,
                style = Tnum,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ChangeRow(state: PaymentUiState.Paying, modifier: Modifier = Modifier) {
    val short = state.cash > 0 && !state.canConfirm
    val label = if (short) "Uang kurang" else "Kembalian"
    val amount = formatRupiah(if (short) -state.change else if (state.canConfirm) state.change else 0)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label $amount" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
            Text(
                amount,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    short -> ErrorRed
                    state.canConfirm -> SuccessGreen
                    else -> TextSecondary
                },
                style = Tnum,
            )
        }
        if (state.error) {
            Text(
                "Gagal menyimpan transaksi. Coba lagi.",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ErrorRed,
            )
        }
    }
}

@Composable
private fun QuickAmounts(
    state: PaymentUiState.Paying,
    onQuickAmount: (Long) -> Unit,
    onExact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exact = state.totals.grandTotal
    val amounts = listOf(exact) + state.quickAmounts
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        amounts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { amount ->
                    val isExact = amount == exact
                    QuickChip(
                        label = formatRupiah(amount),
                        caption = if (isExact) "Uang pas" else null,
                        selected = state.cash == amount,
                        enabled = !state.isSaving,
                        onClick = { if (isExact) onExact() else onQuickAmount(amount) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    caption: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (selected) AccentTint else SurfaceWhite)
            .border(if (selected) 2.dp else 1.dp, if (selected) AccentOrange else LineStrong, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (selected) AccentOrangeDark else TextPrimary, style = Tnum)
        if (caption != null) Text(caption, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun Numpad(
    disabled: Boolean,
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    keyHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NumpadKeys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val shape = RoundedCornerShape(8.dp)
                    val isBackspace = key == "⌫"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .clip(shape)
                            .background(SurfaceWhite)
                            .border(1.dp, LineDefault, shape)
                            .clickable(enabled = !disabled, role = Role.Button) {
                                if (isBackspace) onBackspace() else onDigits(key)
                            }
                            .semantics { contentDescription = if (isBackspace) "Hapus" else key },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isBackspace) {
                            BackspaceGlyph(tint = TextPrimary, modifier = Modifier.size(28.dp))
                        } else {
                            Text(key, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = TextPrimary, style = Tnum)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(wide: Boolean, state: PaymentUiState.Paying, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(SurfaceWhite)) {
        HorizontalDivider(color = LineDefault)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !state.isSaving,
                modifier = Modifier.heightIn(min = 56.dp),
                shape = ControlShape,
                border = BorderStroke(1.dp, LineStrong),
            ) {
                ArrowBackGlyph(tint = TextPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kembali", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            if (wide) Spacer(Modifier.weight(1f))
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm && !state.isSaving,
                modifier = (if (wide) Modifier.widthIn(min = 420.dp) else Modifier.weight(1f)).heightIn(min = 56.dp),
                shape = ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(if (wide) "Konfirmasi Pembayaran" else "Konfirmasi", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SuccessScreen(state: PaymentUiState.Success, onFinish: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = SurfaceWhite,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text("Pembayaran berhasil", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                SuccessRow("Total", formatRupiah(state.totals.grandTotal), modifier = Modifier.padding(top = 16.dp))
                SuccessRow("Uang diterima", formatRupiah(state.cash), modifier = Modifier.padding(top = 4.dp))
                HorizontalDivider(color = LineDefault, modifier = Modifier.padding(vertical = 12.dp))
                Text("Kembalian", fontSize = 14.sp, color = TextSecondary)
                Text(
                    formatRupiah(state.change),
                    modifier = Modifier.semantics { contentDescription = "Kembalian ${formatRupiah(state.change)}" },
                    fontSize = 44.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    style = Tnum,
                )
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 20.dp),
                    shape = ControlShape,
                    border = BorderStroke(1.dp, LineDefault),
                ) { Text("Cetak struk (segera hadir)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 12.dp),
                    shape = ControlShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                ) { Text("Selesai", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun SuccessRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp, color = TextSecondary)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = Tnum, textAlign = TextAlign.End)
    }
}
