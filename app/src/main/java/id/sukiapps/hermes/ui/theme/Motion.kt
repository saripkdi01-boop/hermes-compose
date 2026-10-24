package id.sukiapps.hermes.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

// Hermes Premium — gerak 180-280ms ease-out; spring TANPA pantulan
// (aturan masterprompt: dilarang animasi memantul berlebihan).
object HermesMotion {
    val QuickOut: FiniteAnimationSpec<Float> = tween(
        durationMillis = 180,
        easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    )
    val StandardOut: FiniteAnimationSpec<Float> = tween(
        durationMillis = 280,
        easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    )
    val SoftSpring: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val TabIndicatorOffset: FiniteAnimationSpec<IntOffset> = tween(
        durationMillis = 220,
        easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    )
}
