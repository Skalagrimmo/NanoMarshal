package com.example.engine

import com.example.nanomarshal.core.engine.SemanticTacticalEvent
import com.example.nanomarshal.core.engine.SemanticTacticalEventHandlerRegistry
import com.example.nanomarshal.core.engine.TacticalSemanticState
import org.junit.Assert.assertEquals
import org.junit.Test

class SemanticTacticalEventHandlerRegistryTest {

    private val registry = SemanticTacticalEventHandlerRegistry.default()

    @Test
    fun handlesSupportedWorldEventsIntoTacticalState() {
        var state = TacticalSemanticState()

        state = registry.handle(event("front_escalation", """{"delta":7}""", 1L), state)
        state = registry.handle(event("territory_strengthen", """{"amount":4}""", 2L), state)
        state = registry.handle(event("territory_alert", """{"level":2}""", 3L), state)
        state = registry.handle(event("front_deescalation", """{"delta":2}""", 4L), state)
        state = registry.handle(event("territory_weaken", """{"delta":1}""", 5L), state)

        assertEquals(5, state.frontPressure)
        assertEquals(3, state.territoryControl)
        assertEquals(2, state.alertLevel)
        assertEquals("event-5", state.lastEventId)
        assertEquals(5L, state.lastAppliedRevision)
    }

    @Test
    fun clampsDerivedTacticalState() {
        var state = TacticalSemanticState()

        state = registry.handle(event("front_escalation", """{"delta":999}""", 1L), state)
        state = registry.handle(event("territory_weaken", """{"delta":999}""", 2L), state)
        state = registry.handle(event("territory_alert", """{"level":999}""", 3L), state)

        assertEquals(100, state.frontPressure)
        assertEquals(-100, state.territoryControl)
        assertEquals(5, state.alertLevel)
    }

    @Test
    fun unknownEventKindHasNoTacticalInterpretation() {
        val initial = TacticalSemanticState(
            frontPressure = 4,
            territoryControl = -3,
            alertLevel = 2,
            lastEventId = "prior",
            lastAppliedRevision = 9L
        )

        val next = registry.handle(
            event("weather_shift", """{"delta":99}""", 10L),
            initial
        )

        assertEquals(initial, next)
    }

    @Test
    fun exposesExplicitSupportedKinds() {
        assertEquals(
            setOf(
                "front_escalation",
                "front_deescalation",
                "territory_strengthen",
                "territory_weaken",
                "territory_alert"
            ),
            registry.supportedKinds()
        )
    }

    private fun event(
        kind: String,
        payloadJson: String,
        revision: Long
    ) = SemanticTacticalEvent(
        eventId = "event-$revision",
        worldId = "mission-1",
        revision = revision,
        kind = kind,
        payloadJson = payloadJson
    )
}
