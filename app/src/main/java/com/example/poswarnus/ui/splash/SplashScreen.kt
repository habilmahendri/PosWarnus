package com.example.poswarnus.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.poswarnus.ui.components.BrandMark
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.SurfaceWhite
import kotlin.math.roundToInt

private val ProgressTrackWidth = 160.dp
private val ProgressSegmentWidth = 64.dp

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.start()
        viewModel.events.collect { event ->
            if (event is SplashEvent.NavigateToLogin) onNavigateToLogin()
        }
    }

    // Branding screen per docs/design-reference.md "Splash Screen": full-bleed accent background,
    // white content, indeterminate progress bar, caption pinned 32dp above the bottom.
    Box(
        modifier = Modifier.fillMaxSize().background(AccentOrange),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(background = SurfaceWhite, foreground = AccentOrange, modifier = Modifier.size(72.dp))
            Text(
                text = "RestoPOS",
                style = MaterialTheme.typography.displayLarge.copy(letterSpacing = (-0.88).sp),
                color = SurfaceWhite,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                text = "Fast. Simple. Efficient.",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                color = SurfaceWhite.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 8.dp),
            )
            LoadingBar(modifier = Modifier.padding(top = 56.dp))
        }
        Text(
            text = "Memuat menu dan pengaturan kasir",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = SurfaceWhite.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        )
    }
}

@Composable
private fun LoadingBar(modifier: Modifier = Modifier) {
    val progress = rememberInfiniteTransition(label = "splash-progress").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash-progress-offset",
    )
    val segmentPx = with(LocalDensity.current) { ProgressSegmentWidth.toPx() }
    Box(
        modifier = modifier
            .width(ProgressTrackWidth)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SurfaceWhite.copy(alpha = 0.28f))
            .semantics { contentDescription = "Memuat aplikasi" },
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((-segmentPx + progress.value * segmentPx * 3.6f).roundToInt(), 0) }
                .width(ProgressSegmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceWhite),
        )
    }
}
