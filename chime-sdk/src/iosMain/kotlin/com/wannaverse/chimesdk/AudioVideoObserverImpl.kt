package com.wannaverse.chimesdk

import swiftPMImport.com.wannaverse.chime.sdk.AudioVideoObserverProtocol
import swiftPMImport.com.wannaverse.chime.sdk.DefaultMeetingSession
import swiftPMImport.com.wannaverse.chime.sdk.MeetingSessionStatus
import swiftPMImport.com.wannaverse.chime.sdk.MeetingSessionStatusCodeAudioCallEnded
import swiftPMImport.com.wannaverse.chime.sdk.MeetingSessionStatusCodeAudioDisconnectAudio
import swiftPMImport.com.wannaverse.chime.sdk.MeetingSessionStatusCodeAudioJoinedFromAnotherDevice
import swiftPMImport.com.wannaverse.chime.sdk.MeetingSessionStatusCodeOk
import swiftPMImport.com.wannaverse.chime.sdk.MeetingSessionStatusCodeVideoAtCapacityViewOnly
import swiftPMImport.com.wannaverse.chime.sdk.RemoteVideoSource
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class AudioVideoObserverImpl(
    private val meetingSession: DefaultMeetingSession,
    private val onConnectionStatusChanged: (ConnectionStatus) -> Unit,
    private val onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit,
    private val onCameraSendAvailable: (available: Boolean) -> Unit,
    private val onSessionError: (message: String, isRecoverable: Boolean) -> Unit,
    private val isJoiningOnMute: Boolean
) : NSObject(),
    AudioVideoObserverProtocol {
    init {
        ProtocolDescriptor(
            candidates = listOf("AudioVideoObserver", "_TtP14AmazonChimeSDK18AudioVideoObserver_")
        ).forceRegisterProtocol(this)
    }

    private val remoteVideoSources: MutableMap<String, RemoteVideoSource> = mutableMapOf()

    override fun audioSessionDidStartConnectingWithReconnecting(reconnecting: Boolean) {
        onConnectionStatusChanged(
            if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING
        )
    }

    override fun audioSessionDidStartWithReconnecting(reconnecting: Boolean) {
        onConnectionStatusChanged(ConnectionStatus.CONNECTED)

        if (!reconnecting && isJoiningOnMute) meetingSession.audioVideo().realtimeLocalMute()
    }

    override fun audioSessionDidDrop() {
        onConnectionStatusChanged(ConnectionStatus.RECONNECTING)
        onSessionError("Audio dropped, reconnecting...", true)
    }

    override fun audioSessionDidStopWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {
        onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)

        val message = when (sessionStatus.statusCode()) {
            MeetingSessionStatusCodeOk -> "Meeting ended"
            MeetingSessionStatusCodeAudioJoinedFromAnotherDevice -> "Joined from another device"
            MeetingSessionStatusCodeAudioDisconnectAudio -> "Disconnected remotely"
            MeetingSessionStatusCodeAudioCallEnded -> "Session ended: AudioCallEnded"
            else -> "Session ended: ${sessionStatus.statusCode()}"
        }
        onSessionError(message, false)
    }

    override fun audioSessionDidCancelReconnect() {
        onConnectionStatusChanged(ConnectionStatus.ERROR)
        onSessionError("Failed to reconnect", false)
    }

    override fun connectionDidBecomePoor() {
        onConnectionStatusChanged(ConnectionStatus.POOR_CONNECTION)
    }

    override fun connectionDidRecover() {
        onConnectionStatusChanged(ConnectionStatus.CONNECTED)
    }

    override fun videoSessionDidStartConnecting() {}

    override fun videoSessionDidStartWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {
        if (sessionStatus.statusCode() == MeetingSessionStatusCodeVideoAtCapacityViewOnly) {
            onSessionError("Video at capacity. View only.", false)
        }
    }

    override fun videoSessionDidStopWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {}

    override fun remoteVideoSourcesDidBecomeAvailableWithSources(sources: List<*>) {
        val newSources = sources.filterIsInstance<RemoteVideoSource>()

        newSources.forEach { remoteVideoSources[it.attendeeId()] = it }

        onRemoteVideoAvailable(true, newSources.size)
    }

    override fun remoteVideoSourcesDidBecomeUnavailableWithSources(sources: List<*>) {
        val unavailable = sources.filterIsInstance<RemoteVideoSource>()
        unavailable.forEach { remoteVideoSources.remove(it.attendeeId()) }
        onRemoteVideoAvailable(false, unavailable.size)
    }

    override fun cameraSendAvailabilityDidChangeWithAvailable(available: Boolean) {
        onCameraSendAvailable(available)
    }
}
