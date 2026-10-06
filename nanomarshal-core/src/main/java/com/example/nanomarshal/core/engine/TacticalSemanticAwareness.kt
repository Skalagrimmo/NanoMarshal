package com.example.nanomarshal.core.engine

/**
 * Converts semantic world pressure into bounded tactical-perception modifiers.
 *
 * These values are derived inputs for NanoMarshal only; they never redefine the
 * objective world or emit mutations back to PixelGen.
 */
data class TacticalPerceptionModifiers(
    val alertFloor: Float,
    val visionMultiplier: Float,
    val hearingMultiplier: Float
)

fun TacticalSemanticState.toPerceptionModifiers(): TacticalPerceptionModifiers {
    val normalizedAlert = (alertLevel / 5f).coerceIn(0f, 1f)
    val normalizedFrontPressure = (frontPressure / 100f).coerceIn(0f, 1f)
    val normalizedTerritoryInstability =
        ((-territoryControl).coerceAtLeast(0) / 100f).coerceIn(0f, 1f)

    return TacticalPerceptionModifiers(
        alertFloor = (
            normalizedAlert * 20f +
                normalizedFrontPressure * 15f +
                normalizedTerritoryInstability * 10f
            ).coerceIn(0f, 45f),
        visionMultiplier = (
            1f +
                normalizedAlert * 0.10f +
                normalizedFrontPressure * 0.15f +
                normalizedTerritoryInstability * 0.10f
            ).coerceIn(1f, 1.35f),
        hearingMultiplier = (
            1f +
                normalizedAlert * 0.10f +
                normalizedFrontPressure * 0.10f +
                normalizedTerritoryInstability * 0.20f
            ).coerceIn(1f, 1.40f)
    )
}
