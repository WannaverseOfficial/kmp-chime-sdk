package com.wannaverse.chimesdk

/**
 * Represents a user chat message sent during a meeting.
 *
 * @property senderId Unique ID of the message sender.
 * @property content Text content of the message.
 * @property timestamp Time the message was sent (in epoch milliseconds).
 */
data class ChimeMessage(
    val senderId: String,
    val content: String,
    val timestamp: Long
)