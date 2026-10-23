package com.example.poswarnus.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.poswarnus.ui.dashboard.formatRupiah
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.ErrorRed
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SuccessGreen
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary

private val ControlShape = RoundedCornerShape(6.dp)
private val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** Cash payment over the dashboard. Success cannot be dismissed; only "Selesai" ([onFinish]) closes it. */
@Composable
fun PaymentDialog(
    state: PaymentUiState,
    onCashInput: (String) -> Unit,
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
            dismissOnClickOutside = !isSuccess,
            usePlatformDefaultWidth = false,
            // Lets imePadding() below shrink the card above the keyboard instead of hiding behind it.
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight - 32.dp),
                shape = RoundedCornerShape(10.dp),
                color = SurfaceWhite,
                shadowElevation = 16.dp,
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    when (state) {
                        is PaymentUiState.Paying -> PayingContent(state, onCashInput, onQuickAmount, onExact, onConfirm, onDismiss)
                        is PaymentUiState.Success -> SuccessContent(state, onFinish)
                        PaymentUiState.Closed -> Unit
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PayingContent(
    state: PaymentUiState.Paying,
    onCashInput: (String) -> Unit,
    onQuickAmount: (Long) -> Unit,
    onExact: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text("Pembayaran", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    Text("Total", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(top = 16.dp))
    Text(
        formatRupiah(state.totals.grandTotal),
        modifier = Modifier.semantics { contentDescription = "Total ${formatRupiah(state.totals.grandTotal)}" },
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        style = Tnum,
    )

    // The text is re-formatted on every keystroke, so the caret is pinned to the end; otherwise it
    // stays where the old, shorter text had it and the next digit lands mid-number.
    val cashText = if (state.cash == 0L) "" else formatRupiah(state.cash)
    OutlinedTextField(
        value = TextFieldValue(cashText, selection = TextRange(cashText.length)),
        onValueChange = { onCashInput(it.text) },
        label = { Text("Uang diterima") },
        placeholder = { Text("Rp0") },
        singleLine = true,
        enabled = !state.isSaving,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = Tnum.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickChip("Uang pas", enabled = !state.isSaving, onClick = onExact)
        state.quickAmounts.forEach { amount ->
            QuickChip(formatRupiah(amount), enabled = !state.isSaving, onClick = { onQuickAmount(amount) })
        }
    }

    val short = state.cash > 0 && !state.canConfirm
    val changeText = when {
        short -> "Uang kurang ${formatRupiah(-state.change)}"
        state.canConfirm -> "Kembalian ${formatRupiah(state.change)}"
        else -> "Kembalian Rp0"
    }
    Text(
        changeText,
        modifier = Modifier.padding(top = 16.dp).semantics { contentDescription = changeText },
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (short) ErrorRed else TextPrimary,
        style = Tnum,
    )
    if (state.error) {
        Text(
            "Gagal menyimpan transaksi. Coba lagi.",
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = ErrorRed,
        )
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onDismiss,
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = ControlShape,
            border = BorderStroke(1.dp, LineStrong),
        ) { Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }
        Button(
            onClick = onConfirm,
            enabled = state.canConfirm && !state.isSaving,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = ControlShape,
            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
                Text("Konfirmasi", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QuickChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = ControlShape,
        border = BorderStroke(1.dp, LineStrong),
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = Tnum) }
}

@Composable
private fun SuccessContent(state: PaymentUiState.Success, onFinish: () -> Unit) {
    Text("Pembayaran berhasil", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
    SuccessRow("Total", formatRupiah(state.totals.grandTotal), modifier = Modifier.padding(top = 16.dp))
    SuccessRow("Uang diterima", formatRupiah(state.cash))
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

@Composable
private fun SuccessRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp, color = TextSecondary)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = Tnum, textAlign = TextAlign.End)
    }
}
