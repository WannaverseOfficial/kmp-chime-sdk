package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerdetector.ActiveSpeakerObserver

class ActiveSpeakerObserver(val onActiveSpeakersChanged: (Set<String>) -> Unit) : ActiveSpeakerObserver {
    companion object Companion {
        private const val SPEAKING_THRESHOLD = 0.05
    }

    override val scoreCallbackIntervalMs: Int = 500

    override fun onActiveSpeakerDetected(attendeeInfo: Array<AttendeeInfo>) {}

    override fun onActiveSpeakerScoreChanged(scores: Map<AttendeeInfo, Double>) =
        scores.mapKeys { (attendee) -> attendee.externalUserId }
            .filter { (_, score) -> score > SPEAKING_THRESHOLD }
            .keys
            .let(onActiveSpeakersChanged)
}
