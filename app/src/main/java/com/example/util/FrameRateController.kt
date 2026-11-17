package com.example.util

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import kotlin.math.abs

data class DisplayModeCandidate(
  val modeId: Int,
  val width: Int,
  val height: Int,
  val refreshRate: Float,
)

/**
 * Selects the display mode closest to 60 Hz at the active physical resolution so Android locks
 * both the surface frame cadence and display refresh rate cleanly at 60 FPS.
 */
fun selectLocked60HzDisplayModeId(
  modes: List<DisplayModeCandidate>,
  activeWidth: Int,
  activeHeight: Int,
): Int? {
  if (modes.isEmpty()) return null
  val sameResolution =
    modes.filter { it.width == activeWidth && it.height == activeHeight }.ifEmpty { modes }
  val sixtyHzBand = sameResolution.filter { it.refreshRate in 58.5f..61.5f }
  return sixtyHzBand.minByOrNull { abs(it.refreshRate - 60.0f) }?.modeId
}

/**
 * Returns true only when mutating WindowManager.LayoutParams refresh rate attributes is needed,
 * avoiding redundant WMS relayout/SurfaceSyncGroup transactions on displays already fixed at 60 Hz.
 */
fun shouldMutateWindowAttributesFor60Fps(
  activeRefreshRate: Float,
  hasHighRefreshMode: Boolean,
  currentPreferredRefreshRate: Float,
  currentPreferredModeId: Int,
  lockTo60Fps: Boolean,
): Boolean {
  if (!lockTo60Fps) {
    return currentPreferredRefreshRate != 0f || currentPreferredModeId != 0
  }
  // Display is already running in the 60 Hz band and no override is active: avoid redundant WMS relayouts.
  if (activeRefreshRate in 58.5f..61.5f && currentPreferredRefreshRate == 0f && currentPreferredModeId == 0) {
    return false
  }
  if (!hasHighRefreshMode && activeRefreshRate in 58.5f..61.5f) {
    return false
  }
  return abs(currentPreferredRefreshRate - 60f) > 0.1f
}

object FrameRateController {

  private val mainHandler = Handler(Looper.getMainLooper())

  @Suppress("DEPRECATION")
  private fun currentDisplay(activity: Activity): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      activity.display
    } else {
      activity.windowManager?.defaultDisplay
    }

  /**
   * Schedules the 60 FPS lock after initial window creation & SurfaceSyncGroup (#2/#3) settle so
   * first-frame WMS transactions never stall waiting on a mid-traversal WindowManager relayout.
   */
  fun scheduleFrameRateLock(activity: Activity, lockTo60Fps: Boolean): () -> Unit {
    val task = Runnable {
      if (!activity.isFinishing && !activity.isDestroyed) {
        applyFrameRateLock(activity, lockTo60Fps)
      }
    }
    mainHandler.postDelayed(task, 650L)
    return { mainHandler.removeCallbacks(task) }
  }

  fun applyFrameRateLock(activity: Activity, lockTo60Fps: Boolean) {
    val window = activity.window ?: return
    val decorView = window.decorView
    if (!decorView.isAttachedToWindow) return
    runCatching {
      val display = currentDisplay(activity)
      val activeMode = display?.mode
      val supportedModes = display?.supportedModes
      val activeRefreshRate = activeMode?.refreshRate ?: display?.refreshRate ?: 60f
      val hasHighRefreshMode = supportedModes?.any { it.refreshRate > 61.5f } == true

      val attrs = window.attributes
      val targetRefreshRate = if (lockTo60Fps) 60f else 0f
      val needsAttrChange =
        shouldMutateWindowAttributesFor60Fps(
          activeRefreshRate = activeRefreshRate,
          hasHighRefreshMode = hasHighRefreshMode,
          currentPreferredRefreshRate = attrs.preferredRefreshRate,
          currentPreferredModeId = attrs.preferredDisplayModeId,
          lockTo60Fps = lockTo60Fps,
        )

      if (needsAttrChange) {
        attrs.preferredRefreshRate = targetRefreshRate
        if (!lockTo60Fps) {
          attrs.preferredDisplayModeId = 0
        } else if (activeRefreshRate > 61.5f && activeMode != null && supportedModes != null) {
          val candidates =
            supportedModes.map { m ->
              DisplayModeCandidate(
                modeId = m.modeId,
                width = m.physicalWidth,
                height = m.physicalHeight,
                refreshRate = m.refreshRate,
              )
            }
          val sixtyHzModeId =
            selectLocked60HzDisplayModeId(
              modes = candidates,
              activeWidth = activeMode.physicalWidth,
              activeHeight = activeMode.physicalHeight,
            )
          if (sixtyHzModeId != null) {
            attrs.preferredDisplayModeId = sixtyHzModeId
          }
        }
        window.attributes = attrs
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        // Only toggle Window-level ARR power/touch flags on high-refresh displays because setting
        // them calls PhoneWindow.dispatchWindowAttributesChanged -> updateViewLayout() / SurfaceSyncGroup.
        if (hasHighRefreshMode || activeRefreshRate > 61.5f || !lockTo60Fps) {
          val desiredPowerBalanced = !lockTo60Fps
          if (window.isFrameRatePowerSavingsBalanced != desiredPowerBalanced) {
            window.isFrameRatePowerSavingsBalanced = desiredPowerBalanced
          }
          val desiredTouchBoost = !lockTo60Fps
          if (window.frameRateBoostOnTouchEnabled != desiredTouchBoost) {
            window.frameRateBoostOnTouchEnabled = desiredTouchBoost
          }
        }
        val desiredRequestedRate =
          if (lockTo60Fps) 60f else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
        if (decorView.requestedFrameRate != desiredRequestedRate) {
          decorView.requestedFrameRate = desiredRequestedRate
        }
      }
    }
  }

  fun registerDisplayRefreshObserver(
    activity: Activity,
    isLockEnabled: () -> Boolean,
  ): () -> Unit {
    val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return {}
    val mainHandler = Handler(Looper.getMainLooper())
    var pendingTask: Runnable? = null
    val listener =
      object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
          if (!isLockEnabled() || activity.isFinishing || activity.isDestroyed) return
          val activeDisplay = currentDisplay(activity) ?: return
          if (activeDisplay.displayId != displayId) return
          val activeRate = activeDisplay.mode?.refreshRate ?: activeDisplay.refreshRate
          if (activeRate in 58.5f..61.5f) return

          pendingTask?.let { mainHandler.removeCallbacks(it) }
          val task = Runnable {
            if (!activity.isFinishing && !activity.isDestroyed && isLockEnabled()) {
              applyFrameRateLock(activity, lockTo60Fps = true)
            }
          }
          pendingTask = task
          mainHandler.post(task)
        }
      }
    dm.registerDisplayListener(listener, mainHandler)
    return {
      pendingTask?.let { mainHandler.removeCallbacks(it) }
      runCatching { dm.unregisterDisplayListener(listener) }
    }
  }
}
