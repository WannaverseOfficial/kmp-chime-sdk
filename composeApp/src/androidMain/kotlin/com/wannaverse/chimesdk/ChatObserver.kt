package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessage
import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessageObserver

class ChatObserver(
    private val onChatMessageReceived: (TextMessage) -> Unit,
    private val onEmojiReceived: (TextMessage) -> Unit,
    private val onSystemMessage: (TextMessage) -> Unit
) : DataMessageObserver {

    override fun onDataMessageReceived(dataMessage: DataMessage) {
        val message = TextMessage(
            senderId = dataMessage.senderAttendeeId,
            content = dataMessage.text(),
            timestamp = dataMessage.timestampMs
        )
        when (dataMessage.topic) {
            "chat" -> onChatMessageReceived(message)
            "emoji" -> onEmojiReceived(message)
            "system" -> onSystemMessage(message)
        }
    }
}
