package com.example.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized motion tokens and easing curves for physical card movements.
 *
 * Designed to provide a tactile, weighted, casino-grade "premium" feel while
 * maintaining strict 60 FPS (16.6ms) frame budgets by avoiding layout-recalculating
 * interpolations and pre-allocating specs for zero-garbage animation cycles.
 */
object MotionConstants {

    // =========================================================================
    // 1. Custom CubicBezierEasing Curves for Physical Card Interactions
    // =========================================================================

    /**
     * Physical entry of a card played onto the table felt.
     * Starts briskly from the player's hand and decelerates with a gentle, cushioned
     * slide into its final resting spot on the cloth.
     */
    val CardEntranceEasing: CubicBezierEasing = CubicBezierEasing(0.20f, 0.0f, 0.20f, 1.0f)

    /**
     * High-speed flight trajectory when dealing cards outward from the center deck.
     * Snappy launch with an asymptotic landing that snaps into player position.
     */
    val CardDealFlightEasing: CubicBezierEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

    /**
     * Tension and cascade curve for the riffle bridge shuffle.
     * Accentuates the upward flex of the deck halves before a rapid, smooth release.
     */
    val CardShuffleBridgeEasing: CubicBezierEasing = CubicBezierEasing(0.20f, 0.80f, 0.40f, 1.0f)

    /**
     * Magnetic suction curve that pulls played cards into a compact center stack
     * before collection.
     */
    val CardGatherEasing: CubicBezierEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

    /**
     * Deliberate, sweeping glide as trick cards are claimed and swept toward the winner.
     */
    val CardCollectEasing: CubicBezierEasing = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)

    /**
     * Tactile lift curve when hovering or selecting a card in hand.
     * Subtle overshoot provides an immediate responsive feel to touch input.
     */
    val CardLiftEasing: CubicBezierEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    /**
     * Graceful deceleration curve for fanning cards during the opening brand sequence.
     */
    val CardFanEasing: CubicBezierEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)

    /**
     * Fast return snap for cancelled card drops or resets.
     */
    val CardSnapBackEasing: CubicBezierEasing = CubicBezierEasing(0.25f, 1.0f, 0.50f, 1.0f)

    // =========================================================================
    // 2. Standardized Animation Durations (ms) - Tuned for Frame Budgets
    // =========================================================================

    const val DurationCardDealMs = 370
    const val DurationCardEntranceMs = 1150
    const val DurationCardGatherMs = 600
    const val DurationCardCollectMs = 1200
    const val DurationCardFadeOutMs = 400
    const val DurationCardLiftMs = 250
    const val DurationCardShuffleCutMs = 300
    const val DurationCardBridgeArchMs = 500
    const val DurationCardBridgeReleaseMs = 450
    const val DurationCardFanMs = 1100
    const val DurationTextFadeMs = 600
    const val DurationPulseCycleMs = 800

    // =========================================================================
    // 3. Pre-allocated Reusable Animation Specs (Zero Allocation in Hot Paths)
    // =========================================================================

    /** Card entrance movement on the felt */
    val CardEntranceSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardEntranceMs,
        easing = CardEntranceEasing
    )

    /** Card rotation settling during entrance */
    val CardRotationSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardEntranceMs,
        easing = CardEntranceEasing
    )

    /** In-flight card deal animation from deck to seat */
    val CardDealSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardDealMs,
        easing = CardDealFlightEasing
    )

    /** Trick cards gather towards center */
    val CardGatherSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardGatherMs,
        easing = CardGatherEasing
    )

    /** Trick collection glide to winner */
    val CardCollectSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardCollectMs,
        easing = CardCollectEasing
    )

    /** Trick fade-out after collection */
    val CardFadeOutSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardFadeOutMs,
        easing = LinearEasing
    )

    /** Card vertical lift when selected */
    val CardLiftDpSpec: TweenSpec<Dp> = tween(
        durationMillis = DurationCardLiftMs,
        easing = CardLiftEasing
    )

    /** Card elevation shadow change when selected */
    val CardElevationDpSpec: TweenSpec<Dp> = tween(
        durationMillis = DurationCardLiftMs,
        easing = CardLiftEasing
    )

    /** Opening fan spread */
    val CardFanSpec: TweenSpec<Float> = tween(
        durationMillis = DurationCardFanMs,
        easing = CardFanEasing
    )

    /** Opening text fade */
    val TextFadeSpec: TweenSpec<Float> = tween(
        durationMillis = DurationTextFadeMs,
        easing = FastOutSlowInEasing
    )

    /** Spring spec for interactive dragging / scaling */
    val HandScaleSpringSpec: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
