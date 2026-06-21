package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.DataMessage
import cocoapods.AmazonChimeSDK.DataMessageObserverProtocol
import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class DataMessageObserverImpl(private val meetingSession: DefaultMeetingSession) : NSObject(), DataMessageObserverProtocol {
    init {
        val _this: DataMessageObserverProtocol = this

        ProtocolDescriptor(
            candidates = listOf("DataMessageObserver", "_TtP14AmazonChimeSDK19DataMessageObserver_")
        ).forceRegisterProtocol(this)
    }

    val listeners = mutableMapOf<String, (ChimeMessage) -> Unit>()

    fun addListener(topic: String, listener: (ChimeMessage) -> Unit) {
        listeners[topic] = listener
        meetingSession.audioVideo().addRealtimeDataMessageObserverWithTopic(topic = topic, observer = this)
    }

    fun removeListener(topic: String) {
        listeners.remove(topic)
        meetingSession.audioVideo().removeRealtimeDataMessageObserverFromTopicWithTopic(topic = topic)
    }

    fun clearListeners() {
        listeners.keys.forEach(meetingSession.audioVideo()::removeRealtimeDataMessageObserverFromTopicWithTopic)
        listeners.clear()
    }

    override fun dataMessageDidReceivedWithDataMessage(dataMessage: DataMessage) = listeners[dataMessage.topic()]!!(
        ChimeMessage(
            senderId = dataMessage.senderAttendeeId(),
            content = dataMessage.text() ?: "",
            timestamp = dataMessage.timestampMs()
        )
    )
}