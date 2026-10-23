package com.example.poswarnus.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.poswarnus.ui.components.EmptyCartGlyph
import com.example.poswarnus.ui.components.MinusGlyph
import com.example.poswarnus.ui.components.PlusGlyph
import com.example.poswarnus.ui.components.TrashGlyph
import com.example.poswarnus.ui.dashboard.formatRupiah
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.ErrorRed
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary

private val PanelControlShape = RoundedCornerShape(6.dp)
private val PanelTnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun CartPanel(
    state: CartUiState,
    onLineClick: (String) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRequestClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onCancelClear: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = state.lines.isEmpty()
    Row(modifier = modifier) {
        VerticalDivider(color = LineDefault, thickness = 1.dp)
        Column(modifier = Modifier.fillMaxHeight().weight(1f).background(SurfaceWhite)) {
            PanelHeader(showClear = !isEmpty, onRequestClear = onRequestClear)
            HorizontalDivider(color = LineDefault, thickness = 1.dp)
            if (isEmpty) {
                EmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(state.lines, key = { it.line.lineId }) { item ->
                        CartRow(item, onLineClick, onIncrement, onDecrement, onRemove)
                    }
                }
            }
            PanelFooter(state, isEmpty, onCheckout)
        }
    }

    if (state.showClearConfirm) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            shape = RoundedCornerShape(10.dp),
            containerColor = SurfaceWhite,
            title = { Text("Kosongkan pesanan?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = { Text("Semua item di pesanan ini akan dihapus.", fontSize = 15.sp, color = TextSecondary) },
            dismissButton = {
                TextButton(onClick = onCancelClear, shape = PanelControlShape) {
                    Text("Batal", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmClear, shape = PanelControlShape) {
                    Text("Kosongkan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                }
            },
        )
    }
}

@Composable
private fun PanelHeader(showClear: Boolean, onRequestClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Pesanan", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (showClear) {
            TextButton(onClick = onRequestClear, modifier = Modifier.heightIn(min = 44.dp), shape = PanelControlShape) {
                Text("Kosongkan", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(modifier = modifier.padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyCartGlyph(tint = TextSecondary, modifier = Modifier.size(44.dp))
            Text(
                text = "Belum ada pesanan",
                fontSize = 16.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Pilih menu di sebelah kiri untuk mulai menambahkan item.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CartRow(
    item: CartLineUi,
    onLineClick: (String) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val line = item.line
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLineClick(line.lineId) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    val summary = line.summary
                    if (summary.isNotEmpty()) {
                        Text(summary, fontSize = 13.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatRupiah(line.unitPrice), fontSize = 13.sp, color = TextSecondary, style = PanelTnum)
                    if (item.unavailable) {
                        Text("Menu tidak tersedia lagi", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ErrorRed)
                    }
                }
                Text(
                    formatRupiah(line.lineTotal),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    style = PanelTnum,
                    textAlign = TextAlign.End,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                RowButton("Hapus ${line.name}", { onRemove(line.lineId) }) { TrashGlyph(ErrorRed, Modifier.size(20.dp)) }
                RowButton("Kurangi ${line.name}", { onDecrement(line.lineId) }) { MinusGlyph(TextPrimary, Modifier.size(20.dp)) }
                Text(
                    line.quantity.toString(),
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    style = PanelTnum,
                )
                RowButton("Tambah ${line.name}", { onIncrement(line.lineId) }) { PlusGlyph(TextPrimary, Modifier.size(20.dp)) }
            }
        }
        HorizontalDivider(color = LineDefault)
    }
}

@Composable
private fun RowButton(description: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(PanelControlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

@Composable
private fun PanelFooter(state: CartUiState, isEmpty: Boolean, onCheckout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(SurfaceWhite)) {
        HorizontalDivider(color = LineStrong)
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("Subtotal", formatRupiah(state.subtotal))
            SummaryRow("Jumlah item", state.totalQuantity.toString())
            HorizontalDivider(color = LineDefault, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    formatRupiah(state.subtotal),
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    style = PanelTnum,
                )
            }
            Button(
                onClick = onCheckout,
                enabled = !isEmpty,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp),
                shape = PanelControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
            ) { Text("Bayar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, color = TextPrimary, style = PanelTnum)
    }
}
