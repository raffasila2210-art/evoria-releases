package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalEvoriaTokens
import kotlinx.coroutines.delay

/**
 * Gentle full-screen warm Cloud Bloom shown ONLY after a successful new memory or album save.
 * Uses a calm fade alternative when Reduced Motion is active.
 */
@Composable
fun CloudBloomOverlay(
  visible: Boolean,
  message: String,
  reducedMotion: Boolean,
  onFinished: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val progress = remember { Animatable(0f) }

  LaunchedEffect(visible) {
    if (visible) {
      progress.snapTo(0f)
      if (reducedMotion) {
        progress.snapTo(1f)
        delay(1100L)
      } else {
        progress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
        )
        delay(280L)
      }
      onFinished()
    }
  }

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(220)),
    exit = fadeOut(tween(340)),
  ) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        val maxRadius = size.maxDimension * (0.28f + t * 0.78f)
        val alpha = (1f - (t * 0.55f)).coerceIn(0.18f, 0.72f)
        drawCircle(
          brush =
            Brush.radialGradient(
              colors =
                listOf(
                  tokens.accentPeach.copy(alpha = alpha),
                  tokens.primaryTerracotta.copy(alpha = alpha * 0.45f),
                  Color.Transparent,
                ),
              center = Offset(size.width / 2f, size.height / 2f),
              radius = maxRadius,
            ),
          center = Offset(size.width / 2f, size.height / 2f),
          radius = maxRadius,
        )
      }

      Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "Tersimpan di EVORIA",
          style = MaterialTheme.typography.headlineMedium,
          color = tokens.inkText,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = tokens.mutedText,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
