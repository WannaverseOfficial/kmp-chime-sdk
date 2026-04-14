package com.wannaverse.chimesdk

/** A real-time data message received on a subscribed topic. */
data class TextMessage(
    /** Topic the message was published to. */
    val topic: String,
    /** Attendee ID of the sender. */
    val senderId: String,
    /** UTF-8 message payload. */
    val content: String,
    /** Server-assigned timestamp in milliseconds since epoch. */
    val timestamp: Long
)
