package com.example.nanomarshal.core.engine

/**
 * Objective-world event accepted by the tactical runtime boundary.
 *
 * The tactical runtime may interpret this event later through explicit handlers,
 * but it never becomes the owner of canonical semantic-world truth.
 */
data class SemanticTacticalEvent(
    val eventId: String,
    val worldId: String,
    val revision: Long,
    val kind: String,
    val payloadJson: String
)
