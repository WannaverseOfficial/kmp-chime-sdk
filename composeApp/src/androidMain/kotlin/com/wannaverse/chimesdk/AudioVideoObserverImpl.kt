package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.AudioVideoObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.RemoteVideoSource
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatus
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionStatusCode

class AudioVideoObserverImpl(
    private val onConnectionStatusChanged: (ConnectionStatus) -> Unit,
    private val onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit,
    private val onCameraSendAvailable: (available: Boolean) -> Unit,
    private val onSessionError: (message: String, isRecoverable: Boolean) -> Unit,
    private val onVideoNeedsRestart: () -> Unit,
    private val isJoiningOnMute: Boolean
) : AudioVideoObserver {

    private var isVideoSessionActive = false
    private var hasReportedPoorConnection = false
    private var shouldRestartVideoAfterReconnect = false

    override fun onAudioSessionStartedConnecting(reconnecting: Boolean) {
        if (reconnecting) {
            shouldRestartVideoAfterReconnect = isVideoSessionActive
            onConnectionStatusChanged(ConnectionStatus.RECONNECTING)
        } else {
            onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        }
    }

    override fun onAudioSessionStarted(reconnecting: Boolean) {
        onConnectionStatusChanged(ConnectionStatus.CONNECTED)

        if (!reconnecting && isJoiningOnMute) {
            meetingSession?.audioVideo?.realtimeLocalMute()
        }

        if (reconnecting && shouldRestartVideoAfterReconnect && !isVideoSessionActive) {
            onVideoNeedsRestart()
            shouldRestartVideoAfterReconnect = false
        }
    }

    override fun onAudioSessionDropped() {
        shouldRestartVideoAfterReconnect = isVideoSessionActive
        onConnectionStatusChanged(ConnectionStatus.RECONNECTING)
        onSessionError("Audio connection lost, reconnecting...", true)
    }

    override fun onAudioSessionStopped(sessionStatus: MeetingSessionStatus) {
        shouldRestartVideoAfterReconnect = false
        onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)

        val message = when (sessionStatus.statusCode) {
            MeetingSessionStatusCode.OK -> "Meeting ended"
            MeetingSessionStatusCode.AudioServerHungup -> "Audio server disconnected"
            MeetingSessionStatusCode.AudioJoinedFromAnotherDevice -> "Joined from another device"
            MeetingSessionStatusCode.AudioDisconnectAudio -> "Disconnected remotely"
            else -> "Session ended: ${sessionStatus.statusCode?.name ?: "unknown"}"
        }
        onSessionError(message, false)
    }

    override fun onAudioSessionCancelledReconnect() {
        shouldRestartVideoAfterReconnect = false
        onConnectionStatusChanged(ConnectionStatus.ERROR)
        onSessionError("Failed to reconnect to audio", false)
    }

    override fun onConnectionBecamePoor() {
        hasReportedPoorConnection = true
        onConnectionStatusChanged(ConnectionStatus.POOR_CONNECTION)
    }

    override fun onConnectionRecovered() {
        if (hasReportedPoorConnection) {
            hasReportedPoorConnection = false
            onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        }
    }

    override fun onVideoSessionStartedConnecting() {}

    override fun onVideoSessionStarted(sessionStatus: MeetingSessionStatus) {
        isVideoSessionActive = true
        if (sessionStatus.statusCode == MeetingSessionStatusCode.VideoAtCapacityViewOnly) {
            onSessionError("Video at capacity. View only mode.", false)
        }
    }

    override fun onVideoSessionStopped(sessionStatus: MeetingSessionStatus) {
        isVideoSessionActive = false
        if (sessionStatus.statusCode != MeetingSessionStatusCode.OK) {
            onSessionError("Video ended: ${sessionStatus.statusCode?.name ?: "unknown"}", false)
        }
    }

    override fun onRemoteVideoSourceAvailable(sources: List<RemoteVideoSource>) {
        onRemoteVideoAvailable(true, sources.size)
    }

    override fun onRemoteVideoSourceUnavailable(sources: List<RemoteVideoSource>) {
        onRemoteVideoAvailable(false, sources.size)
    }

    override fun onCameraSendAvailabilityUpdated(available: Boolean) {
        onCameraSendAvailable(available)
    }
}
