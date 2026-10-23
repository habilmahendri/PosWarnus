package com.example.poswarnus.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Card
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.ErrorRed
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

private val rupiahFormat: NumberFormat = NumberFormat.getNumberInstance(Locale("in", "ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}

/** Formats a price as "Rp25.000" (Indonesian thousands grouping, no decimals). */
fun formatRupiah(price: Double): String = "Rp${rupiahFormat.format(price)}"

fun formatRupiah(amount: Long): String = "Rp${rupiahFormat.format(amount)}"

/**
 * Menu card — docs/design-reference.md "Dashboard" § catalog grid: white card, 1px line border,
 * 10dp radius, 16:9 photo, 2-line name with reserved height, tabular price. An unavailable item
 * only dims its photo and shows a "Habis" tag (not the whole card), per the mockup.
 * With [inCartQuantity] > 0 the card shows the "in cart" state: a 28dp accent badge with the
 * quantity at the photo's top-right, and an accent border plus a 1dp accent ring.
 */
@Composable
fun MenuItemCard(item: MenuItem, inCartQuantity: Int = 0, onClick: () -> Unit) {
    val isAvailable = item.isAvailable
    val inCart = inCartQuantity > 0
    val cardShape = RoundedCornerShape(10.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // The 1dp ring slot is always reserved so toggling the in-cart state doesn't shift the grid.
            .border(1.dp, if (inCart) AccentOrange else Color.Transparent, RoundedCornerShape(11.dp))
            .padding(1.dp)
            .clickable(
                enabled = isAvailable,
                interactionSource = remember { MutableInteractionSource() },
                indication = if (isAvailable) LocalIndication.current else null,
            ) { if (isAvailable) onClick() },
        shape = cardShape,
        border = BorderStroke(1.dp, if (inCart) AccentOrange else LineDefault),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box {
                val imageModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .alpha(if (isAvailable) 1f else 0.4f)
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
                if (!isAvailable) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceWhite,
                        border = BorderStroke(1.dp, Color(0xFFE9B4B1)),
                        modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    ) {
                        Text(
                            text = "Habis",
                            style = MaterialTheme.typography.labelLarge,
                            color = ErrorRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                if (inCart) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(AccentOrange, CircleShape)
                            .semantics { contentDescription = "$inCartQuantity di pesanan" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = inCartQuantity.toString(),
                            color = SurfaceWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.heightIn(min = 40.dp),
                )
                Text(
                    text = formatRupiah(item.price),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Category pill — docs/design-reference.md "Dashboard" § category row: 44dp tall, 6dp radius,
 * selected = filled accent + bold white text, unselected = plain secondary text, no border.
 */
@Composable
fun CategoryChip(category: Category?, isSelected: Boolean, onClick: () -> Unit) {
    val label = category?.name ?: "Semua"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) AccentOrange else Color.Transparent,
        contentColor = if (isSelected) SurfaceWhite else TextSecondary,
    ) {
        Box(modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
