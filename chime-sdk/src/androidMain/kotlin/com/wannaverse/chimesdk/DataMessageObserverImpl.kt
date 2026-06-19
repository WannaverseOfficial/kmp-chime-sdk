package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessage
import com.amazonaws.services.chime.sdk.meetings.realtime.datamessage.DataMessageObserver
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSession

class DataMessageObserverImpl(private val meetingSession: MeetingSession) : DataMessageObserver {
    val listeners = mutableMapOf<String, (ChimeMessage) -> Unit>()

    fun addListener(topic: String, listener: (ChimeMessage) -> Unit) {
        listeners[topic] = listener
        meetingSession.audioVideo.addRealtimeDataMessageObserver(topic = topic, observer = this)
    }

    fun removeListener(topic: String) {
        listeners.remove(topic)
        meetingSession.audioVideo.removeRealtimeDataMessageObserverFromTopic(topic = topic)
    }

    fun clearListeners() {
        listeners.keys.forEach(meetingSession.audioVideo::removeRealtimeDataMessageObserverFromTopic)
        listeners.clear()
    }

    override fun onDataMessageReceived(dataMessage: DataMessage) = listeners[dataMessage.topic]!!(
        ChimeMessage(
            senderId = dataMessage.senderAttendeeId,
            content = dataMessage.text(),
            timestamp = dataMessage.timestampMs
        )
    )
}