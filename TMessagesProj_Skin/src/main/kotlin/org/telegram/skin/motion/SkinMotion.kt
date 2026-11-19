package org.telegram.skin.motion

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.function.BooleanSupplier
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How the skin moves. Every motion is one of four kinds, and each kind says
 * something about the material that moves:
 *
 * - press: an aluminium key drops onto its stop at once and comes back on its
 *   spring with one small rebound, the weight of metal;
 * - state: a toggle's cap slides along its groove and stops without a wobble,
 *   glaze flows into porcelain more slowly than the cap that pulls it, icons
 *   turn over in mid-air;
 * - space: slabs and panels are carried into place and come to rest without a
 *   bounce, a porcelain slip seats in its slot with a hint of a click, and
 *   whatever leaves gets out of the way faster than it came;
 * - emphasis: a celadon LED strikes and blooms past full before it settles,
 *   then dies away with an afterglow; a level jumps up with a voice and sinks
 *   back slowly; numerals roll like a counter's drum.
 *
 * Compose screens take these through [org.telegram.skin.theme.Skin.motion];
 * Telegram's views take the springs through dynamicanimation and the curves
 * through [MotionCurve.interpolator], so a press feels the same everywhere.
 */
object SkinMotion {

    // Press.

    /** A key reaching its stop: as fast as a finger, no overshoot. */
    @JvmField
    val PRESS_DOWN = MotionSpring(stiffness = 3800f, damping = 1f)

    /** The key's return: one rebound of about a tenth, then still. */
    @JvmField
    val PRESS_RELEASE = MotionSpring(stiffness = 700f, damping = 0.58f)

    /** How far a flat key sinks, in dp. */
    const val PRESS_SINK_DP = 1.5f

    /** What a round knob shrinks to instead of sinking. */
    const val PRESS_SCALE = 0.95f

    // State.

    /** Colour, tint and text changes. */
    const val STATE_MS = 180

    @JvmField
    val STATE = MotionCurve(0.3f, 0f, 0.1f, 1f)

    /** One icon turning into another, e.g. send into mic. */
    const val MORPH_MS = 240

    @JvmField
    val MORPH = MotionCurve(0.4f, 0f, 0.2f, 1f)

    /** A toggle's metal cap in its groove: heavy, stops without a visible wobble. */
    @JvmField
    val TOGGLE = MotionSpring(stiffness = 520f, damping = 0.82f)

    /** Glaze filling a porcelain track: slow to start, as a glaze runs. */
    const val GLAZE_MS = 280

    @JvmField
    val GLAZE = MotionCurve(0.45f, 0f, 0.25f, 1f)

    // Space.

    /** Slabs, panels and sheets carried into place, at rest without a bounce. */
    @JvmField
    val ENTER = MotionSpring(stiffness = 380f, damping = 0.86f)

    /** Leaving: accelerates out of the way, quicker than [ENTER] settles. */
    const val EXIT_MS = 170

    @JvmField
    val EXIT = MotionCurve(0.4f, 0f, 1f, 1f)

    /** A porcelain slip seating in the console's slot, with a hint of a click. */
    @JvmField
    val SEAT = MotionSpring(stiffness = 460f, damping = 0.74f)

    /** Something let go after a drag, carrying the finger's speed home. */
    @JvmField
    val SETTLE = MotionSpring(stiffness = 600f, damping = 0.9f)

    /** The gap between keys growing out one after another. */
    const val STAGGER_MS = 24

    // Emphasis.

    /** An LED striking: it blooms about a fifth past full, then settles. */
    @JvmField
    val LED_ON = MotionSpring(stiffness = 1100f, damping = 0.45f)

    /** An LED going out: most of the light gone at once, a faint afterglow lingering. */
    const val LED_OFF_MS = 380

    @JvmField
    val LED_OFF = MotionCurve(0.05f, 0.7f, 0.1f, 1f)

    /** A level follows its source rising with this time constant (ms)... */
    const val LEVEL_RISE_MS = 45f

    /** ...and falling with this one. */
    const val LEVEL_FALL_MS = 240f

    /** Numerals rolling to their next value. */
    const val ROLL_MS = 260

    @JvmField
    val ROLL = MotionCurve(0.2f, 0f, 0f, 1f)

    /**
     * One step of a level following [target], [elapsedMs] after the last step,
     * with [LEVEL_RISE_MS] and [LEVEL_FALL_MS] as time constants. It never
     * overshoots and allocates nothing, so drawing code can call it every frame.
     */
    @JvmStatic
    fun follow(current: Float, target: Float, elapsedMs: Float): Float {
        val tau = if (target > current) LEVEL_RISE_MS else LEVEL_FALL_MS
        return target + (current - target) * exp(-elapsedMs.coerceAtLeast(0f) / tau)
    }

    /**
     * Where [spring] has got to [ms] after setting off from rest, as a share of
     * the way (past 1 while it overshoots). For views whose own clocks drive a
     * motion: the same physics Compose and dynamicanimation run, mass 1.
     */
    @JvmStatic
    fun springAt(spring: MotionSpring, ms: Float): Float {
        if (ms <= 0f) return 0f
        val t = ms / 1000.0
        val w = sqrt(spring.stiffness.toDouble())
        val z = spring.damping.toDouble()
        val rest = when {
            z < 1.0 -> {
                val wd = w * sqrt(1 - z * z)
                exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
            }
            z == 1.0 -> exp(-w * t) * (1 + w * t)
            else -> {
                // Overdamped: two decaying exponentials, starting at rest.
                val root = w * sqrt(z * z - 1)
                val r1 = -z * w + root
                val r2 = -z * w - root
                (r2 * exp(r1 * t) - r1 * exp(r2 * t)) / (r2 - r1)
            }
        }
        return (1 - rest).toFloat()
    }

