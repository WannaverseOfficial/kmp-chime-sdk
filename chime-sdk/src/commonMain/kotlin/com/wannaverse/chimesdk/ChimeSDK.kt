package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ChimeSDK {
    companion object {
        /**
         * Creates a meeting session and initializes the SDK. Call [joinMeeting] to connect to the session.
         *
         * @param externalMeetingId Your app-defined meeting identifier.
         * @param meetingId Chime meeting ID returned by CreateMeeting.
         * @param audioHostURL Media server host for audio (UDP/SRTP).
         * @param audioFallbackURL WebSocket fallback when UDP is blocked.
         * @param turnControlURL TURN credential endpoint.
         * @param signalingURL WebSocket signaling endpoint.
         * @param ingestionURL Client event ingestion endpoint.
         * @param attendeeId Chime attendee ID returned by CreateAttendee.
         * @param externalUserId Your app-defined user identifier.
         * @param joinToken Attendee join token returned by CreateAttendee.
         */
        fun createSession(
            externalMeetingId: String,
            meetingId: String,
            audioHostURL: String,
            audioFallbackURL: String,
            turnControlURL: String,
            signalingURL: String,
            ingestionURL: String,
            attendeeId: String,
            externalUserId: String,
            joinToken: String
        ): ChimeSDK
    }

    /**
     * Returns the currently available audio input devices such as microphones.
     *
     * The returned list reflects the devices detected by the current platform at
     * the time of the call and may change as hardware is connected or removed.
     */
    fun getAvailableInputDevices(): List<AudioDevice>

    /**
     * Returns the currently available audio output devices such as speakers and headsets.
     *
     * The returned list reflects the devices detected by the current platform at
     * the time of the call and may change as hardware is connected or removed.
     */
    fun getAvailableOutputDevices(): List<AudioDevice>

    /**
     * Joins a Chime meeting and starts audio/video with default parameters. Call [createSession] first.
     *
     * @param realTimeListener Callbacks for attendee presence, mute, and volume events.
     * @param onActiveSpeakersChanged Invoked with the set of currently active speaker attendee IDs
     * @param onConnectionStatusChanged Invoked when the session connection status changes.
     * @param onRemoteVideoAvailable Invoked when remote video availability or source count changes.
     * @param onCameraSendAvailable Invoked when the ability to send local camera video changes.
     * @param onSessionError Invoked on session errors; [isRecoverable] indicates whether the SDK will retry.
     * @param onVideoNeedsRestart Invoked when the video session must be restarted by the caller.
     * @param selectedAudioInputDevice [AudioDevice.label] of the audio input device to use, or null to use the platform default.
     * @param isJoiningOnMute Whether to join with the microphone muted. Defaults to false.
     * @param onLocalTileAdded Invoked with the local video tile ID once the local tile is bound, or null if unavailable.
     */
    fun joinMeeting(
        realTimeListener: RealTimeEventListener,
        onActiveSpeakersChanged: (Set<String>) -> Unit,
        onConnectionStatusChanged: (ConnectionStatus) -> Unit,
        onRemoteVideoAvailable: (Boolean, Int) -> Unit,
        onCameraSendAvailable: (Boolean) -> Unit,
        onSessionError: (String, Boolean) -> Unit,
        onVideoNeedsRestart: () -> Unit,
        selectedAudioInputDevice: String?,
        isJoiningOnMute: Boolean,
        onLocalTileAdded: (Int) -> Unit,
        onLocalTileRemoved: () -> Unit,
        onRemoteTileAdded: (Int) -> Unit,
        onRemoteTileRemoved: () -> Unit
    )

    /** Ends the active meeting session and releases all resources. */
    fun leaveMeeting()

    /** Starts capturing and sending local camera video. */
    fun startLocalVideo()

    /** Stops capturing and sending local camera video. */
    fun stopLocalVideo()

    /** Composable that renders the local camera preview. */
    @Composable
    fun LocalVideoView(cameraFacing: CameraFacing, isOnTop: Boolean, modifier: Modifier = Modifier)

    /** Composable that renders a remote participant's video tile. */
    @Composable
    fun RemoteVideoView(tileId: Int, isOnTop: Boolean, modifier: Modifier = Modifier)

    /**
     * Broadcasts a real-time data message on [topic].
     *
     * @param topic Destination topic string.
     * @param data UTF-8 payload, max 2 KB.
     * @param lifetimeMs How long the message is replayed to late joiners (ms). 0 means no replay.
     */
    fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long = 0)

    /**
     * Mutes or unmutes the local microphone.
     *
     * @return true if the operation succeeded.
     */
    fun setMute(shouldMute: Boolean): Boolean

    /** Toggles between front and back cameras. */
    fun switchCamera()

    /**
     * Routes audio output to the given device.
     *
     * @param device target device, or null to use the platform default.
     */
    fun switchAudioDevice(device: AudioDevice?)

    /**
     * Subscribes to incoming data messages on [topic]. Call after [joinMeeting].
     *
     * @param topic Topic to subscribe to.
     * @param listener Invoked on the main thread for each received [TextMessage].
     */
    fun subscribeToTopic(topic: String, listener: (ChimeMessage) -> Unit)

    /**
     * Unsubscribes from data messages on [topic].
     *
     * @param topic Topic previously passed to [subscribeToTopic].
     */
    fun unsubscribeFromTopic(topic: String)
}
