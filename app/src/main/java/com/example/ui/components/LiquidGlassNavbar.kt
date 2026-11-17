package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NavbarGlassIntensity
import com.example.data.local.NavbarThemeStyle
import com.example.ui.theme.LocalEvoriaTokens
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class EvoriaNavTab(
  val index: Int,
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

val EvoriaMainTabs =
  listOf(
    EvoriaNavTab(0, "beranda", "Beranda", Icons.Filled.Home, Icons.Outlined.Home),
    EvoriaNavTab(1, "kenangan", "Kenangan", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    EvoriaNavTab(2, "jelajah", "Jelajah", Icons.Filled.Search, Icons.Outlined.Search),
    EvoriaNavTab(3, "refleksi", "Refleksi", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    EvoriaNavTab(4, "profil", "Profil", Icons.Filled.Person, Icons.Outlined.Person),
  )

enum class LiquidGlassInteractionState(val labelId: String) {
  IDLE("Tenang (Idle)"),
  PRESSED("Ditekan & Menggembung"),
  DRAGGED("Digeser Jelly Elastis"),
}

@Immutable
data class LiquidGlassTelemetry(
  val interactionState: LiquidGlassInteractionState = LiquidGlassInteractionState.IDLE,
  val isHolding: Boolean = false,
  val isDragging: Boolean = false,
  val activeTabIndex: Int = 0,
  val indicatorCenterRatio: Float = 0f,
  val stretchFactor: Float = 1f,
  val chromaticDispersionDp: Float = 0f,
  val velocityPxPerSec: Float = 0f,
  val bulgeFactor: Float = 0f,
  val waistPinchFactor: Float = 0f,
  val underGlowAlpha: Float = 0.16f,
)

@Immutable
data class LiquidGlassKinematics(
  val stretchFactor: Float,
  val chromaticDispersionDp: Float,
  val directionalBiasRatio: Float,
  val bulgeFactor: Float = 0f,
  val verticalJellyScale: Float = 1f,
  val waistPinchFactor: Float = 0f,
  val underGlowAlpha: Float = 0.16f,
)

fun computeSnapTabIndex(capsuleLeftPx: Float, slotWidthPx: Float, tabCount: Int): Int {
  if (tabCount <= 1 || slotWidthPx <= 0f) return 0
  val centerPx = capsuleLeftPx + slotWidthPx / 2f
  return (centerPx / slotWidthPx).toInt().coerceIn(0, tabCount - 1)
}

/**
 * Computes momentum-aware snap tab index from the decoupled indicator position and release velocity,
 * so quick flicks carry naturally into the intended tab instead of stalling midway.
 */
fun computeProjectedSnapTabIndex(
  indicatorLeftPx: Float,
  velocityPxPerSec: Float,
  slotWidthPx: Float,
  tabCount: Int,
): Int {
  if (tabCount <= 1 || slotWidthPx <= 0f) return 0
  val momentumProjectionPx = (velocityPxPerSec * 0.065f).coerceIn(-slotWidthPx * 0.42f, slotWidthPx * 0.42f)
  val maxLeft = (tabCount - 1) * slotWidthPx
  val projectedLeft = (indicatorLeftPx + momentumProjectionPx).coerceIn(0f, maxLeft)
  return computeSnapTabIndex(projectedLeft, slotWidthPx, tabCount)
}

/**
 * Converts raw horizontal touch coordinates into a smooth indicator left position with soft
 * edge rubber-band resistance at the pill boundaries.
 */
fun computeTouchTargetLeftPx(
  rawDesiredLeftPx: Float,
  slotWidthPx: Float,
  tabCount: Int,
): Float {
  if (tabCount <= 1 || slotWidthPx <= 0f) return 0f
  val maxLeft = (tabCount - 1) * slotWidthPx
  val maxOverscroll = slotWidthPx * 0.14f
  return when {
    rawDesiredLeftPx < 0f -> {
      val over = -rawDesiredLeftPx
      -(over * 0.28f).coerceAtMost(maxOverscroll)
    }
    rawDesiredLeftPx > maxLeft -> {
      val over = rawDesiredLeftPx - maxLeft
      maxLeft + (over * 0.28f).coerceAtMost(maxOverscroll)
    }
    else -> rawDesiredLeftPx
  }
}

fun computeLiquidGlassKinematics(
  velocityPxPerSec: Float,
  interactionState: LiquidGlassInteractionState,
  reducedMotion: Boolean,
): LiquidGlassKinematics {
  if (reducedMotion) {
    return LiquidGlassKinematics(
      stretchFactor = 1f,
      chromaticDispersionDp = 0f,
      directionalBiasRatio = 0f,
      bulgeFactor = 0f,
      verticalJellyScale = 1f,
      waistPinchFactor = 0f,
      underGlowAlpha = 0.14f,
    )
  }
  val speedRatio = (abs(velocityPxPerSec) / 2000f).coerceIn(0f, 1f)
  val signedBias = (velocityPxPerSec / 2200f).coerceIn(-1f, 1f)
  return when (interactionState) {
    LiquidGlassInteractionState.IDLE ->
      LiquidGlassKinematics(
        stretchFactor = 1f,
        chromaticDispersionDp = 0f,
        directionalBiasRatio = 0f,
        bulgeFactor = 0f,
        verticalJellyScale = 1f,
        waistPinchFactor = 0f,
        underGlowAlpha = 0.18f,
      )
    LiquidGlassInteractionState.PRESSED ->
      LiquidGlassKinematics(
        stretchFactor = 1.03f,
        chromaticDispersionDp = 0.75f,
        directionalBiasRatio = 0f,
        bulgeFactor = 1f,
        verticalJellyScale = 1.28f,
        waistPinchFactor = 1f,
        underGlowAlpha = 0.72f,
      )
    LiquidGlassInteractionState.DRAGGED ->
      LiquidGlassKinematics(
        stretchFactor = 1.06f + speedRatio * 0.22f,
        chromaticDispersionDp = (1.1f + speedRatio * 2.15f).coerceIn(1.1f, 3.25f),
        directionalBiasRatio = signedBias,
        bulgeFactor = 1.06f,
        // Volume-conserving jelly squash-and-stretch: faster horizontal drag compresses dome slightly
        verticalJellyScale = (1.27f - speedRatio * 0.08f).coerceIn(1.16f, 1.30f),
        waistPinchFactor = 1f,
        underGlowAlpha = 0.88f,
      )
  }
}

/**
 * Computes optical vertical pinch constriction at horizontal position [xPx] along the navbar pill card,
 * modeling the inward bending of the glass card reflections around the inflated capsule lens.
 */
fun computeNavbarWaistPinchPx(
  xPx: Float,
  capsuleCenterX: Float,
  capsuleHalfWidthPx: Float,
  maxPinchPx: Float,
  pillLeftPx: Float,
  pillRightPx: Float,
  pillRadiusPx: Float,
): Float {
  if (maxPinchPx <= 0.05f || capsuleHalfWidthPx <= 1f) return 0f
  val flatStart = pillLeftPx + pillRadiusPx * 0.82f
  val flatEnd = pillRightPx - pillRadiusPx * 0.82f
  if (xPx <= flatStart || xPx >= flatEnd) return 0f

  val edgeMargin = (pillRadiusPx * 0.42f).coerceAtLeast(1f)
  val leftTaper = ((xPx - flatStart) / edgeMargin).coerceIn(0f, 1f)
  val rightTaper = ((flatEnd - xPx) / edgeMargin).coerceIn(0f, 1f)
  val endTaper = leftTaper * rightTaper

  val sigma = (capsuleHalfWidthPx * 1.48f).coerceAtLeast(1f)
  val normDx = (xPx - capsuleCenterX) / sigma
  val bell = exp(-normDx * normDx * 1.55f)
  return maxPinchPx * bell * endTaper
}

private class LiquidGlassPathPool {
  val pillPath = Path()
  val shadowPath = Path()
  val topReflectionPath = Path()
  val bottomReflectionPath = Path()
  val capsulePath = Path()
  val capsuleShadowPath = Path()
  val outerPrismPath = Path()
  val ghostRedPath = Path()
  val ghostCyanPath = Path()
  val innerMeniscusPath = Path()
}

private fun buildPinchedNavbarPillPath(
  pillLeft: Float,
  pillTop: Float,
  pillWidth: Float,
  pillHeight: Float,
  capsuleCenterX: Float,
  capsuleHalfWidth: Float,
  maxPinchPx: Float,
  insetPx: Float = 0f,
  outPath: Path = Path(),
): Path {
  val l = pillLeft + insetPx
  val t = pillTop + insetPx
  val r = (pillLeft + pillWidth - insetPx).coerceAtLeast(l + 2f)
  val b = (pillTop + pillHeight - insetPx).coerceAtLeast(t + 2f)
  val h = b - t
  val w = r - l
  val rad = (h / 2f).coerceAtMost(w / 2f)
  val cy = (t + b) / 2f

  outPath.reset()
  if (maxPinchPx <= 0.15f) {
    outPath.addRoundRect(RoundRect(Rect(l, t, r, b), CornerRadius(rad, rad)))
    return outPath
  }

  val capX = rad * 0.88f
  val spanLeft = l + capX
  val spanRight = (r - capX).coerceAtLeast(spanLeft + 2f)
  val steps = 36

  fun pinchAt(x: Float): Float =
    computeNavbarWaistPinchPx(
      xPx = x,
      capsuleCenterX = capsuleCenterX,
      capsuleHalfWidthPx = capsuleHalfWidth,
      maxPinchPx = maxPinchPx,
      pillLeftPx = l,
      pillRightPx = r,
      pillRadiusPx = rad,
    ).coerceAtMost(h * 0.24f)

  val startPinchLeft = pinchAt(spanLeft)
  val startPinchRight = pinchAt(spanRight)
  val kCap = rad * 0.5522848f

  outPath.moveTo(l, cy)
  outPath.cubicTo(
    l,
    cy - kCap,
    spanLeft - kCap * 0.72f,
    t + startPinchLeft,
    spanLeft,
    t + startPinchLeft,
  )

  for (i in 1..steps) {
    val frac = i / steps.toFloat()
    val x = spanLeft + (spanRight - spanLeft) * frac
    val y = t + pinchAt(x)
    outPath.lineTo(x, y)
  }

  outPath.cubicTo(
    spanRight + kCap * 0.72f,
    t + startPinchRight,
    r,
    cy - kCap,
    r,
    cy,
  )

  outPath.cubicTo(
    r,
    cy + kCap,
    spanRight + kCap * 0.72f,
    b - startPinchRight,
    spanRight,
    b - startPinchRight,
  )

  for (i in 1..steps) {
    val frac = i / steps.toFloat()
    val x = spanRight - (spanRight - spanLeft) * frac
    val y = b - pinchAt(x)
    outPath.lineTo(x, y)
  }

  outPath.cubicTo(
    spanLeft - kCap * 0.72f,
    b - startPinchLeft,
    l,
    cy + kCap,
    l,
    cy,
  )
  outPath.close()
  return outPath
}

private fun buildPinchedEdgeHighlightPath(
  pillLeft: Float,
  pillTop: Float,
  pillWidth: Float,
  pillHeight: Float,
  capsuleCenterX: Float,
  capsuleHalfWidth: Float,
  maxPinchPx: Float,
  isTopEdge: Boolean,
  insetFromEdgePx: Float,
  outPath: Path = Path(),
): Path {
  val l = pillLeft + insetFromEdgePx
  val r = (pillLeft + pillWidth - insetFromEdgePx).coerceAtLeast(l + 4f)
  val rad = (pillHeight / 2f).coerceAtMost(pillWidth / 2f)
  val spanLeft = l + rad * 0.45f
  val spanRight = (r - rad * 0.45f).coerceAtLeast(spanLeft + 2f)
  val steps = 32

  outPath.reset()
  for (i in 0..steps) {
    val frac = i / steps.toFloat()
    val x = spanLeft + (spanRight - spanLeft) * frac
    val pinch =
      if (maxPinchPx <= 0.15f) 0f
      else {
        computeNavbarWaistPinchPx(
          xPx = x,
          capsuleCenterX = capsuleCenterX,
          capsuleHalfWidthPx = capsuleHalfWidth,
          maxPinchPx = maxPinchPx,
          pillLeftPx = pillLeft,
          pillRightPx = pillLeft + pillWidth,
          pillRadiusPx = rad,
        ).coerceAtMost(pillHeight * 0.24f)
      }
    val endFade =
      when {
        x < pillLeft + rad -> {
          val u = ((pillLeft + rad - x) / (rad * 0.65f)).coerceIn(0f, 1f)
          u * u * 3.5f
        }
        x > pillLeft + pillWidth - rad -> {
          val u = ((x - (pillLeft + pillWidth - rad)) / (rad * 0.65f)).coerceIn(0f, 1f)
          u * u * 3.5f
        }
        else -> 0f
      }
    val y =
      if (isTopEdge) pillTop + insetFromEdgePx + pinch + endFade
      else pillTop + pillHeight - insetFromEdgePx - pinch - endFade
    if (i == 0) outPath.moveTo(x, y) else outPath.lineTo(x, y)
  }
  return outPath
}

private fun buildConvexJellyCapsulePath(
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  bulgeProgress: Float,
  dirBias: Float,
  crownPx: Float,
  leanPx: Float,
  outPath: Path = Path(),
): Path {
  val w = width.coerceAtLeast(4f)
  val h = height.coerceAtLeast(4f)
  val right = left + w
  val bottom = top + h
  val cy = top + h / 2f
  val r = (h / 2f).coerceAtMost(w * 0.47f)

  outPath.reset()
  val clampedBulge = bulgeProgress.coerceIn(0f, 1.2f)
  if (clampedBulge < 0.01f && abs(dirBias) < 0.01f) {
    outPath.addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(r, r)))
    return outPath
  }

  val k = r * 0.5522848f
  val crown = crownPx * clampedBulge
  val lean = leanPx * clampedBulge
  val trailExtra = if (dirBias > 0f) dirBias * 2.4f else 0f
  val leadExtra = if (dirBias < 0f) -dirBias * 2.4f else 0f

  outPath.moveTo(left - trailExtra * 0.4f, cy)
  outPath.cubicTo(
    left - trailExtra * 0.35f,
    cy - k,
    left + r - k,
    top,
    left + r,
    top,
  )
  outPath.cubicTo(
    left + r + (w - 2f * r) * 0.28f + lean * 0.4f,
    top - crown,
    right - r - (w - 2f * r) * 0.28f + lean * 0.4f,
    top - crown,
    right - r,
    top,
  )
  outPath.cubicTo(
    right - r + k,
    top,
    right + leadExtra * 0.35f,
    cy - k,
    right + leadExtra * 0.4f,
    cy,
  )
  outPath.cubicTo(
    right + leadExtra * 0.35f,
    cy + k,
    right - r + k,
    bottom,
    right - r,
    bottom,
  )
  outPath.cubicTo(
    right - r - (w - 2f * r) * 0.28f + lean * 0.4f,
    bottom + crown,
    left + r + (w - 2f * r) * 0.28f + lean * 0.4f,
    bottom + crown,
    left + r,
    bottom,
  )
  outPath.cubicTo(
    left + r - k,
    bottom,
    left - trailExtra * 0.35f,
    cy + k,
    left - trailExtra * 0.4f,
    cy,
  )
  outPath.close()
  return outPath
}

@Composable
fun rememberSystemReducedMotion(userReducedMotionOverride: Boolean): Boolean {
  val context = LocalContext.current
  val animatorScale =
    remember(context) {
      runCatching {
          Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
        .getOrDefault(1f)
    }
  return userReducedMotionOverride || animatorScale == 0f
}

private class CachedPillBrushes(
  val pillBgBrush: Brush,
  val sheenBrush: Brush,
  val borderBrush: Brush,
  val topReflectionBrush: Brush,
  val bottomReflectionBrush: Brush,
  val underGlowBaseRadius: Float,
  val underGlowBrush: Brush,
  val outerPrismBrush: Brush,
  val domeBaseRadius: Float,
  val domeBrush: Brush,
  val leftAuroraCenter: Offset,
  val leftAuroraRadius: Float,
  val leftAuroraBrush: Brush,
  val midAuroraCenter: Offset,
  val midAuroraRadius: Float,
  val midAuroraBrush: Brush,
  val rightAuroraCenter: Offset,
  val rightAuroraRadius: Float,
  val rightAuroraBrush: Brush,
)

private class DynamicCapsuleBrushCache {
  private var lastKey: Int = 0
  private var lastTop: Float = Float.NaN
  private var lastHeight: Float = Float.NaN
  private var lastBulgeQuantized: Int = -1
  private var lastGlowQuantized: Int = -1
  var bodyBrush: Brush? = null
  var meniscusBrush: Brush? = null
  var rimBrush: Brush? = null

  fun updateIfNeeded(
    styleKey: Int,
    capsuleTop: Float,
    capsuleHeight: Float,
    bulgeClamped: Float,
    glow: Float,
    bodyTop: Color,
    bodyBottom: Color,
    isDarkLook: Boolean,
    accentPeach: Color,
  ) {
    val bulgeQ = (bulgeClamped * 100f).toInt()
    val glowQ = (glow * 100f).toInt()
    if (
      bodyBrush != null &&
        lastKey == styleKey &&
        abs(lastTop - capsuleTop) < 0.25f &&
        abs(lastHeight - capsuleHeight) < 0.25f &&
        lastBulgeQuantized == bulgeQ &&
        lastGlowQuantized == glowQ
    ) {
      return
    }
    lastKey = styleKey
    lastTop = capsuleTop
    lastHeight = capsuleHeight
    lastBulgeQuantized = bulgeQ
    lastGlowQuantized = glowQ

    val endY = (capsuleTop + capsuleHeight).coerceAtLeast(capsuleTop + 1f)
    bodyBrush =
      Brush.verticalGradient(
        colors = listOf(bodyTop, bodyBottom),
        startY = capsuleTop,
        endY = endY,
      )
    meniscusBrush =
      Brush.verticalGradient(
        colors =
          listOf(
            Color.Black.copy(alpha = 0.22f * bulgeClamped),
            Color.Transparent,
            Color.Black.copy(alpha = 0.16f * bulgeClamped),
          ),
        startY = capsuleTop,
        endY = endY,
      )
    rimBrush =
      Brush.verticalGradient(
        colors =
          listOf(
            Color.White.copy(alpha = if (isDarkLook) 0.62f + bulgeClamped * 0.26f else 0.96f),
            accentPeach.copy(alpha = (0.34f + bulgeClamped * 0.44f).coerceAtMost(0.88f)),
          ),
        startY = capsuleTop,
        endY = endY,
      )
  }
}

private fun DrawScope.drawPinchedNavbarGlassCard(
  pathPool: LiquidGlassPathPool,
  cachedPillBrushes: CachedPillBrushes,
  pillLeft: Float,
  pillTop: Float,
  pillWidth: Float,
  pillHeight: Float,
  isDarkLook: Boolean,
  primaryTerracotta: Color,
  capsuleCenterX: Float,
  capsuleHalfWidth: Float,
  maxPinchPx: Float,
  bulgeAlphaBoost: Float,
) {
  val pillPath =
    buildPinchedNavbarPillPath(
      pillLeft = pillLeft,
      pillTop = pillTop,
      pillWidth = pillWidth,
      pillHeight = pillHeight,
      capsuleCenterX = capsuleCenterX,
      capsuleHalfWidth = capsuleHalfWidth,
      maxPinchPx = maxPinchPx,
      insetPx = 0f,
      outPath = pathPool.pillPath,
    )

  val shadowPath =
    buildPinchedNavbarPillPath(
      pillLeft = pillLeft - 1.dp.toPx(),
      pillTop = pillTop + 4.dp.toPx(),
      pillWidth = pillWidth + 2.dp.toPx(),
      pillHeight = pillHeight + 2.dp.toPx(),
      capsuleCenterX = capsuleCenterX,
      capsuleHalfWidth = capsuleHalfWidth,
      maxPinchPx = maxPinchPx * 0.7f,
      outPath = pathPool.shadowPath,
    )
  drawPath(
    path = shadowPath,
    color =
      if (isDarkLook) Color.Black.copy(alpha = 0.42f)
      else primaryTerracotta.copy(alpha = 0.16f),
  )

  clipPath(pillPath) {
    drawRect(
      brush = cachedPillBrushes.pillBgBrush,
      topLeft = Offset(pillLeft, pillTop),
      size = Size(pillWidth, pillHeight),
    )
    drawCircle(
      brush = cachedPillBrushes.leftAuroraBrush,
      center = cachedPillBrushes.leftAuroraCenter,
      radius = cachedPillBrushes.leftAuroraRadius,
    )
    drawCircle(
      brush = cachedPillBrushes.midAuroraBrush,
      center = cachedPillBrushes.midAuroraCenter,
      radius = cachedPillBrushes.midAuroraRadius,
    )
    drawCircle(
      brush = cachedPillBrushes.rightAuroraBrush,
      center = cachedPillBrushes.rightAuroraCenter,
      radius = cachedPillBrushes.rightAuroraRadius,
    )
    drawRect(
      brush = cachedPillBrushes.sheenBrush,
      topLeft = Offset(pillLeft, pillTop),
      size = Size(pillWidth, pillHeight),
    )
  }

  drawPath(
    path = pillPath,
    brush = cachedPillBrushes.borderBrush,
    style = Stroke(width = 1.05.dp.toPx(), join = StrokeJoin.Round),
  )

  val topReflectionPath =
    buildPinchedEdgeHighlightPath(
      pillLeft = pillLeft,
      pillTop = pillTop,
      pillWidth = pillWidth,
      pillHeight = pillHeight,
      capsuleCenterX = capsuleCenterX,
      capsuleHalfWidth = capsuleHalfWidth,
      maxPinchPx = maxPinchPx,
      isTopEdge = true,
      insetFromEdgePx = 1.6.dp.toPx(),
      outPath = pathPool.topReflectionPath,
    )
  drawPath(
    path = topReflectionPath,
    brush = cachedPillBrushes.topReflectionBrush,
    alpha = (0.76f + bulgeAlphaBoost * 0.24f).coerceIn(0f, 1f),
    style = Stroke(width = (1.15f + bulgeAlphaBoost * 0.45f).dp.toPx(), cap = StrokeCap.Round),
  )

  val bottomReflectionPath =
    buildPinchedEdgeHighlightPath(
      pillLeft = pillLeft,
      pillTop = pillTop,
      pillWidth = pillWidth,
      pillHeight = pillHeight,
      capsuleCenterX = capsuleCenterX,
      capsuleHalfWidth = capsuleHalfWidth,
      maxPinchPx = maxPinchPx,
      isTopEdge = false,
      insetFromEdgePx = 1.7.dp.toPx(),
      outPath = pathPool.bottomReflectionPath,
    )
  drawPath(
    path = bottomReflectionPath,
    brush = cachedPillBrushes.bottomReflectionBrush,
    alpha = (0.74f + bulgeAlphaBoost * 0.26f).coerceIn(0f, 1f),
    style = Stroke(width = (1.0f + bulgeAlphaBoost * 0.35f).dp.toPx(), cap = StrokeCap.Round),
  )
}

@Composable
private fun LiquidGlassVisualStageCanvas(
  totalWidthPx: Float,
  pillTopPx: Float,
  pillHeightPx: Float,
  horizontalPadPx: Float,
  slotWidthPx: Float,
  isDarkLook: Boolean,
  pillBgColors: List<Color>,
  glassSpecularTop: Color,
  glassSpecularBottom: Color,
  accentPeach: Color,
  primaryTerracotta: Color,
  navbarThemeStyle: NavbarThemeStyle,
  glassIntensity: NavbarGlassIntensity,
  reducedMotion: Boolean,
  indicatorLeftPxState: State<Float>,
  bulgeProgressState: State<Float>,
  glowIntensityState: State<Float>,
  stretchFactorState: State<Float>,
  chromaticDispersionDpState: State<Float>,
  directionalBiasState: State<Float>,
) {
  val pathPool = remember { LiquidGlassPathPool() }
  val dynamicCapsuleBrushes = remember { DynamicCapsuleBrushCache() }
  val cachedPillBrushes =
    remember(
      totalWidthPx,
      pillTopPx,
      pillHeightPx,
      slotWidthPx,
      isDarkLook,
      pillBgColors,
      glassSpecularTop,
      glassSpecularBottom,
      accentPeach,
      primaryTerracotta,
    ) {
      val pillWidth = totalWidthPx.coerceAtLeast(2f)
      val leftCenter = Offset(pillWidth * 0.24f, pillTopPx + pillHeightPx * 0.34f)
      val leftRadius = (pillWidth * 0.36f).coerceAtLeast(1f)
      val midCenter = Offset(pillWidth * 0.56f, pillTopPx + pillHeightPx * 0.52f)
      val midRadius = (pillWidth * 0.30f).coerceAtLeast(1f)
      val rightCenter = Offset(pillWidth * 0.82f, pillTopPx + pillHeightPx * 0.68f)
      val rightRadius = (pillWidth * 0.34f).coerceAtLeast(1f)
      val underGlowBaseRadius = (slotWidthPx * 0.94f * 0.68f).coerceAtLeast(1f)
      val domeBaseRadius = (slotWidthPx * 0.94f * 0.72f).coerceAtLeast(1f)
      CachedPillBrushes(
        pillBgBrush =
          Brush.verticalGradient(
            colors = pillBgColors,
            startY = pillTopPx,
            endY = pillTopPx + pillHeightPx,
          ),
        sheenBrush =
          Brush.verticalGradient(
            colors =
              listOf(
                Color.White.copy(alpha = if (isDarkLook) 0.10f else 0.38f),
                Color.Transparent,
                primaryTerracotta.copy(alpha = if (isDarkLook) 0.08f else 0.05f),
              ),
            startY = pillTopPx,
            endY = pillTopPx + pillHeightPx,
          ),
        borderBrush =
          Brush.verticalGradient(
            colors = listOf(glassSpecularTop, glassSpecularBottom),
            startY = pillTopPx,
            endY = pillTopPx + pillHeightPx,
          ),
        topReflectionBrush =
          Brush.horizontalGradient(
            colors =
              listOf(
                Color.White.copy(alpha = if (isDarkLook) 0.56f else 0.94f),
                accentPeach.copy(alpha = if (isDarkLook) 0.46f else 0.68f),
                Color.White.copy(alpha = if (isDarkLook) 0.50f else 0.86f),
              ),
            startX = 0f,
            endX = pillWidth,
          ),
        bottomReflectionBrush =
          Brush.horizontalGradient(
            colors =
              listOf(
                accentPeach.copy(alpha = if (isDarkLook) 0.38f else 0.54f),
                Color.White.copy(alpha = if (isDarkLook) 0.44f else 0.70f),
                accentPeach.copy(alpha = if (isDarkLook) 0.38f else 0.54f),
              ),
            startX = 0f,
            endX = pillWidth,
          ),
        underGlowBaseRadius = underGlowBaseRadius,
        underGlowBrush =
          Brush.radialGradient(
            colors =
              listOf(
                accentPeach.copy(alpha = 0.85f),
                primaryTerracotta.copy(alpha = 0.42f),
                Color.Transparent,
              ),
            center = Offset.Zero,
            radius = underGlowBaseRadius,
          ),
        outerPrismBrush =
          Brush.sweepGradient(
            0.00f to Color(0xFFFFB48A).copy(alpha = 0.58f),
            0.14f to Color(0xFF22D3EE).copy(alpha = 0.62f),
            0.26f to Color(0xFF34D399).copy(alpha = 0.52f),
            0.38f to Color(0xFFFF3B30).copy(alpha = 0.86f),
            0.48f to Color(0xFFFF9500).copy(alpha = 0.82f),
            0.56f to Color(0xFF22D3EE).copy(alpha = 0.78f),
            0.66f to Color(0xFF2563EB).copy(alpha = 0.72f),
            0.78f to Color(0xFFA855F7).copy(alpha = 0.55f),
            0.90f to Color(0xFFFFE4D6).copy(alpha = 0.48f),
            1.00f to Color(0xFFFFB48A).copy(alpha = 0.58f),
            center = Offset.Zero,
          ),
        domeBaseRadius = domeBaseRadius,
        domeBrush =
          Brush.radialGradient(
            colors =
              listOf(
                Color.White.copy(alpha = if (isDarkLook) 0.32f else 0.45f),
                accentPeach.copy(alpha = 0.36f),
                Color.Transparent,
              ),
            center = Offset.Zero,
            radius = domeBaseRadius,
          ),
        leftAuroraCenter = leftCenter,
        leftAuroraRadius = leftRadius,
        leftAuroraBrush =
          Brush.radialGradient(
            colors =
              listOf(
                accentPeach.copy(alpha = if (isDarkLook) 0.20f else 0.26f),
                accentPeach.copy(alpha = if (isDarkLook) 0.07f else 0.09f),
                Color.Transparent,
              ),
            center = leftCenter,
            radius = leftRadius,
          ),
        midAuroraCenter = midCenter,
        midAuroraRadius = midRadius,
        midAuroraBrush =
          Brush.radialGradient(
            colors =
              listOf(
                Color(0xFF4ECDC4).copy(alpha = if (isDarkLook) 0.14f else 0.11f),
                Color.Transparent,
              ),
            center = midCenter,
            radius = midRadius,
          ),
        rightAuroraCenter = rightCenter,
        rightAuroraRadius = rightRadius,
        rightAuroraBrush =
          Brush.radialGradient(
            colors =
              listOf(
                primaryTerracotta.copy(alpha = if (isDarkLook) 0.18f else 0.14f),
                Color.Transparent,
              ),
            center = rightCenter,
            radius = rightRadius,
          ),
      )
    }

  Canvas(
    modifier = Modifier.fillMaxSize()
  ) {
    if (size.width <= 2f || size.height <= 2f || slotWidthPx <= 1f) return@Canvas

    val activeLeft = indicatorLeftPxState.value
    val bulge = if (reducedMotion) 0f else bulgeProgressState.value
    val bulgeClamped = bulge.coerceAtLeast(0f)
    val glowRaw = glowIntensityState.value
    val glow = (glowRaw * glassIntensity.glowMultiplier).coerceIn(0f, 1.45f)
    val stretch = stretchFactorState.value
    val dirBias = directionalBiasState.value

    val speedStretchExcess = (stretch - 1f).coerceAtLeast(0f)
    val bulgeWidthScale = 1f + 0.13f * bulgeClamped
    val bulgeHeightScale = (1f + 0.31f * bulge) * (1f - (speedStretchExcess * 0.22f).coerceAtMost(0.08f))

    val capsuleBaseWidth = slotWidthPx * 0.94f
    val capsuleWidth = capsuleBaseWidth * stretch * bulgeWidthScale
    val baseCapsuleHeight = 56.dp.toPx()
    val capsuleHeight = (baseCapsuleHeight * bulgeHeightScale).coerceAtLeast(40.dp.toPx())

    val directionalShift = dirBias * 7f
    val minLeft = horizontalPadPx * 0.18f
    val maxLeft = (totalWidthPx - horizontalPadPx * 0.18f - capsuleWidth).coerceAtLeast(minLeft)
    val capsuleLeft =
      (horizontalPadPx + activeLeft + (slotWidthPx - capsuleWidth) / 2f + directionalShift)
        .coerceIn(minLeft, maxLeft)
    val pillCenterY = pillTopPx + pillHeightPx / 2f
    val capsuleTop = pillCenterY - capsuleHeight / 2f
    val capsuleCenterX = capsuleLeft + capsuleWidth / 2f

    val maxPinchPx = (5.8.dp.toPx() * bulgeClamped * glassIntensity.dispersionMultiplier.coerceAtLeast(0.85f))

    val underGlowAlpha = ((0.18f + glowRaw * 0.62f) * glassIntensity.glowMultiplier).coerceIn(0f, 0.92f)
    if (underGlowAlpha > 0.02f) {
      val underGlowCenter = Offset(capsuleCenterX, capsuleTop + capsuleHeight + 1.dp.toPx())
      val underGlowRadius = (capsuleWidth * 0.68f).coerceAtLeast(1f)
      val underGlowScale = (underGlowRadius / cachedPillBrushes.underGlowBaseRadius).coerceAtLeast(0.5f)
      withTransform({
        translate(underGlowCenter.x, underGlowCenter.y)
        scale(scaleX = 1.22f * underGlowScale, scaleY = 0.44f * underGlowScale, pivot = Offset.Zero)
      }) {
        drawCircle(
          brush = cachedPillBrushes.underGlowBrush,
          center = Offset.Zero,
          radius = cachedPillBrushes.underGlowBaseRadius,
          alpha = underGlowAlpha,
        )
      }
    }

    drawPinchedNavbarGlassCard(
      pathPool = pathPool,
      cachedPillBrushes = cachedPillBrushes,
      pillLeft = 0f,
      pillTop = pillTopPx,
      pillWidth = size.width,
      pillHeight = pillHeightPx,
      isDarkLook = isDarkLook,
      primaryTerracotta = primaryTerracotta,
      capsuleCenterX = capsuleCenterX,
      capsuleHalfWidth = capsuleWidth / 2f,
      maxPinchPx = maxPinchPx,
      bulgeAlphaBoost = bulgeClamped,
    )

    val capsulePath =
      buildConvexJellyCapsulePath(
        left = capsuleLeft,
        top = capsuleTop,
        width = capsuleWidth,
        height = capsuleHeight,
        bulgeProgress = bulge,
        dirBias = dirBias,
        crownPx = 2.4.dp.toPx(),
        leanPx = dirBias * 4.5.dp.toPx(),
        outPath = pathPool.capsulePath,
      )

    val shadowPath =
      buildConvexJellyCapsulePath(
        left = capsuleLeft - 1.5.dp.toPx(),
        top = capsuleTop + 3.dp.toPx(),
        width = capsuleWidth + 3.dp.toPx(),
        height = capsuleHeight + 2.5.dp.toPx(),
        bulgeProgress = bulge,
        dirBias = dirBias,
        crownPx = 2.2.dp.toPx(),
        leanPx = dirBias * 4.5.dp.toPx(),
        outPath = pathPool.capsuleShadowPath,
      )
    drawPath(
      path = shadowPath,
      color =
        if (isDarkLook) Color.Black.copy(alpha = 0.36f + bulgeClamped * 0.24f)
        else primaryTerracotta.copy(alpha = 0.14f + bulgeClamped * 0.16f),
    )

    val dispIntensity =
      (bulgeClamped * 0.85f + (chromaticDispersionDpState.value / 3.25f) * 0.65f)
        .coerceIn(0f, 1.35f) * glassIntensity.dispersionMultiplier
    if (dispIntensity > 0.04f && !reducedMotion) {
      val prismCenter = Offset(capsuleCenterX + dirBias * 6.dp.toPx(), pillCenterY)
      val outerPrismPath =
        buildConvexJellyCapsulePath(
          left = capsuleLeft - 1.2.dp.toPx() * bulgeClamped,
          top = capsuleTop - 0.9.dp.toPx() * bulgeClamped,
          width = capsuleWidth + 2.4.dp.toPx() * bulgeClamped,
          height = capsuleHeight + 1.8.dp.toPx() * bulgeClamped,
          bulgeProgress = bulge,
          dirBias = dirBias,
          crownPx = 2.6.dp.toPx(),
          leanPx = dirBias * 4.5.dp.toPx(),
          outPath = pathPool.outerPrismPath,
        )
      outerPrismPath.translate(Offset(-prismCenter.x, -prismCenter.y))
      withTransform({
        translate(prismCenter.x, prismCenter.y)
      }) {
        drawPath(
          path = outerPrismPath,
          brush = cachedPillBrushes.outerPrismBrush,
          alpha = dispIntensity.coerceIn(0f, 1f),
          style = Stroke(width = (3.4f * dispIntensity.coerceIn(0.35f, 1.2f)).dp.toPx(), join = StrokeJoin.Round),
        )
      }

      val dispShiftPx = (chromaticDispersionDpState.value * glassIntensity.dispersionMultiplier).dp.toPx()
      if (dispShiftPx > 0.1f) {
        val trailSign = if (dirBias >= 0f) -1f else 1f
        drawPath(
          path =
            buildConvexJellyCapsulePath(
              left = capsuleLeft + trailSign * dispShiftPx * 0.85f,
              top = capsuleTop,
              width = capsuleWidth,
              height = capsuleHeight,
              bulgeProgress = bulge,
              dirBias = dirBias,
              crownPx = 2.4.dp.toPx(),
              leanPx = dirBias * 4.5.dp.toPx(),
              outPath = pathPool.ghostRedPath,
            ),
          color = Color(0xFFFF4D4D).copy(alpha = 0.34f * dispIntensity.coerceAtMost(1f)),
          style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round),
        )
        drawPath(
          path =
            buildConvexJellyCapsulePath(
              left = capsuleLeft - trailSign * dispShiftPx * 0.85f,
              top = capsuleTop,
              width = capsuleWidth,
              height = capsuleHeight,
              bulgeProgress = bulge,
              dirBias = dirBias,
              crownPx = 2.4.dp.toPx(),
              leanPx = dirBias * 4.5.dp.toPx(),
              outPath = pathPool.ghostCyanPath,
            ),
          color = Color(0xFF22D3EE).copy(alpha = 0.32f * dispIntensity.coerceAtMost(1f)),
          style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round),
        )
      }
    }

    val (bodyTop, bodyBottom) =
      when (navbarThemeStyle) {
        NavbarThemeStyle.CRYSTAL_CLEAR ->
          if (isDarkLook) {
            Color(0xB89B908B).copy(alpha = (0.58f + glow * 0.14f).coerceAtMost(0.84f)) to
              Color(0x965E5450).copy(alpha = (0.52f + glow * 0.12f).coerceAtMost(0.80f))
          } else {
            Color(0xEBFFFFFF) to Color(0xC8EFF4F8)
          }
        NavbarThemeStyle.VELVET_NOIR ->
          Color(0xC476645E).copy(alpha = (0.62f + glow * 0.14f).coerceAtMost(0.86f)) to
            Color(0xB03E322E).copy(alpha = (0.58f + glow * 0.12f).coerceAtMost(0.82f))
        NavbarThemeStyle.ROSE_QUARTZ ->
          if (isDarkLook) {
            Color(0xC8936C65).copy(alpha = (0.60f + glow * 0.14f).coerceAtMost(0.85f)) to
              Color(0xB2563C37)
          } else {
            Color(0xF2FFF7F2) to Color(0xDEFBDFD0)
          }
        NavbarThemeStyle.FROST_AURORA ->
          if (isDarkLook) {
            Color(0xC28E7B74).copy(alpha = (0.58f + glow * 0.14f).coerceAtMost(0.85f)) to
              Color(0xAE4D403C)
          } else {
            Color(0xF4FFFFFF) to Color(0xDCF6ECE0)
          }
      }

    val styleKey = (navbarThemeStyle.ordinal * 31) + (if (isDarkLook) 1 else 0) + accentPeach.hashCode()
    dynamicCapsuleBrushes.updateIfNeeded(
      styleKey = styleKey,
      capsuleTop = capsuleTop,
      capsuleHeight = capsuleHeight,
      bulgeClamped = bulgeClamped,
      glow = glow,
      bodyTop = bodyTop,
      bodyBottom = bodyBottom,
      isDarkLook = isDarkLook,
      accentPeach = accentPeach,
    )

    clipPath(capsulePath) {
      dynamicCapsuleBrushes.bodyBrush?.let { bodyBrush ->
        drawRect(
          brush = bodyBrush,
          topLeft = Offset(capsuleLeft - 4.dp.toPx(), capsuleTop - 4.dp.toPx()),
          size = Size(capsuleWidth + 8.dp.toPx(), capsuleHeight + 8.dp.toPx()),
        )
      }

      val domeFocus = Offset(capsuleCenterX + dirBias * capsuleWidth * 0.14f, capsuleTop + capsuleHeight * 0.24f)
      val domeRadius = (capsuleWidth * 0.72f).coerceAtLeast(1f)
      val domeScale = (domeRadius / cachedPillBrushes.domeBaseRadius).coerceAtLeast(0.5f)
      withTransform({
        translate(domeFocus.x, domeFocus.y)
        scale(scaleX = domeScale, scaleY = domeScale, pivot = Offset.Zero)
      }) {
        drawCircle(
          brush = cachedPillBrushes.domeBrush,
          center = Offset.Zero,
          radius = cachedPillBrushes.domeBaseRadius,
          alpha = (0.72f + bulgeClamped * 0.28f).coerceIn(0f, 1f),
        )
      }

      if (bulgeClamped > 0.04f) {
        val innerMeniscusPath =
          buildConvexJellyCapsulePath(
            left = capsuleLeft + 2.2.dp.toPx(),
            top = capsuleTop + 2.2.dp.toPx(),
            width = (capsuleWidth - 4.4.dp.toPx()).coerceAtLeast(4f),
            height = (capsuleHeight - 4.4.dp.toPx()).coerceAtLeast(4f),
            bulgeProgress = bulge,
            dirBias = dirBias,
            crownPx = 2.0.dp.toPx(),
            leanPx = dirBias * 3.5.dp.toPx(),
            outPath = pathPool.innerMeniscusPath,
          )
        dynamicCapsuleBrushes.meniscusBrush?.let { meniscusBrush ->
          drawPath(
            path = innerMeniscusPath,
            brush = meniscusBrush,
            style = Stroke(width = 2.6.dp.toPx(), join = StrokeJoin.Round),
          )
        }
      }
    }

    dynamicCapsuleBrushes.rimBrush?.let { rimBrush ->
      drawPath(
        path = capsulePath,
        brush = rimBrush,
        style = Stroke(width = (1.15f + bulgeClamped * 0.65f).dp.toPx(), join = StrokeJoin.Round),
      )
    }
  }
}

