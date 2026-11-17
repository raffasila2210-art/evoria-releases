package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.LocalEvoriaTokens

private val WarmTreeColorFilter =
  ColorFilter.colorMatrix(
    ColorMatrix(
      floatArrayOf(
        1.22f, 0.14f, 0.0f, 0f, 22f,
        0.08f, 1.04f, 0.0f, 0f, 10f,
        0.0f, 0.04f, 0.72f, 0f, -8f,
        0f, 0f, 0f, 1f, 0f,
      )
    )
  )

/**
 * Ambient EVORIA background canvas extracting the logo's visual language:
 * sparse corner botanical leaves, subtle sunset radial glow, gentle rolling hills,
 * calm lake ripples, and optional warm-filtered tree sanctuary photo on Home.
 * Paths and Brushes are cached via drawWithCache for zero per-frame allocations at 60 FPS.
 */
@Composable
fun EvoriaSanctuaryBackdrop(
  modifier: Modifier = Modifier,
  showBottomHills: Boolean = true,
  useWarmTreePhoto: Boolean = false,
  content: @Composable BoxScope.() -> Unit,
) {
  val tokens = LocalEvoriaTokens.current
  val treeOpacity = if (tokens.isDark) 0.42f else 0.28f
  val scrimBrush =
    remember(tokens.isDark, tokens.accentPeach) {
      Brush.verticalGradient(
        colors =
          if (tokens.isDark) {
            listOf(
              Color(0xB82A1E1B),
              tokens.accentPeach.copy(alpha = 0.16f),
              Color(0xDC1B1716),
            )
          } else {
            listOf(
              Color(0xBDF5EDE2),
              tokens.accentPeach.copy(alpha = 0.24f),
              Color(0xDDF5F0E8),
            )
          }
      )
    }

  Box(modifier = modifier.fillMaxSize().background(tokens.background)) {
    if (useWarmTreePhoto) {
      AsyncImage(
        model = rememberThumbnailRequest(R.drawable.bg_home_tree, sizePx = 720),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = WarmTreeColorFilter,
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = treeOpacity },
      )
      Box(
        modifier = Modifier.fillMaxSize().background(scrimBrush)
      )
    }

    Spacer(
      modifier =
        Modifier
          .fillMaxSize()
          .drawWithCache {
            val w = size.width
            val h = size.height

            val sunsetCenter = Offset(w * 0.85f, h * 0.12f)
            val sunsetRadius = (w * 0.65f).coerceAtLeast(1f)
            val sunsetBrush =
              Brush.radialGradient(
                colors = listOf(tokens.sunsetGlow, Color.Transparent),
                center = sunsetCenter,
                radius = sunsetRadius,
              )

            val leafColor = tokens.primaryTerracotta.copy(alpha = if (tokens.isDark) 0.12f else 0.09f)
            val stemStroke = Stroke(width = 3f, cap = StrokeCap.Round)
            val stemPath =
              if (!useWarmTreePhoto) {
                Path().apply {
                  moveTo(w * 0.98f, h * 0.02f)
                  quadraticTo(w * 0.86f, h * 0.09f, w * 0.78f, h * 0.17f)
                }
              } else null

            val leafPaths =
              if (!useWarmTreePhoto) {
                listOf(0.89f to 0.06f, 0.84f to 0.11f, 0.93f to 0.10f).map { (lx, ly) ->
                  val cx = w * lx
                  val cy = h * ly
                  Path().apply {
                    moveTo(cx, cy)
                    quadraticTo(cx - 26f, cy - 12f, cx - 36f, cy + 12f)
                    quadraticTo(cx - 10f, cy + 20f, cx, cy)
                  }
                }
              } else emptyList()

            val farHill =
              if (showBottomHills) {
                Path().apply {
                  moveTo(0f, h * 0.89f)
                  quadraticTo(w * 0.28f, h * 0.83f, w * 0.58f, h * 0.88f)
                  quadraticTo(w * 0.82f, h * 0.92f, w, h * 0.86f)
                  lineTo(w, h)
                  lineTo(0f, h)
                  close()
                }
              } else null

            val nearHill =
              if (showBottomHills) {
                Path().apply {
                  moveTo(0f, h * 0.93f)
                  quadraticTo(w * 0.35f, h * 0.96f, w * 0.68f, h * 0.91f)
                  quadraticTo(w * 0.88f, h * 0.89f, w, h * 0.94f)
                  lineTo(w, h)
                  lineTo(0f, h)
                  close()
                }
              } else null

            val rippleColor = tokens.accentPeach.copy(alpha = if (tokens.isDark) 0.10f else 0.16f)

            onDrawBehind {
              drawCircle(
                brush = sunsetBrush,
                center = sunsetCenter,
                radius = sunsetRadius,
              )

              if (stemPath != null) {
                drawPath(stemPath, color = leafColor, style = stemStroke)
                for (leaf in leafPaths) {
                  drawPath(leaf, color = leafColor)
                }
              }

              if (farHill != null && nearHill != null) {
                drawPath(farHill, color = tokens.hillSilhouetteFar)
                drawPath(nearHill, color = tokens.hillSilhouetteNear)

                for (i in 0..2) {
                  val y = h * (0.945f + i * 0.015f)
                  val startX = w * (0.18f + i * 0.10f)
                  val endX = w * (0.48f + i * 0.08f)
                  drawLine(
                    color = rippleColor,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round,
                  )
                }
              }
            }
          }
    )
    content()
  }
}
