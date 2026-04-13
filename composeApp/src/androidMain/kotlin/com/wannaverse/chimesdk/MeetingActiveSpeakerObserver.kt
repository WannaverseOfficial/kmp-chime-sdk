package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerdetector.ActiveSpeakerObserver

object MeetingActiveSpeakerObserver : ActiveSpeakerObserver {
    private const val SPEAKING_THRESHOLD = 0.05
    var onActiveSpeakersChanged: ((Set<String>) -> Unit)? = null

    override val scoreCallbackIntervalMs: Int = 500

    override fun onActiveSpeakerDetected(attendeeInfo: Array<AttendeeInfo>) {}

    override fun onActiveSpeakerScoreChanged(scores: Map<AttendeeInfo, Double>) {
        if (scores.isEmpty()) return
        val activeSpeakers = scores
            .filter { (_, score) -> score > SPEAKING_THRESHOLD }
            .map { (attendee, _) -> attendee.externalUserId }
            .toSet()
        onActiveSpeakersChanged?.invoke(activeSpeakers)
    }
}
