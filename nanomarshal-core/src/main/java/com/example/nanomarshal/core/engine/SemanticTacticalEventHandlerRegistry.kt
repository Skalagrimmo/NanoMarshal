package com.example.nanomarshal.core.engine

/**
 * Tactical interpretation of semantic world pressure.
 *
 * This is derived state owned by NanoMarshal. It is not an alternative source
 * of objective-world truth and never writes back to PixelGen.
 */
data class TacticalSemanticState(
    val frontPressure: Int = 0,
    val territoryControl: Int = 0,
    val alertLevel: Int = 0,
    val lastEventId: String? = null,
    val lastAppliedRevision: Long = -1L
)

/**
 * Interprets one already-authorized semantic event into tactical-only state.
 */
fun interface SemanticTacticalEventHandler {
    fun handle(
        event: SemanticTacticalEvent,
        current: TacticalSemanticState
    ): TacticalSemanticState
}

/**
 * Explicit registry of semantic event interpretations.
 *
 * Unknown event kinds are intentionally ignored here: the event remains in the
 * bounded event log, but tactical meaning is added only when a handler exists.
 */
class SemanticTacticalEventHandlerRegistry(
    private val handlers: Map<String, SemanticTacticalEventHandler>
) {

    fun handle(
        event: SemanticTacticalEvent,
        current: TacticalSemanticState
    ): TacticalSemanticState {
        val handler = handlers[event.kind] ?: return current
        return handler.handle(event, current).copy(
            lastEventId = event.eventId,
            lastAppliedRevision = maxOf(current.lastAppliedRevision, event.revision)
        )
    }

    fun supportedKinds(): Set<String> = handlers.keys

    companion object {
        fun default(): SemanticTacticalEventHandlerRegistry =
            SemanticTacticalEventHandlerRegistry(
                handlers = mapOf(
                    "front_escalation" to SemanticTacticalEventHandler { event, state ->
                        state.copy(
                            frontPressure = (state.frontPressure + eventDelta(event)).coerceIn(0, 100)
                        )
                    },
                    "front_deescalation" to SemanticTacticalEventHandler { event, state ->
                        state.copy(
                            frontPressure = (state.frontPressure - eventDelta(event)).coerceIn(0, 100)
                        )
                    },
                    "territory_strengthen" to SemanticTacticalEventHandler { event, state ->
                        state.copy(
                            territoryControl = (state.territoryControl + eventDelta(event)).coerceIn(-100, 100)
                        )
                    },
                    "territory_weaken" to SemanticTacticalEventHandler { event, state ->
                        state.copy(
                            territoryControl = (state.territoryControl - eventDelta(event)).coerceIn(-100, 100)
                        )
                    },
                    "territory_alert" to SemanticTacticalEventHandler { event, state ->
                        val level = payloadInt(event.payloadJson, "level")
                            ?: (state.alertLevel + eventDelta(event))

                        state.copy(
                            alertLevel = level.coerceIn(0, 5)
                        )
                    }
                )
            )

        private fun eventDelta(event: SemanticTacticalEvent): Int =
            payloadInt(event.payloadJson, "delta")
                ?: payloadInt(event.payloadJson, "amount")
                ?: 1

        private fun payloadInt(payloadJson: String, key: String): Int? =
            Regex("""\"$key\"\s*:\s*(-?\d+)""")
                .find(payloadJson)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
    }
}
