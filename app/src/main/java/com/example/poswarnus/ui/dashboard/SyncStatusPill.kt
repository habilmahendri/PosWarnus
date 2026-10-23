package com.example.poswarnus.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.poswarnus.ui.theme.AccentOrangeDark
import com.example.poswarnus.ui.theme.AccentTint
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.SuccessGreen
import com.example.poswarnus.ui.theme.TextSecondary
import java.util.TimeZone

/** Compact, non-interactive sync status. The dot plus the text carry the state, not colour alone. */
@Composable
fun SyncStatusPill(pill: SyncPill, modifier: Modifier = Modifier) {
    if (pill is SyncPill.None) return

    val label = remember(pill) { syncPillLabel(pill, System.currentTimeMillis(), TimeZone.getDefault()) }
    val description = remember(pill) { syncPillDescription(pill) }

    val background: Color
    val dot: Color
    val text: Color
    when (pill) {
        is SyncPill.Offline -> { background = LineDefault; dot = TextSecondary; text = TextSecondary }
        is SyncPill.Pending -> { background = AccentTint; dot = AccentOrangeDark; text = AccentOrangeDark }
        else -> { background = Color.Transparent; dot = SuccessGreen; text = TextSecondary }
    }

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}
