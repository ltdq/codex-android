package com.cy.codex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec

/**
 * The shell's page stack swaps in one frame: any settle phase that outlives a frame is time the
 * library keeps the revealed page covered.
 */
class NavPageSwapTest {

    @Test
    fun `no settle phase outlives a frame`() {
        val motion = ShellPageTransition.motion

        listOf("commit" to motion.commit, "cancel" to motion.cancel, "programmatic" to motion.programmatic)
            .forEach { (phase, spec) ->
                val tween =
                    spec as? NavSettleSpec.Tween
                        ?: fail("$phase must not animate: $spec")
                assertEquals(0, tween.durationMillis, "$phase settles over ${tween.durationMillis}ms")
            }
    }

    /** A settle that stops short of the target index, or drags on, leaves the leaving page drawn. */
    @Test
    fun `every phase lands on the target index within a frame`() =
        runTest {
            val motion = ShellPageTransition.motion
            listOf("commit" to motion.commit, "cancel" to motion.cancel, "programmatic" to motion.programmatic)
                .forEach { (phase, spec) ->
                    val curve = spec as NavSettleSpec.Tween
                    val driver = Animatable(1f)
                    FrameClock.reset()

                    withContext(FrameClock) {
                        driver.animateTo(0f, tween(curve.durationMillis, easing = curve.easing))
                    }

                    assertEquals(0f, driver.value, "$phase stopped short of the index")
                    assertTrue(
                        FrameClock.frames <= 1,
                        "$phase took ${FrameClock.frames} frames",
                    )
                }
        }

    /** Frames as fast as the animation asks for them, counting how many it needed. */
    private object FrameClock : MonotonicFrameClock {
        var frames = 0
            private set

        private var nanos = 0L

        fun reset() {
            frames = 0
        }

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frames++
            nanos += 16_666_667L
            return onFrame(nanos)
        }
    }
}
