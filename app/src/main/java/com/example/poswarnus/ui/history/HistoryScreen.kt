package com.example.poswarnus.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poswarnus.data.transaction.HistoryEntry
import com.example.poswarnus.data.transaction.HistoryLine
import com.example.poswarnus.ui.components.ArrowBackGlyph
import com.example.poswarnus.ui.components.SearchGlyph
import com.example.poswarnus.ui.components.TrainingBanner
import com.example.poswarnus.ui.dashboard.formatRupiah
import com.example.poswarnus.ui.navigation.isWideWindow
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.AccentOrangeDark
import com.example.poswarnus.ui.theme.AccentTint
import com.example.poswarnus.ui.theme.BackgroundCream
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SuccessGreen
import com.example.poswarnus.ui.theme.SuccessTint
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary
import com.example.poswarnus.ui.training.TrainingBannerViewModel

private val ListPaneWidth = 520.dp
private val PanelShape = RoundedCornerShape(6.dp)

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
    trainingViewModel: TrainingBannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val training by trainingViewModel.enabled.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCream)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = isWideWindow()
        val showingDetailOnly = !wide && state.detailOpen && state.selected != null

        BackHandler(enabled = showingDetailOnly) { viewModel.onCloseDetail() }

        Column(modifier = Modifier.fillMaxSize()) {
            if (training) TrainingBanner()
            HistoryTopBar(
                summary = state.summary,
                showBack = !wide,
                onBack = if (showingDetailOnly) viewModel::onCloseDetail else onBack,
            )
            HorizontalDivider(color = LineDefault, thickness = 1.dp)
            when {
                wide -> Row(modifier = Modifier.fillMaxSize()) {
                    ListPane(
                        state = state,
                        wide = true,
                        viewModel = viewModel,
                        modifier = Modifier
                            .width(ListPaneWidth)
                            .fillMaxHeight()
                            .background(SurfaceWhite)
                            .border(1.dp, LineDefault),
                    )
                    DetailPane(
                        selected = state.selected,
                        selectedWhen = state.selectedWhen,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
                    )
                }
                showingDetailOnly -> DetailPane(
                    selected = state.selected,
                    selectedWhen = state.selectedWhen,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
                else -> ListPane(
                    state = state,
                    wide = false,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize().background(SurfaceWhite),
                )
            }
        }
    }
}

@Composable
private fun HistoryTopBar(summary: String, showBack: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(SurfaceWhite)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Kembali ke kasir" },
            ) {
                ArrowBackGlyph(tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "Riwayat transaksi",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(
            text = summary,
            fontSize = 14.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun ListPane(
    state: HistoryUiState,
    wide: Boolean,
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayTabs(state.day, viewModel::onDayChange)
                Spacer(Modifier.weight(1f))
                SearchField(state.query, viewModel::onQueryChange, Modifier.width(240.dp))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DayTabs(state.day, viewModel::onDayChange)
                SearchField(state.query, viewModel::onQueryChange, Modifier.fillMaxWidth())
            }
        }
        HorizontalDivider(color = LineDefault, thickness = 1.dp)
        when {
            state.rows.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.id }) { row ->
                    HistoryRowItem(
                        row = row,
                        highlighted = wide && row.id == state.selected?.id,
                        onClick = { viewModel.onSelect(row.id) },
                    )
                    HorizontalDivider(color = LineDefault, thickness = 1.dp)
                }
            }
            state.dayIsEmpty -> EmptyState(
                title = "Belum ada transaksi",
                hint = if (state.day == HistoryDay.TODAY) {
                    "Penjualan hari ini akan muncul di sini."
                } else {
                    "Penjualan kemarin akan muncul di sini."
                },
            )
            else -> EmptyState(
                title = "Transaksi tidak ditemukan",
                hint = "Coba nomor pesanan atau nama menu lain.",
            )
        }
    }
}

