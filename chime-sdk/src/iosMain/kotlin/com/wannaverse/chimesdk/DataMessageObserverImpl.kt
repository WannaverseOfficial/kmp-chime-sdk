package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.DataMessage
import cocoapods.AmazonChimeSDK.DataMessageObserverProtocol
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class DataMessageObserverImpl(private val listener: (ChimeMessage) -> Unit) : NSObject(), DataMessageObserverProtocol {
    init {
        val _this: DataMessageObserverProtocol = this
    }

    override fun dataMessageDidReceivedWithDataMessage(dataMessage: DataMessage) = listener(
        ChimeMessage(
            senderId = dataMessage.senderAttendeeId(),
            content = dataMessage.text() ?: "",
            timestamp = dataMessage.timestampMs()
        )
    )
}