    /**
     * A knock's path through [spring]: what a thing at rest does when struck,
     * scaled so its furthest swing is 1. It leaves 0 at once, peaks, swings a
     * little past 0 the other way and dies out; a slip seating in its slot
     * moves this way. Only for underdamped springs; others give 0.
     */
    @JvmStatic
    fun springKick(spring: MotionSpring, ms: Float): Float {
        val z = spring.damping.toDouble()
        if (ms <= 0f || z >= 1.0) return 0f
        val w = sqrt(spring.stiffness.toDouble())
        val wd = w * sqrt(1 - z * z)
        val peakT = atan2(wd, z * w) / wd
        val peak = exp(-z * w * peakT) * sin(wd * peakT)
        val t = ms / 1000.0
        return (exp(-z * w * t) * sin(wd * t) / peak).toFloat()
    }

    // Reduced motion.

    private var context: Context? = null
    private var saver = BooleanSupplier { false }
    private var reducedState by mutableStateOf(false)

    /** Hands over the app's context and Telegram's power-saving switch, which [reduced] reads. */
    @JvmStatic
    fun install(context: Context, saver: BooleanSupplier) {
        this.context = context.applicationContext
        this.saver = saver
        refresh()
    }

    /**
     * True when motion should go straight to where it ends: animations removed
     * in the system's settings, or Telegram's chat animations off (battery
     * saving turns them off too). Read it when an animation starts, not on
     * every frame.
     */
    @JvmStatic
    fun reduced(): Boolean = systemOff() || saver.asBoolean

    /**
     * Re-reads [reduced] for the Compose screens. Their owners call it on
     * resume, which covers a change made in settings, and Telegram's power
     * saving calls it when the battery runs low.
     */
    @JvmStatic
    fun refresh() {
        reducedState = reduced()
    }

    internal val reducedForCompose: Boolean
        get() = reducedState

    private fun systemOff(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return !ValueAnimator.areAnimatorsEnabled()
        }
        val resolver = context?.contentResolver ?: return false
        return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A spring as both Compose and dynamicanimation define one: stiffness, and the damping ratio. */
@Immutable
class MotionSpring(val stiffness: Float, val damping: Float) {

    /** How far past its target the spring swings, as a share of the distance travelled. */
    val overshoot: Float
        get() = if (damping >= 1f) 0f else exp(-damping * PI / sqrt(1.0 - damping * damping)).toFloat()

    /** About when it comes within 2% of its target for good, in ms. */
    val settleMs: Float
        get() {
            val omega = sqrt(stiffness.toDouble())
            // Critically damped: (1 + wt)e^-wt = 0.02; underdamped: the envelope e^-zwt = 0.02.
            val seconds = if (damping >= 1f) 5.83 / omega else ln(50.0) / (damping * omega)
            return (seconds * 1000).toFloat()
        }
}

/** A cubic Bézier timing curve, the same in Compose and in Android views. */
@Immutable
class MotionCurve(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {

    val easing: Easing = CubicBezierEasing(x1, y1, x2, y2)

    /** The curve for views and animators; it holds no state, so one is shared. */
    val interpolator: Interpolator by lazy { PathInterpolator(x1, y1, x2, y2) }
}

/** The specs a Compose screen animates with. Every one of them snaps when motion is reduced. */
@Immutable
class SkinMotionSpecs private constructor(val reduced: Boolean) {

    fun <T> pressDown(): FiniteAnimationSpec<T> = springOf(SkinMotion.PRESS_DOWN)

    fun <T> pressRelease(): FiniteAnimationSpec<T> = springOf(SkinMotion.PRESS_RELEASE)

    fun <T> state(): FiniteAnimationSpec<T> = tweenOf(SkinMotion.STATE_MS, SkinMotion.STATE)

    fun <T> morph(): FiniteAnimationSpec<T> = tweenOf(SkinMotion.MORPH_MS, SkinMotion.MORPH)

    fun <T> toggle(): FiniteAnimationSpec<T> = springOf(SkinMotion.TOGGLE)

    fun <T> glaze(): FiniteAnimationSpec<T> = tweenOf(SkinMotion.GLAZE_MS, SkinMotion.GLAZE)

    fun <T> enter(): FiniteAnimationSpec<T> = springOf(SkinMotion.ENTER)

    fun <T> exit(): FiniteAnimationSpec<T> = tweenOf(SkinMotion.EXIT_MS, SkinMotion.EXIT)

    fun <T> seat(): FiniteAnimationSpec<T> = springOf(SkinMotion.SEAT)

    fun <T> settle(): FiniteAnimationSpec<T> = springOf(SkinMotion.SETTLE)

    fun <T> ledOn(): FiniteAnimationSpec<T> = springOf(SkinMotion.LED_ON)

    fun <T> ledOff(): FiniteAnimationSpec<T> = tweenOf(SkinMotion.LED_OFF_MS, SkinMotion.LED_OFF)

    fun <T> roll(): FiniteAnimationSpec<T> = tweenOf(SkinMotion.ROLL_MS, SkinMotion.ROLL)

    /** The delay before the [index]th of several things growing out one after another. */
    fun stagger(index: Int): Int = if (reduced) 0 else index * SkinMotion.STAGGER_MS

    private fun <T> springOf(s: MotionSpring): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = s.damping, stiffness = s.stiffness)

    private fun <T> tweenOf(durationMs: Int, curve: MotionCurve): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMs, easing = curve.easing)

    companion object {
        val Full = SkinMotionSpecs(reduced = false)
        val Reduced = SkinMotionSpecs(reduced = true)
    }
}