@Composable
private fun DayTabs(day: HistoryDay, onChange: (HistoryDay) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayTab("Hari ini", day == HistoryDay.TODAY) { onChange(HistoryDay.TODAY) }
        DayTab("Kemarin", day == HistoryDay.YESTERDAY) { onChange(HistoryDay.YESTERDAY) }
    }
}

@Composable
private fun DayTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) AccentOrange else SurfaceWhite)
            .border(1.dp, if (selected) AccentOrange else LineStrong, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
        cursorBrush = SolidColor(AccentOrange),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Cari transaksi" },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(SurfaceWhite, shape)
                    .border(1.dp, LineStrong, shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchGlyph(tint = TextSecondary, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("No. pesanan atau menu", fontSize = 15.sp, color = TextSecondary, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun HistoryRowItem(row: HistoryRow, highlighted: Boolean, onClick: () -> Unit) {
    val description = "${row.code}, pukul ${row.timeText}, ${row.itemCount} item, ${formatRupiah(row.grandTotal)}" +
        if (row.isPending) ", menunggu sinkron" else ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) AccentTint else SurfaceWhite)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = highlighted
            }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = row.code,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) AccentOrangeDark else TextPrimary,
            )
            if (row.isPending) PendingChip()
            Spacer(Modifier.weight(1f))
            Text(
                text = formatRupiah(row.grandTotal),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
        Text(
            text = "${row.timeText} · ${row.itemCount} item",
            fontSize = 13.sp,
            color = TextSecondary,
        )
        Text(
            text = row.itemNames,
            fontSize = 13.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(title: String, hint: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center)
        Text(hint, fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatusChip(text: String, background: Color, content: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .background(background, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun PendingChip() = StatusChip("Menunggu sinkron", AccentTint, AccentOrangeDark)

@Composable
private fun DetailPane(selected: HistoryEntry?, selectedWhen: String, modifier: Modifier = Modifier) {
    if (selected == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Pilih transaksi untuk melihat rincian.", fontSize = 15.sp, color = TextSecondary)
        }
        return
    }
    val description = "Rincian ${selected.code}, $selectedWhen, total ${formatRupiah(selected.grandTotal)}"
    Column(
        modifier = modifier
            .background(SurfaceWhite, PanelShape)
            .border(1.dp, LineDefault, PanelShape)
            .semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = selected.code,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(selectedWhen, fontSize = 14.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Selesai", SuccessTint, SuccessGreen)
                if (selected.isPending) PendingChip()
            }
            HorizontalDivider(color = LineDefault, thickness = 1.dp)
            selected.lines.forEach { LineRow(it) }
            HorizontalDivider(color = LineDefault, thickness = 1.dp)
            TotalRow("Subtotal", formatRupiah(selected.subtotal))
            TotalRow("${selected.taxLabel} ${formatPercent(selected.taxBasisPoints)}%", formatRupiah(selected.taxAmount))
            TotalRow("Jumlah item", selected.itemCount.toString())
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(formatRupiah(selected.grandTotal), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            TotalRow("Tunai diterima", formatRupiah(selected.cashReceived))
            TotalRow("Kembalian", formatRupiah(selected.changeAmount))
        }
        HorizontalDivider(color = LineDefault, thickness = 1.dp)
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = LineDefault,
                    disabledContentColor = TextSecondary,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Cetak Ulang Struk (segera hadir)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LineRow(line: HistoryLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (line.meta.isNotEmpty()) Text(line.meta, fontSize = 13.sp, color = TextSecondary)
            Text("${line.quantity} × ${formatRupiah(line.unitPrice)}", fontSize = 13.sp, color = TextSecondary)
        }
        Text(formatRupiah(line.subtotal), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 15.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = TextPrimary)
    }
}

/** 1000 basis points -> "10", 750 -> "7,5" (Indonesian decimal comma). */
private fun formatPercent(basisPoints: Int): String {
    val whole = basisPoints / 100
    val frac = basisPoints % 100
    return when {
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole,${frac / 10}"
        else -> "$whole,${frac.toString().padStart(2, '0')}"
    }
}
