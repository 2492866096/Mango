package org.telegram.skin.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The motion tokens keep the character each kind of motion is meant to have. */
class SkinMotionTest {

    @Test
    fun pressHitsItsStopAndReboundsOnce() {
        assertEquals(0f, SkinMotion.PRESS_DOWN.overshoot)
        assertTrue("a key reaches its stop within a frame or six", SkinMotion.PRESS_DOWN.settleMs < 110f)
        val rebound = SkinMotion.PRESS_RELEASE.overshoot
        assertTrue("the rebound should be felt, not seen as a wobble: $rebound", rebound in 0.05f..0.12f)
    }

    @Test
    fun heavyThingsDoNotBounce() {
        assertTrue(SkinMotion.ENTER.overshoot < 0.01f)
        assertTrue(SkinMotion.SETTLE.overshoot < 0.01f)
        assertTrue(SkinMotion.TOGGLE.overshoot < 0.03f)
    }

    @Test
    fun aSlipClicksIntoItsSlot() {
        val click = SkinMotion.SEAT.overshoot
        assertTrue("more than a slab, less than a key: $click", click > SkinMotion.ENTER.overshoot && click < SkinMotion.PRESS_RELEASE.overshoot)
    }

    @Test
    fun leavingIsQuickerThanArriving() {
        assertTrue(SkinMotion.EXIT_MS < SkinMotion.ENTER.settleMs)
    }

    @Test
    fun glazeFlowsSlowerThanTheCapPullingIt() {
        assertTrue(SkinMotion.GLAZE_MS > SkinMotion.STATE_MS)
        assertTrue(SkinMotion.GLAZE.x1 > SkinMotion.STATE.x1)
    }

    @Test
    fun anLedStrikesAndFadesWithAnAfterglow() {
        val bloom = SkinMotion.LED_ON.overshoot
        assertTrue("bloom past full: $bloom", bloom in 0.15f..0.25f)
        assertTrue(SkinMotion.LED_ON.settleMs < SkinMotion.LED_OFF_MS)
        // Most of the light goes in the first quarter of the fade.
        assertTrue(SkinMotion.LED_OFF.easing.transform(0.25f) > 0.6f)
    }

    @Test
    fun aLevelRisesAtOnceAndSinksSlowly() {
        var up = 0f
        var down = 1f
        repeat(6) {
            up = SkinMotion.follow(up, 1f, 16f)
            down = SkinMotion.follow(down, 0f, 16f)
        }
        assertTrue("up $up", up > 0.85f && up <= 1f)
        assertTrue("down $down", down > 0.6f && down < 1f)
        assertEquals(0.5f, SkinMotion.follow(0.5f, 0.5f, 16f))
    }

    @Test
    fun springAtMatchesComposePhysics() {
        val springs = listOf(
            SkinMotion.PRESS_DOWN, SkinMotion.PRESS_RELEASE, SkinMotion.TOGGLE, SkinMotion.ENTER,
            SkinMotion.SEAT, SkinMotion.SETTLE, SkinMotion.LED_ON, MotionSpring(stiffness = 300f, damping = 1.6f),
        )
        for (s in springs) {
            val spec = spring<Float>(dampingRatio = s.damping, stiffness = s.stiffness)
            val compose = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
            for (ms in listOf(8, 16, 40, 80, 120, 200, 320)) {
                val nanos = ms * 1_000_000L
                val ours = SkinMotion.springAt(s, ms.toFloat())
                // Compose calls a spring done once it is within 1% and then reports the target itself.
                if (nanos < compose.durationNanos) {
                    assertEquals("${s.stiffness}/${s.damping} at $ms ms", compose.getValueFromNanos(nanos), ours, 0.002f)
                } else {
                    assertEquals("${s.stiffness}/${s.damping} at $ms ms, settled", 1f, ours, 0.011f)
                }
            }
        }
    }

    @Test
    fun curvesAreTimingCurves() {
        val curves = listOf(
            SkinMotion.STATE, SkinMotion.MORPH, SkinMotion.GLAZE, SkinMotion.EXIT,
            SkinMotion.LED_OFF, SkinMotion.ROLL,
        )
        for (c in curves) {
            assertTrue(c.x1 in 0f..1f && c.x2 in 0f..1f)
            assertEquals(1f, c.easing.transform(1f), 0.001f)
            assertEquals(0f, c.easing.transform(0f), 0.001f)
        }
    }

    @Test
    fun reducedMotionGoesStraightToTheEnd() {
        val r = SkinMotionSpecs.Reduced
        val specs = listOf<FiniteAnimationSpec<Float>>(
            r.pressDown(), r.pressRelease(), r.state(), r.morph(), r.toggle(), r.glaze(),
            r.enter(), r.exit(), r.seat(), r.settle(), r.ledOn(), r.ledOff(), r.roll(),
        )
        assertTrue(specs.all { it is SnapSpec<*> })
        assertEquals(0, r.stagger(5))
        val f = SkinMotionSpecs.Full
        assertFalse(f.enter<Float>() is SnapSpec<*>)
        assertEquals(5 * SkinMotion.STAGGER_MS, f.stagger(5))
    }
}
