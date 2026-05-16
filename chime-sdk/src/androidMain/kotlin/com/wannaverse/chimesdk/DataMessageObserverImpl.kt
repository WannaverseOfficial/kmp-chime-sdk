package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessage
import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessageObserver

class DataMessageObserverImpl(private val listener: (ChimeMessage) -> Unit) : DataMessageObserver {
    override fun onDataMessageReceived(dataMessage: DataMessage) = listener(
        ChimeMessage(
            senderId = dataMessage.senderAttendeeId,
            content = dataMessage.text(),
            timestamp = dataMessage.timestampMs
        )
    )
}