package com.example.engine

import com.example.nanomarshal.core.engine.TacticalPerceptionModifiers
import com.example.nanomarshal.core.engine.TacticalSemanticState
import com.example.nanomarshal.core.engine.toPerceptionModifiers
import org.junit.Assert.assertEquals
import org.junit.Test

class TacticalSemanticAwarenessTest {

    @Test
    fun calmSemanticStateHasNeutralModifiers() {
        assertEquals(
            TacticalPerceptionModifiers(0f, 1f, 1f),
            TacticalSemanticState().toPerceptionModifiers()
        )
    }

    @Test
    fun semanticPressureRaisesBoundedAwareness() {
        val modifiers = TacticalSemanticState(
            frontPressure = 100,
            territoryControl = -100,
            alertLevel = 5
        ).toPerceptionModifiers()

        assertEquals(45f, modifiers.alertFloor)
        assertEquals(1.35f, modifiers.visionMultiplier)
        assertEquals(1.40f, modifiers.hearingMultiplier)
    }

    @Test
    fun strongTerritoryControlDoesNotCreateInstabilityBonus() {
        val modifiers = TacticalSemanticState(
            territoryControl = 100
        ).toPerceptionModifiers()

        assertEquals(0f, modifiers.alertFloor)
        assertEquals(1f, modifiers.visionMultiplier)
        assertEquals(1f, modifiers.hearingMultiplier)
    }
}