/**
 * EVORIA reusable Liquid Glass Navigation Bar:
 * - Decouples continuous indicator position from committed page index during touch & drag.
 * - Features inflating bulge on hold, volume-conserving jelly drag kinematics, subtle bottom light
 *   glow, inward pinched navbar card reflections around the bulging lens, and smooth curved
 *   chromatic rim dispersion that springs cleanly back to normal on release.
 */
@Composable
fun LiquidGlassNavbar(
  selectedTabIndex: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
  tabs: List<EvoriaNavTab> = EvoriaMainTabs,
  navbarThemeStyle: NavbarThemeStyle = NavbarThemeStyle.FROST_AURORA,
  navbarGlassIntensity: NavbarGlassIntensity = NavbarGlassIntensity.BALANCED,
  reducedMotionOverride: Boolean = false,
  applyNavigationBarInsets: Boolean = true,
  onTelemetryChanged: ((LiquidGlassTelemetry) -> Unit)? = null,
) {
  val tokens = LocalEvoriaTokens.current
  val haptics = LocalHapticFeedback.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val reducedMotion = rememberSystemReducedMotion(reducedMotionOverride)
  val pillIsDarkLook = tokens.isDark || navbarThemeStyle == NavbarThemeStyle.VELVET_NOIR

  val pillBgColors =
    remember(navbarThemeStyle, tokens.isDark) {
      when (navbarThemeStyle) {
        NavbarThemeStyle.CRYSTAL_CLEAR ->
          if (tokens.isDark) listOf(Color(0xB83D3633), Color(0xD41B1716))
          else listOf(Color(0xDCFFFFFF), Color(0xCAEAF0F6))
        NavbarThemeStyle.VELVET_NOIR ->
          listOf(Color(0xE82A211F), Color(0xF514100F))
        NavbarThemeStyle.ROSE_QUARTZ ->
          if (tokens.isDark) listOf(Color(0xD44C322D), Color(0xEA2B1B18))
          else listOf(Color(0xF0FFF5EE), Color(0xE0F4DFD2))
        NavbarThemeStyle.FROST_AURORA ->
          if (tokens.isDark) listOf(Color(0xD23D322F), Color(0xEA1E1816))
          else listOf(Color(0xEEFDF9F2), Color(0xDCEDE3D6))
      }
    }

  val safeIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)

  val pillContainerModifier =
    modifier
      .fillMaxWidth()
      .then(if (applyNavigationBarInsets) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
      .padding(horizontal = 14.dp, vertical = 2.dp)

  Box(
    modifier = pillContainerModifier,
    contentAlignment = Alignment.Center,
  ) {
    // 84dp outer stage leaves 8dp top & bottom unclipped halo space around the centered 68dp pill
    // so the bulging glass capsule, curved chromatic rim, and bottom light glow never clip.
    BoxWithConstraints(
      modifier =
        Modifier
          .widthIn(max = 560.dp)
          .fillMaxWidth()
          .height(84.dp)
          .systemGestureExclusion()
    ) {
      val totalWidthPx = with(density) { maxWidth.toPx() }
      val pillTopPx = with(density) { 8.dp.toPx() }
      val pillHeightPx = with(density) { 68.dp.toPx() }
      val horizontalPadPx = with(density) { 6.dp.toPx() }
      val trackWidthPx = (totalWidthPx - horizontalPadPx * 2f).coerceAtLeast(1f)
      val slotWidthPx = trackWidthPx / tabs.size.toFloat()

      // Decoupled visual indicator position (completely independent from page index during drag)
      val indicatorPosPx = remember { Animatable(safeIndex.toFloat() * slotWidthPx) }

      val isGestureActiveState = remember { mutableStateOf(false) }
      val isHoldingState = remember { mutableStateOf(false) }
      val isDraggingState = remember { mutableStateOf(false) }
      val pendingSnapTargetState = remember { mutableStateOf<Int?>(null) }

      // Touch target & smoothed optical kinematics kept in MutableFloatStates (draw-phase only)
      val touchTargetLeftPxState = remember { mutableFloatStateOf(safeIndex.toFloat() * slotWidthPx) }
      val rawDragVelocityState = remember { mutableFloatStateOf(0f) }
      val smoothedStretchState = remember { mutableFloatStateOf(1f) }
      val smoothedDispersionState = remember { mutableFloatStateOf(0f) }
      val smoothedBiasState = remember { mutableFloatStateOf(0f) }

      var lastCrossedIndex by remember { mutableIntStateOf(safeIndex) }
      var activeSnapJob by remember { mutableStateOf<Job?>(null) }

      val latestSafeIndex by rememberUpdatedState(safeIndex)
      val latestSlotWidthPx by rememberUpdatedState(slotWidthPx)
      val latestTrackWidthPx by rememberUpdatedState(trackWidthPx)
      val latestTabsCount by rememberUpdatedState(tabs.size)
      val latestReducedMotion by rememberUpdatedState(reducedMotion)
      val latestOnTabSelected by rememberUpdatedState(onTabSelected)
      val latestOnTelemetryChanged by rememberUpdatedState(onTelemetryChanged)

      val interactionState by
        remember {
          derivedStateOf {
            when {
              isDraggingState.value -> LiquidGlassInteractionState.DRAGGED
              isHoldingState.value -> LiquidGlassInteractionState.PRESSED
              else -> LiquidGlassInteractionState.IDLE
            }
          }
        }

      // Sync visual indicator to committed page index ONLY when no user gesture or snap is active
      LaunchedEffect(safeIndex, slotWidthPx) {
        if (!isGestureActiveState.value && pendingSnapTargetState.value == null) {
          val target = safeIndex * slotWidthPx
          if (abs(indicatorPosPx.targetValue - target) > 0.5f || abs(indicatorPosPx.value - target) > 0.5f) {
            activeSnapJob?.cancel()
            activeSnapJob =
              launch {
                if (reducedMotion) {
                  indicatorPosPx.snapTo(target)
                } else {
                  indicatorPosPx.animateTo(
                    targetValue = target,
                    animationSpec =
                      spring(
                        dampingRatio = 0.66f,
                        stiffness = 460f,
                      ),
                  )
                }
              }
          }
        } else if (pendingSnapTargetState.value == safeIndex && !isGestureActiveState.value) {
          pendingSnapTargetState.value = null
        }
      }

      // Vsync-paced touch interpolation & optical kinematics loop (ZERO Compose recompositions per frame)
      LaunchedEffect(interactionState, reducedMotion) {
        if (interactionState == LiquidGlassInteractionState.DRAGGED) {
          while (isActive) {
            var prevNanos = 0L
            while (isActive) {
              var nextLeftToSnap: Float? = null
              var shouldSleep = false
              withFrameNanos { frameNanos ->
                val dtSec =
                  if (prevNanos == 0L) 0.016f
                  else ((frameNanos - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                prevNanos = frameNanos

                val currentLeft = indicatorPosPx.value
                val targetLeft = touchTargetLeftPxState.floatValue
                val diff = targetLeft - currentLeft
                val alpha =
                  if (reducedMotion) 1f
                  else (1f - exp(-dtSec * 44f)).coerceIn(0.24f, 1f)
                val posConverged = abs(diff) <= 0.25f
                val nextLeft = if (posConverged) targetLeft else currentLeft + diff * alpha

                if (abs(nextLeft - currentLeft) > 0.01f) {
                  nextLeftToSnap = nextLeft
                }

                // Gently decay velocity when finger pauses motionless mid-drag
                val curVel = rawDragVelocityState.floatValue
                if (abs(curVel) > 0f && posConverged) {
                  val decayed = curVel * exp(-dtSec * 20f)
                  rawDragVelocityState.floatValue = if (abs(decayed) < 12f) 0f else decayed
                }

                val kin =
                  computeLiquidGlassKinematics(
                    velocityPxPerSec = rawDragVelocityState.floatValue,
                    interactionState = LiquidGlassInteractionState.DRAGGED,
                    reducedMotion = reducedMotion,
                  )
                val kinAlpha = if (reducedMotion) 1f else (1f - exp(-dtSec * 28f)).coerceIn(0.18f, 1f)
                val nextStretch =
                  smoothedStretchState.floatValue +
                    (kin.stretchFactor - smoothedStretchState.floatValue) * kinAlpha
                val nextDisp =
                  smoothedDispersionState.floatValue +
                    (kin.chromaticDispersionDp - smoothedDispersionState.floatValue) * kinAlpha
                val nextBias =
                  smoothedBiasState.floatValue +
                    (kin.directionalBiasRatio - smoothedBiasState.floatValue) * kinAlpha

                smoothedStretchState.floatValue = nextStretch
                smoothedDispersionState.floatValue = nextDisp
                smoothedBiasState.floatValue = nextBias

                val kinConverged =
                  abs(nextStretch - kin.stretchFactor) < 0.002f &&
                    abs(nextDisp - kin.chromaticDispersionDp) < 0.01f &&
                    abs(nextBias - kin.directionalBiasRatio) < 0.005f
                shouldSleep = posConverged && kinConverged && rawDragVelocityState.floatValue == 0f
              }
              val snapTarget = nextLeftToSnap
              if (snapTarget != null) {
                indicatorPosPx.snapTo(snapTarget)
                val crossed = computeSnapTabIndex(snapTarget, latestSlotWidthPx, latestTabsCount)
                if (crossed != lastCrossedIndex) {
                  lastCrossedIndex = crossed
                  haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
              }
              if (shouldSleep) break
            }

            // Pause vsync loop cleanly until pointer target moves again
            val settledTarget = touchTargetLeftPxState.floatValue
            val settledVel = rawDragVelocityState.floatValue
            snapshotFlow { touchTargetLeftPxState.floatValue to rawDragVelocityState.floatValue }
              .first { (t, v) ->
                abs(t - settledTarget) > 0.25f || abs(v - settledVel) > 12f
              }
          }
        } else {
          // Smoothly relax stretch, dispersion, and directional bias back to PRESSED / IDLE on vsync
          val targetKin =
            computeLiquidGlassKinematics(
              velocityPxPerSec = 0f,
              interactionState = interactionState,
              reducedMotion = reducedMotion,
            )
          if (reducedMotion) {
            smoothedStretchState.floatValue = targetKin.stretchFactor
            smoothedDispersionState.floatValue = targetKin.chromaticDispersionDp
            smoothedBiasState.floatValue = targetKin.directionalBiasRatio
          } else {
            var prevNanos = 0L
            while (isActive) {
              var settled = false
              withFrameNanos { frameNanos ->
                val dtSec =
                  if (prevNanos == 0L) 0.016f
                  else ((frameNanos - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                prevNanos = frameNanos
                val settleAlpha = (1f - exp(-dtSec * 24f)).coerceIn(0.15f, 1f)

                val nextStretch =
                  smoothedStretchState.floatValue +
                    (targetKin.stretchFactor - smoothedStretchState.floatValue) * settleAlpha
                val nextDisp =
                  smoothedDispersionState.floatValue +
                    (targetKin.chromaticDispersionDp - smoothedDispersionState.floatValue) * settleAlpha
                val nextBias =
                  smoothedBiasState.floatValue +
                    (targetKin.directionalBiasRatio - smoothedBiasState.floatValue) * settleAlpha

                smoothedStretchState.floatValue = nextStretch
                smoothedDispersionState.floatValue = nextDisp
                smoothedBiasState.floatValue = nextBias

                settled =
                  abs(nextStretch - targetKin.stretchFactor) < 0.002f &&
                    abs(nextDisp - targetKin.chromaticDispersionDp) < 0.01f &&
                    abs(nextBias - targetKin.directionalBiasRatio) < 0.005f
              }
              if (settled) {
                smoothedStretchState.floatValue = targetKin.stretchFactor
                smoothedDispersionState.floatValue = targetKin.chromaticDispersionDp
                smoothedBiasState.floatValue = targetKin.directionalBiasRatio
                break
              }
            }
          }
        }
      }

      // Elastic jelly bulge progress: 0f (normal idle) -> 1f (pressed) / 1.06f (dragged), returning
      // with underdamped jelly bounce back to normal when released.
      val bulgeProgressState =
        animateFloatAsState(
          targetValue =
            when {
              reducedMotion -> 0f
              interactionState == LiquidGlassInteractionState.DRAGGED -> 1.06f
              interactionState == LiquidGlassInteractionState.PRESSED -> 1f
              else -> 0f
            },
          animationSpec = spring(dampingRatio = 0.46f, stiffness = 390f),
          label = "capsuleJellyBulge",
        )

      val glowIntensityState =
        animateFloatAsState(
          targetValue =
            when (interactionState) {
              LiquidGlassInteractionState.DRAGGED -> 1f
              LiquidGlassInteractionState.PRESSED -> 0.88f
              LiquidGlassInteractionState.IDLE -> 0f
            },
          animationSpec = tween(durationMillis = 150),
          label = "capsuleGlow",
        )

      if (onTelemetryChanged != null) {
        LaunchedEffect(Unit) {
          snapshotFlow {
            val activeState = interactionState
            val targetKin =
              computeLiquidGlassKinematics(
                velocityPxPerSec = rawDragVelocityState.floatValue,
                interactionState = activeState,
                reducedMotion = reducedMotion,
              )
            LiquidGlassTelemetry(
              interactionState = activeState,
              isHolding = isHoldingState.value,
              isDragging = isDraggingState.value,
              activeTabIndex = latestSafeIndex,
              indicatorCenterRatio =
                ((indicatorPosPx.value + latestSlotWidthPx / 2f) / latestTrackWidthPx).coerceIn(0f, 1f),
              stretchFactor = smoothedStretchState.floatValue,
              chromaticDispersionDp = smoothedDispersionState.floatValue,
              velocityPxPerSec = rawDragVelocityState.floatValue,
              bulgeFactor = bulgeProgressState.value.coerceAtLeast(0f),
              waistPinchFactor = bulgeProgressState.value.coerceIn(0f, 1f),
              underGlowAlpha = targetKin.underGlowAlpha,
            )
          }.collect { telemetry ->
            latestOnTelemetryChanged?.invoke(telemetry)
          }
        }
      }

      // 1 & 2. Unified Liquid Glass Optical Canvas (bottom light glow + pinched glass card +
      //        convex bulging jelly capsule + curved chromatic rim dispersion)
      LiquidGlassVisualStageCanvas(
        totalWidthPx = totalWidthPx,
        pillTopPx = pillTopPx,
        pillHeightPx = pillHeightPx,
        horizontalPadPx = horizontalPadPx,
        slotWidthPx = slotWidthPx,
        isDarkLook = pillIsDarkLook,
        pillBgColors = pillBgColors,
        glassSpecularTop = tokens.glassSpecularTop,
        glassSpecularBottom = tokens.glassSpecularBottom,
        accentPeach = tokens.accentPeach,
        primaryTerracotta = tokens.primaryTerracotta,
        navbarThemeStyle = navbarThemeStyle,
        glassIntensity = navbarGlassIntensity,
        reducedMotion = reducedMotion,
        indicatorLeftPxState = indicatorPosPx.asState(),
        bulgeProgressState = bulgeProgressState,
        glowIntensityState = glowIntensityState,
        stretchFactorState = smoothedStretchState,
        chromaticDispersionDpState = smoothedDispersionState,
        directionalBiasState = smoothedBiasState,
      )

      // 3. Interactive tab items centered inside the 68dp pill zone
      val hoveredIndex by
        remember(safeIndex, slotWidthPx, tabs.size) {
          derivedStateOf {
            if (isDraggingState.value || isHoldingState.value) {
              computeSnapTabIndex(indicatorPosPx.value, slotWidthPx, tabs.size)
            } else {
              pendingSnapTargetState.value ?: safeIndex
            }
          }
        }

      val labelSmall = MaterialTheme.typography.labelSmall
      val normalTabLabelStyle =
        remember(labelSmall) {
          labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
        }
      val highlightedTabLabelStyle =
        remember(labelSmall) {
          labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(68.dp)
            .align(Alignment.Center)
            .padding(horizontal = 6.dp)
            .pointerInput(Unit) {
              awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                activeSnapJob?.cancel()
                pendingSnapTargetState.value = null
                isGestureActiveState.value = true

                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                val slotW = latestSlotWidthPx
                val tabCount = latestTabsCount
                val downTabIndex = ((down.position.x / slotW).toInt()).coerceIn(0, tabCount - 1)
                lastCrossedIndex = computeSnapTabIndex(indicatorPosPx.value, slotW, tabCount)

                // Anchor relative to the current indicator position if grabbed inside/near the capsule,
                // or glide smoothly toward the finger if grabbed further away
                val indicatorCenterAtDown = indicatorPosPx.value + slotW / 2f
                val grabbedCapsule = abs(down.position.x - indicatorCenterAtDown) <= slotW * 0.62f
                val pointerAnchorOffset =
                  if (grabbedCapsule) indicatorPosPx.value - down.position.x else -slotW / 2f

                var holdTriggered = false
                var dragStarted = false
                val startX = down.position.x

                val holdJob: Job =
                  scope.launch {
                    delay(95L)
                    holdTriggered = true
                    isHoldingState.value = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!dragStarted && downTabIndex != computeSnapTabIndex(indicatorPosPx.value, slotW, tabCount)) {
                      indicatorPosPx.animateTo(
                        downTabIndex * slotW,
                        spring(dampingRatio = 0.64f, stiffness = 480f),
                      )
                      lastCrossedIndex = downTabIndex
                    }
                  }

                try {
                  while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    val curSlotW = latestSlotWidthPx
                    val curTabsCount = latestTabsCount

                    if (!change.pressed) {
                      holdJob.cancel()
                      val finalVelocity = velocityTracker.calculateVelocity().x
                      val currentIndicator = indicatorPosPx.value
                      val targetIndex =
                        if (dragStarted || holdTriggered) {
                          computeProjectedSnapTabIndex(
                            indicatorLeftPx = currentIndicator,
                            velocityPxPerSec = finalVelocity,
                            slotWidthPx = curSlotW,
                            tabCount = curTabsCount,
                          )
                        } else {
                          ((change.position.x / curSlotW).toInt()).coerceIn(0, curTabsCount - 1)
                        }

                      // Release returns bulge, pinch, jelly stretch, and chromatic dispersion cleanly to normal
                      isHoldingState.value = false
                      isDraggingState.value = false
                      isGestureActiveState.value = false
                      rawDragVelocityState.floatValue = 0f
                      pendingSnapTargetState.value = targetIndex

                      val dest = targetIndex * curSlotW
                      val isReduced = latestReducedMotion
                      activeSnapJob =
                        scope.launch {
                          try {
                            if (isReduced) {
                              indicatorPosPx.snapTo(dest)
                            } else {
                              indicatorPosPx.animateTo(
                                targetValue = dest,
                                initialVelocity = finalVelocity * 0.34f,
                                animationSpec =
                                  spring(
                                    dampingRatio = 0.58f,
                                    stiffness = 480f,
                                  ),
                              )
                            }
                          } finally {
                            if (pendingSnapTargetState.value == targetIndex) {
                              pendingSnapTargetState.value = null
                            }
                          }
                        }

                      if (targetIndex != latestSafeIndex) {
                        latestOnTabSelected(targetIndex)
                      }
                      break
                    }

                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val deltaFromDown = change.position.x - startX
                    if (!dragStarted && abs(deltaFromDown) > viewConfiguration.touchSlop) {
                      holdJob.cancel()
                      dragStarted = true
                      isHoldingState.value = true
                      isDraggingState.value = true
                      val rawDesired = change.position.x + pointerAnchorOffset
                      touchTargetLeftPxState.floatValue =
                        computeTouchTargetLeftPx(rawDesired, curSlotW, curTabsCount)
                    }

                    if (dragStarted) {
                      change.consume()
                      val rawDesired = change.position.x + pointerAnchorOffset
                      touchTargetLeftPxState.floatValue =
                        computeTouchTargetLeftPx(rawDesired, curSlotW, curTabsCount)
                      rawDragVelocityState.floatValue = velocityTracker.calculateVelocity().x
                    }
                  }
                } catch (_: Throwable) {
                  holdJob.cancel()
                  isHoldingState.value = false
                  isDraggingState.value = false
                  isGestureActiveState.value = false
                  rawDragVelocityState.floatValue = 0f
                  val fallbackTab = latestSafeIndex
                  pendingSnapTargetState.value = fallbackTab
                  activeSnapJob =
                    scope.launch {
                      try {
                        indicatorPosPx.animateTo(
                          fallbackTab * latestSlotWidthPx,
                          spring(dampingRatio = 0.66f, stiffness = 460f),
                        )
                      } finally {
                        if (pendingSnapTargetState.value == fallbackTab) {
                          pendingSnapTargetState.value = null
                        }
                      }
                    }
                }
              }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        tabs.forEach { tab ->
          val isCommitted = tab.index == safeIndex
          val isHighlighted = tab.index == hoveredIndex
          val targetTint =
            when {
              isHighlighted ->
                if (pillIsDarkLook) Color(0xFFFBE9DF) else tokens.primaryTerracotta
              pillIsDarkLook -> Color(0xFFC2B5AE)
              interactionState == LiquidGlassInteractionState.IDLE -> tokens.mutedText
              else -> tokens.mutedText.copy(alpha = 0.76f)
            }
          val itemColor by animateColorAsState(
            targetValue = targetTint,
            animationSpec = tween(durationMillis = 100),
            label = "tabColor_${tab.route}",
          )
          val itemScaleState =
            animateFloatAsState(
              targetValue =
                if (isHighlighted && interactionState != LiquidGlassInteractionState.IDLE && !reducedMotion) 1.09f
                else 1f,
              animationSpec = spring(dampingRatio = 0.56f, stiffness = 420f),
              label = "tabScale_${tab.route}",
            )

          Column(
            modifier =
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                  val s = itemScaleState.value
                  scaleX = s
                  scaleY = s
                }
                .testTag("nav_tab_${tab.route}")
                .semantics {
                  role = Role.Tab
                  selected = isCommitted
                  contentDescription = tab.label
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = if (isHighlighted) tab.selectedIcon else tab.unselectedIcon,
              contentDescription = null,
              tint = itemColor,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = tab.label,
              style = if (isHighlighted) highlightedTabLabelStyle else normalTabLabelStyle,
              color = itemColor,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
