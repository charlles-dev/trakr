package app.trakr.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/** Easing de assinatura do Trakr: saída com leve aceleração "snappy". */
val TrakrEase: Easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

/** Spring de assinatura para micro-interações. */
fun trakrSpring() =
    androidx.compose.animation.core.spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 700f,
    )

/**
 * Micro-interação de toque: o elemento encolhe sutilmente enquanto o dedo
 * está pressionado e volta com um leve "spring" ao soltar.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    spring: Boolean = true,
): Modifier =
    composed {
        var pressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec =
                if (spring) {
                    trakrSpring()
                } else {
                    tween(90)
                },
            label = "pressScale",
        )
        this
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    }
