package com.jetmarkdown.views

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import com.facebook.react.uimanager.events.NativeGestureUtil
import kotlin.math.abs

/**
 * Horizontal scroller for code blocks and tables.
 *
 * Two problems to solve, both because this is not a React view:
 * 1. Once a wrapping RN Pressable becomes the JS responder, Fabric's
 *    JSResponderHandler makes that view intercept every non-UP event, which
 *    cancels this scroller's stream (~40ms after DOWN). Guard against it by
 *    disallowing ancestor interception while a touch may become a horizontal
 *    drag, then hand the gesture back the moment it proves vertical so the
 *    outer list still pans.
 * 2. Nothing cancels the JS responder (the Pressable press) when this view
 *    takes the drag — notify the React root like ReactHorizontalScrollView
 *    does, so onPress does not fire after a scroll.
 */
class NestedHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
  private var downX = 0f
  private var downY = 0f
  private var notified = false
  private var directionLocked = false

  private fun hasOverflow(): Boolean =
    canScrollHorizontally(1) || canScrollHorizontally(-1)

  private fun onDown(ev: MotionEvent) {
    downX = ev.x
    downY = ev.y
    notified = false
    directionLocked = false
    if (hasOverflow()) {
      parent?.requestDisallowInterceptTouchEvent(true)
    }
  }

  private fun onMove(ev: MotionEvent) {
    if (directionLocked) {
      return
    }
    val dx = abs(ev.x - downX)
    val dy = abs(ev.y - downY)
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    if (dx > slop || dy > slop) {
      directionLocked = true
      if (dy > dx) {
        // Vertical drag: let the outer list intercept and pan.
        parent?.requestDisallowInterceptTouchEvent(false)
      } else {
        notifyGestureStarted(ev)
      }
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
      onDown(ev)
    }
    if (super.onInterceptTouchEvent(ev)) {
      notifyGestureStarted(ev)
      return true
    }
    return false
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    when (ev.actionMasked) {
      MotionEvent.ACTION_MOVE -> onMove(ev)
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
        parent?.requestDisallowInterceptTouchEvent(false)
    }
    return super.onTouchEvent(ev)
  }

  private fun notifyGestureStarted(ev: MotionEvent) {
    if (!notified) {
      notified = true
      NativeGestureUtil.notifyNativeGestureStarted(this, ev)
    }
  }
}
