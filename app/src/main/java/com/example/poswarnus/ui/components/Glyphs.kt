package com.example.poswarnus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

// Small Canvas-drawn glyphs standing in for the mockup's inline stroke SVGs — there is no
// Material icon library dependency in this module (see app/build.gradle.kts), so these mirror
// the reference's line-icon style (docs/design-reference.md) without adding one.

@Composable
fun SearchGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        val r = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color = tint, radius = r, center = center, style = stroke)
        val dir = Offset(0.707f, 0.707f)
        drawLine(
            color = tint,
            start = center + dir * (r * 1.05f),
            end = center + dir * (r * 1.95f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun CloseGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val pad = size.minDimension * 0.22f
        drawLine(tint, Offset(pad, pad), Offset(size.width - pad, size.height - pad), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width - pad, pad), Offset(pad, size.height - pad), stroke, StrokeCap.Round)
    }
}

@Composable
fun EmptyCartGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.08f
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.16f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.8f),
            cornerRadius = CornerRadius(w * 0.1f),
            style = Stroke(width = stroke),
        )
        drawLine(tint, Offset(w * 0.3f, h * 0.38f), Offset(w * 0.7f, h * 0.38f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.3f, h * 0.56f), Offset(w * 0.7f, h * 0.56f), stroke, StrokeCap.Round)
    }
}

/** Password show/hide toggle — open eye when [visible], eye with a slash when hidden. */
@Composable
fun EyeGlyph(visible: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.7f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.7f),
            style = stroke,
        )
        drawCircle(color = tint, radius = h * 0.14f, center = Offset(cx, cy), style = Stroke(width = stroke.width))
        if (!visible) {
            drawLine(tint, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.82f, h * 0.82f), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, background: Color, foreground: Color) {
    Canvas(modifier = modifier) {
        drawRoundRect(color = background, cornerRadius = CornerRadius(size.minDimension * 0.22f))
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.27f
        val strokeW = size.minDimension * 0.07f
        drawCircle(color = foreground, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeW))
        drawLine(
            foreground,
            Offset(cx - r * 0.4f, cy - r * 0.9f),
            Offset(cx - r * 0.4f, cy + r * 0.1f),
            strokeW * 0.85f,
            StrokeCap.Round,
        )
        drawLine(
            foreground,
            Offset(cx + r * 0.4f, cy - r * 0.9f),
            Offset(cx + r * 0.4f, cy + r * 0.9f),
            strokeW * 0.85f,
            StrokeCap.Round,
        )
    }
}

@Composable
fun MinusGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val pad = size.minDimension * 0.2f
        drawLine(tint, Offset(pad, size.height / 2f), Offset(size.width - pad, size.height / 2f), stroke, StrokeCap.Round)
    }
}

@Composable
fun PlusGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val pad = size.minDimension * 0.2f
        drawLine(tint, Offset(pad, size.height / 2f), Offset(size.width - pad, size.height / 2f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(size.width / 2f, pad), Offset(size.width / 2f, size.height - pad), stroke, StrokeCap.Round)
    }
}

@Composable
fun CheckGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.125f
        val w = size.width
        val h = size.height
        drawLine(tint, Offset(w * 0.2f, h * 0.52f), Offset(w * 0.4f, h * 0.71f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.4f, h * 0.71f), Offset(w * 0.79f, h * 0.31f), stroke, StrokeCap.Round)
    }
}

@Composable
fun TrashGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.08f
        val w = size.width
        val h = size.height
        drawLine(tint, Offset(w * 0.16f, h * 0.26f), Offset(w * 0.84f, h * 0.26f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.38f, h * 0.14f), Offset(w * 0.62f, h * 0.14f), stroke, StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.24f, h * 0.26f),
            size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.6f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(tint, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.42f, h * 0.7f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.58f, h * 0.7f), stroke, StrokeCap.Round)
    }
}
