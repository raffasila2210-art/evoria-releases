package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalEvoriaTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen volumetric Cloud Bloom transition:
 * 1. Fluffy multi-lobed clouds roll across and veil the entire screen.
 * 2. Clouds part smoothly outward as warm golden light-leak rays pour from the center.
 * 3. A celebratory popup reveals the contextual Indonesian save message.
 */
@Composable
fun CloudBloomOverlay(
  visible: Boolean,
  message: String,
  reducedMotion: Boolean,
  onFinished: () -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val timeline = remember { Animatable(0f) }
  val popupScale = remember { Animatable(0.75f) }

  LaunchedEffect(visible) {
    if (visible) {
      timeline.snapTo(0f)
      popupScale.snapTo(if (reducedMotion) 1f else 0.72f)
      if (reducedMotion) {
        timeline.snapTo(0.65f)
        delay(1300L)
      } else {
        val animJob = launch {
          timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
          )
        }
        delay(560L)
        popupScale.animateTo(
          targetValue = 1f,
          animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        )
        animJob.join()
        delay(420L)
      }
      onFinished()
    }
  }

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(tween(180)),
    exit = fadeOut(tween(340)),
  ) {
    val t = timeline.value
    // Cloud veil: 0..0.34 closes fully in, 0.34..0.52 holds thick full screen, 0.52..1.0 parts smoothly
    val closeFraction = (t / 0.32f).coerceIn(0f, 1f)
    val partFraction = ((t - 0.42f) / 0.58f).coerceIn(0f, 1f)
    val veilAlpha =
      when {
        t < 0.32f -> closeFraction * 0.94f
        t < 0.48f -> 0.94f
        else -> (1f - ((t - 0.48f) / 0.52f)).coerceIn(0f, 1f) * 0.94f
      }
    // Golden light-leak peaks while clouds are parting
    val lightLeakIntensity =
      if (t < 0.28f) 0f
      else {
        val rise = ((t - 0.28f) / 0.24f).coerceIn(0f, 1f)
        val fade = (1f - ((t - 0.68f) / 0.32f).coerceIn(0f, 1f))
        (rise * fade).coerceIn(0f, 1f)
      }
    val popupAlpha =
      if (reducedMotion) 1f
      else ((t - 0.30f) / 0.16f).coerceIn(0f, 1f) * (1f - ((t - 0.88f) / 0.12f).coerceIn(0f, 1f))

    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onFinished,
          )
          .testTag("cloud_bloom_overlay"),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)

        val skyTint =
          if (tokens.isDark) Color(0xFF1F1917).copy(alpha = veilAlpha * 0.86f)
          else Color(0xFFFFF8F2).copy(alpha = veilAlpha * 0.92f)
        drawRect(color = skyTint)

        val cloudCore =
          if (tokens.isDark) Color(0xFFEADCD1).copy(alpha = veilAlpha * 0.72f)
          else Color(0xFFFFFFFF).copy(alpha = veilAlpha * 0.96f)
        val cloudWarmRim = tokens.accentPeach.copy(alpha = veilAlpha * 0.68f)
        val cloudDeepTint = tokens.primaryTerracotta.copy(alpha = veilAlpha * 0.32f)

        val slideOutX = partFraction * w * 0.72f
        val slideOutY = partFraction * h * 0.52f
        val baseR = size.minDimension * (0.28f + closeFraction * 0.22f)

        // Multi-lobed left & right volumetric cloud banks converging then parting
        val leftOffset = -w * 0.28f * (1f - closeFraction) - slideOutX
        val rightOffset = w * 0.28f * (1f - closeFraction) + slideOutX
        val topOffset = -h * 0.22f * (1f - closeFraction) - slideOutY
        val bottomOffset = h * 0.22f * (1f - closeFraction) + slideOutY

        val lobes =
          listOf(
            Triple(Offset(w * 0.20f + leftOffset, h * 0.24f + topOffset * 0.4f), baseR * 1.15f, cloudWarmRim),
            Triple(Offset(w * 0.18f + leftOffset, h * 0.54f), baseR * 1.32f, cloudCore),
            Triple(Offset(w * 0.26f + leftOffset, h * 0.80f + bottomOffset * 0.4f), baseR * 1.18f, cloudDeepTint),
            Triple(Offset(w * 0.80f + rightOffset, h * 0.24f + topOffset * 0.4f), baseR * 1.15f, cloudWarmRim),
            Triple(Offset(w * 0.82f + rightOffset, h * 0.54f), baseR * 1.32f, cloudCore),
            Triple(Offset(w * 0.74f + rightOffset, h * 0.80f + bottomOffset * 0.4f), baseR * 1.18f, cloudDeepTint),
            Triple(Offset(w * 0.50f, h * 0.18f + topOffset), baseR * 1.35f, cloudCore),
            Triple(Offset(w * 0.50f, h * 0.84f + bottomOffset), baseR * 1.35f, cloudCore),
            Triple(Offset(w * 0.50f + leftOffset * 0.4f, h * 0.48f), baseR * 1.28f, cloudCore),
            Triple(Offset(w * 0.50f + rightOffset * 0.4f, h * 0.52f), baseR * 1.28f, cloudWarmRim),
          )

        lobes.forEach { (c, radius, color) ->
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(color, color.copy(alpha = color.alpha * 0.55f), Color.Transparent),
                center = c,
                radius = radius,
              ),
            center = c,
            radius = radius,
          )
        }

        // Golden light leak + sunbeam rays pouring through the parted clouds
        if (lightLeakIntensity > 0.01f) {
          val rayRadius = size.maxDimension * (0.45f + lightLeakIntensity * 0.55f)
          val sunGold = Color(0xFFFFD8A6).copy(alpha = lightLeakIntensity * 0.78f)
          val sunPeach = tokens.accentPeach.copy(alpha = lightLeakIntensity * 0.55f)

          for (i in 0 until 10) {
            val angle = i * 36f + (t * 28f)
            rotate(degrees = angle, pivot = center) {
              drawRect(
                brush =
                  Brush.verticalGradient(
                    colors = listOf(sunGold, sunPeach.copy(alpha = sunPeach.alpha * 0.4f), Color.Transparent),
                    startY = center.y,
                    endY = center.y - rayRadius,
                  ),
                topLeft = Offset(center.x - (w * 0.075f), center.y - rayRadius),
                size = Size(w * 0.15f, rayRadius),
                blendMode = BlendMode.Plus,
              )
            }
          }

          drawCircle(
            brush =
              Brush.radialGradient(
                colors =
                  listOf(
                    Color.White.copy(alpha = lightLeakIntensity * 0.85f),
                    sunGold,
                    tokens.primaryTerracotta.copy(alpha = lightLeakIntensity * 0.30f),
                    Color.Transparent,
                  ),
                center = center,
                radius = size.minDimension * 0.65f,
              ),
            center = center,
            radius = size.minDimension * 0.65f,
            blendMode = BlendMode.Plus,
          )
        }
      }

      if (popupAlpha > 0.02f) {
        Surface(
          shape = RoundedCornerShape(28.dp),
          color = tokens.elevatedCard.copy(alpha = 0.96f),
          border = BorderStroke(1.2.dp, tokens.primaryTerracotta.copy(alpha = 0.45f)),
          shadowElevation = 14.dp,
          modifier =
            Modifier
              .padding(horizontal = 28.dp)
              .widthIn(max = 360.dp)
              .graphicsLayer {
                scaleX = popupScale.value
                scaleY = popupScale.value
                alpha = popupAlpha
              }
              .testTag("cloud_bloom_popup"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              modifier =
                Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .background(tokens.accentPeach.copy(alpha = 0.34f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = tokens.primaryTerracotta,
                modifier = Modifier.size(30.dp),
              )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = tokens.primaryTerracotta,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "EVORIA",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tokens.primaryTerracotta,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = message,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = tokens.inkText,
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("cloud_bloom_message"),
            )
          }
        }
      }
    }
  }
}
