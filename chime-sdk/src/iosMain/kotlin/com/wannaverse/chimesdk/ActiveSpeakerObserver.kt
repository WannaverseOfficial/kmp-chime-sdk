package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.ActiveSpeakerObserverProtocol
import cocoapods.AmazonChimeSDK.AttendeeInfo
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSInteger

@OptIn(ExperimentalForeignApi::class)
class ActiveSpeakerObserver(val onActiveSpeakersChanged: (Set<String>) -> Unit) :
    ActiveSpeakerObserverProtocol {
    companion object Companion {
        private const val SPEAKING_THRESHOLD = 0.05
    }

    init {
        val _this: ActiveSpeakerObserverProtocol = this

        ProtocolDescriptor(
            candidates = listOf("ActiveSpeakerObserver", "_TtP14AmazonChimeSDK21ActiveSpeakerObserver_")
        ).forceRegisterProtocol(this)
    }

    override fun observerId(): String = "ActiveSpeakerObserver"

    override fun scoresCallbackIntervalMs(): NSInteger = 500

    override fun activeSpeakerDidDetectWithAttendeeInfo(attendeeInfo: List<*>) {}

    override fun activeSpeakerScoreDidChangeWithScores(scores: Map<Any?, *>) =
        scores.mapKeys { (attendee) -> (attendee as AttendeeInfo).externalUserId() }
            .filter { (_, score) -> (score as Double) > SPEAKING_THRESHOLD }
            .keys
            .let(onActiveSpeakersChanged)

}
