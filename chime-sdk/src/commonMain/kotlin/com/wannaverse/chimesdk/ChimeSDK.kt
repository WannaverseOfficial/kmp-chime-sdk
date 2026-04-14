package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Joins a Chime meeting and starts audio/video.
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
 * @param realTimeListener Callbacks for attendee presence, mute, and volume events.
 * @param onActiveSpeakersChanged Invoked with the set of currently active speaker attendee IDs.
 * @param cameraFacing Initial camera facing direction. Defaults to [CameraFacing.FRONT].
 * @param onLocalVideoTileAdded Invoked with the local video tile ID once the local tile is bound, or null if unavailable.
 * @param onConnectionStatusChanged Invoked when the session connection status changes.
 * @param onRemoteVideoAvailable Invoked when remote video availability or source count changes.
 * @param onCameraSendAvailable Invoked when the ability to send local camera video changes.
 * @param onSessionError Invoked on session errors; [isRecoverable] indicates whether the SDK will retry.
 * @param onVideoNeedsRestart Invoked when the video session must be restarted by the caller.
 * @param onLocalVideoTileRemoved Invoked when the local video tile is unbound.
 * @param preferredAudioInputDeviceType Preferred audio input device type string. Pass null for the platform default.
 * @param onRemoteTileAdded Invoked with a tile ID when a remote video tile is added.
 * @param onRemoteTileRemoved Invoked with a tile ID when a remote video tile is removed.
 * @param isJoiningOnMute Whether to join with the microphone muted. Defaults to false.
 * @param onLocalAttendeeIdAvailable Invoked with the local attendee ID once the session is established.
 */
expect fun joinMeeting(
    externalMeetingId: String,
    meetingId: String,
    audioHostURL: String,
    audioFallbackURL: String,
    turnControlURL: String,
    signalingURL: String,
    ingestionURL: String,
    attendeeId: String,
    externalUserId: String,
    joinToken: String,
    realTimeListener: RealTimeEventListener,
    onActiveSpeakersChanged: (Set<String>) -> Unit,
    cameraFacing: CameraFacing = CameraFacing.FRONT,
    onLocalVideoTileAdded: ((Int?) -> Unit)? = null,
    onConnectionStatusChanged: (ConnectionStatus) -> Unit = {},
    onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit = { _, _ -> },
    onCameraSendAvailable: (available: Boolean) -> Unit = {},
    onSessionError: (message: String, isRecoverable: Boolean) -> Unit = { _, _ -> },
    onVideoNeedsRestart: () -> Unit = {},
    onLocalVideoTileRemoved: (() -> Unit)? = null,
    preferredAudioInputDeviceType: String? = null,
    onRemoteTileAdded: ((Int) -> Unit)? = null,
    onRemoteTileRemoved: ((Int) -> Unit)? = null,
    isJoiningOnMute: Boolean = false,
    onLocalAttendeeIdAvailable: (String) -> Unit = {}
)

/** Ends the active meeting session and releases all resources. */
expect fun leaveMeeting()

/** Starts capturing and sending local camera video. */
expect fun startLocalVideo()

/** Stops capturing and sending local camera video. */
expect fun stopLocalVideo()

/** Composable that renders the local camera preview. */
@Composable
expect fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean)

/** Composable that renders a remote participant's video tile. */
@Composable
expect fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean)

/**
 * Broadcasts a real-time data message on [topic].
 *
 * @param topic Destination topic string.
 * @param data UTF-8 payload, max 2 KB.
 * @param lifetimeMs How long the message is replayed to late joiners (ms). 0 means no replay.
 */
expect fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long = 0)

/**
 * Mutes or unmutes the local microphone.
 *
 * @return true if the operation succeeded.
 */
expect fun setMute(shouldMute: Boolean): Boolean

/** Toggles between front and back cameras. */
expect fun switchCamera()

/**
 * Routes audio output to the given device.
 *
 * @param deviceId [AudioDevice.id] of the target device, or null to use the platform default.
 */
expect fun switchAudioDevice(deviceId: String?)

/**
 * Subscribes to incoming data messages on [topic]. Call after [joinMeeting].
 *
 * @param topic Topic to subscribe to.
 * @param listener Invoked on the main thread for each received [TextMessage].
 */
expect fun subscribeToTopic(topic: String, listener: (TextMessage) -> Unit)

/**
 * Unsubscribes from data messages on [topic].
 *
 * @param topic Topic previously passed to [subscribeToTopic].
 */
expect fun unsubscribeFromTopic(topic: String)
