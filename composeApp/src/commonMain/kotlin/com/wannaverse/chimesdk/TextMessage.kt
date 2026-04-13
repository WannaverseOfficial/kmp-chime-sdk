package com.wannaverse.chimesdk

data class TextMessage(
    val senderId: String,
    val content: String,
    val timestamp: Long
)