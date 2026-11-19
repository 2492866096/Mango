package org.telegram.skin.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The skin's three kinds of touch feedback. They are for things that happen
 * (a detent crossed, an action done, an action refused), never for scrolling
 * or plain taps on rows. They go through the view, so the system's touch
 * feedback setting is honoured.
 *
 * The newer constants do nothing on older Android, so each kind falls back to
 * an old constant the platform plays with the same effect: CONTEXT_CLICK and
 * SEGMENT_TICK are both a tick, VIRTUAL_KEY and CONFIRM both a click. Android
 * 10 and older have no double knock, so a heavy click stands in for REJECT.
 */
object SkinHaptics {

    /** A light tick: a detent crossed, a key passing under a sliding finger. */
    @JvmStatic
    fun tap(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CONTEXT_CLICK
        )
    }

    /** A firm click: something done, e.g. a recording locked or a menu opened by holding. */
    @JvmStatic
    fun confirm(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    /** A double knock: the action cannot happen, e.g. slow mode or nothing to send. */
    @JvmStatic
    fun reject(view: View) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
        )
    }
}
