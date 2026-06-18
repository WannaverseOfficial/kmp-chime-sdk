@file:OptIn(ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import cocoapods.AmazonChimeSDK.ActiveSpeakerObserverProtocol
import cocoapods.AmazonChimeSDK.AttendeeInfo
import cocoapods.AmazonChimeSDK.AudioVideoObserverProtocol
import cocoapods.AmazonChimeSDK.ConsoleLogger
import cocoapods.AmazonChimeSDK.DefaultActiveSpeakerPolicy
import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.DefaultVideoRenderView
import cocoapods.AmazonChimeSDK.LogLevelINFO
import cocoapods.AmazonChimeSDK.MediaDevice
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBluetooth
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBuiltInSpeaker
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioHandset
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioWiredHeadset
import cocoapods.AmazonChimeSDK.MeetingSessionConfiguration
import cocoapods.AmazonChimeSDK.MeetingSessionCredentials
import cocoapods.AmazonChimeSDK.MeetingSessionStatus
import cocoapods.AmazonChimeSDK.MeetingSessionStatusCodeAudioCallEnded
import cocoapods.AmazonChimeSDK.MeetingSessionStatusCodeAudioDisconnectAudio
import cocoapods.AmazonChimeSDK.MeetingSessionStatusCodeAudioJoinedFromAnotherDevice
import cocoapods.AmazonChimeSDK.MeetingSessionStatusCodeOk
import cocoapods.AmazonChimeSDK.MeetingSessionStatusCodeVideoAtCapacityViewOnly
import cocoapods.AmazonChimeSDK.MeetingSessionURLs
import cocoapods.AmazonChimeSDK.RemoteVideoSource
import cocoapods.AmazonChimeSDK.VideoTileObserverProtocol
import cocoapods.AmazonChimeSDK.VideoTileState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothHFP
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVideoChat
import platform.AVFAudio.setActive
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRect
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIView
import platform.darwin.NSObject

private val logger = ConsoleLogger(name = "ChimeSDK", level = LogLevelINFO)

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession
) : NSObject(),
    VideoTileObserverProtocol {
    actual companion object {
        actual fun createSession(
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
        ): ChimeSDK {
            val credentials = MeetingSessionCredentials(
                attendeeId = attendeeId,
                externalUserId = externalUserId,
                joinToken = joinToken
            )

            val urls = MeetingSessionURLs(
                audioFallbackUrl = audioFallbackURL,
                audioHostUrl = audioHostURL,
                turnControlUrl = turnControlURL,
                signalingUrl = signalingURL,
                urlRewriter = { it },
                ingestionUrl = ingestionURL
            )

            val configuration = MeetingSessionConfiguration(
                meetingId = meetingId,
                externalMeetingId = externalMeetingId,
                credentials = credentials,
                urls = urls,
                urlRewriter = { it }
            )

            val meetingSession = DefaultMeetingSession(configuration = configuration, logger = logger)

            return ChimeSDK(meetingSession)
        }
    }

    private var isJoiningOnMute = false
    private var isRemoteVideoStarted = false
    private var isLocalVideoStarted = false

    private var localTileId: Long? = null
    private val remoteTiles: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()
    private val attendeeTileMap: MutableMap<String, Long> = mutableMapOf()

    val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView().also { it.setMirror(true) }
    internal val localVideoContainer: LocalVideoContainerView = LocalVideoContainerView(localRenderView).also { it.addSubview(localRenderView) }
    var onLocalVideoTileAdded: ((Int) -> Unit)? = null
    var onLocalVideoTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: ((Int) -> Unit)? = null
    var onRemoteTileRemoved: (() -> Unit)? = null
    var realTimeListener: RealTimeEventListener? = null

    init {
        @Suppress("UNUSED_VARIABLE") val _1: VideoTileObserverProtocol = this

        val observerProtocols = listOf(
            ProtocolDescriptor(
                candidates = listOf("VideoTileObserver", "_TtP14AmazonChimeSDK17VideoTileObserver_")
            )
        )

        observerProtocols.forEach { it.forceRegisterProtocol(this) }
    }

    actual fun getAvailableInputDevices(): List<AudioDevice> =
        meetingSession.audioVideo()
            .listAudioDevices()
            .mapNotNull { device ->
                if (device !is MediaDevice) return@mapNotNull null
                val type = when (device.type()) {
                    MediaDeviceTypeAudioBluetooth -> AudioDeviceType.BLUETOOTH
                    MediaDeviceTypeAudioWiredHeadset -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceTypeAudioHandset -> AudioDeviceType.BUILT_IN_MIC
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label()
                )
            }

    actual fun getAvailableOutputDevices(): List<AudioDevice> =
        meetingSession.audioVideo()
            .listAudioDevices()
            .mapNotNull { device ->
                if (device !is MediaDevice) return@mapNotNull null
                val type = when (device.type()) {
                    MediaDeviceTypeAudioBluetooth -> AudioDeviceType.BLUETOOTH
                    MediaDeviceTypeAudioWiredHeadset -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceTypeAudioBuiltInSpeaker -> AudioDeviceType.SPEAKER
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label()
                )
            }

    actual fun joinMeeting(
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
    ) {
        val realtimeObserver = RealTimeObserverImpl(realTimeListener)
        meetingSession.audioVideo().addRealtimeObserverWithObserver(realtimeObserver)

        val deviceObserver = DeviceObserver(meetingSession, realTimeListener)
        meetingSession.audioVideo().addDeviceChangeObserverWithObserver(deviceObserver)

        meetingSession.audioVideo().listAudioDevices()
            .filterIsInstance<MediaDevice>()
            .firstOrNull { it.label() == selectedAudioInputDevice }
            ?.let(deviceObserver::selectAudioDevice)

        this.onLocalVideoTileAdded = onLocalTileAdded
        this.onLocalVideoTileRemoved = onLocalTileRemoved
        this.onRemoteTileAdded = onRemoteTileAdded
        this.onRemoteTileRemoved = onRemoteTileRemoved

//        isFrontCamera = (cameraFacing == CameraFacing.FRONT)
//        isJoiningOnMute = startMuted

        meetingSession.audioVideo().addVideoTileObserverWithObserver(observer = this)

        val audioVideoObserver = AudioVideoObserverImpl(
            meetingSession = meetingSession,
            onConnectionStatusChanged = onConnectionStatusChanged,
            onRemoteVideoAvailable = onRemoteVideoAvailable,
            onCameraSendAvailable = onCameraSendAvailable,
            onSessionError = onSessionError,
            onVideoNeedsRestart = onVideoNeedsRestart,
            isJoiningOnMute = isJoiningOnMute
        )
        meetingSession.audioVideo().addAudioVideoObserverWithObserver(audioVideoObserver)

        val activeSpeakerObserver = ActiveSpeakerObserver(onActiveSpeakersChanged)
        meetingSession.audioVideo().addActiveSpeakerObserverWithPolicy(
            policy = DefaultActiveSpeakerPolicy(),
            observer = activeSpeakerObserver
        )

        configureAudioSession()

        AVAudioSession.sharedInstance().requestRecordPermission { _ ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    try {
                        meetingSession.audioVideo().startAndReturnError(error = null)
                        meetingSession.audioVideo().startRemoteVideo()
                    } catch (e: Throwable) {
                        onSessionError.invoke("Failed to start audio: ${e.message}", false)
                    }
                }
            }
        }
    }

    actual fun leaveMeeting() {
        meetingSession.audioVideo().stopLocalVideo()
        meetingSession.audioVideo().stopRemoteVideo()
        meetingSession.audioVideo().stop()
        meetingSession.audioVideo().removeVideoTileObserverWithObserver(observer = this)
        localTileId = null
        remoteTiles.clear()
        attendeeTileMap.clear()
        isJoiningOnMute = false
        isRemoteVideoStarted = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onLocalVideoTileAdded = null
        onLocalVideoTileRemoved = null
        onRemoteTileAdded = null
        onRemoteTileRemoved = null
        realTimeListener = null
    }

    actual fun startLocalVideo() {
        if (isLocalVideoStarted) return
        meetingSession.audioVideo().startLocalVideoAndReturnError(error = null)
        isLocalVideoStarted = true
    }

    actual fun stopLocalVideo() {
        meetingSession.audioVideo().stopLocalVideo()
    }

    internal class LocalVideoContainerView(private val localRenderView: DefaultVideoRenderView) : UIView(frame = cValue<CGRect>()) {
        override fun layoutSubviews() {
            super.layoutSubviews()
            localRenderView.setFrame(bounds)
        }
    }

    @Composable
    actual fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean) =
        UIKitView(
            factory = { localVideoContainer },
            modifier = modifier,
            update = { localRenderView.setFrame(it.bounds) }
        )

    internal class RemoteVideoContainerView(
        private val chimeSDK: ChimeSDK,
        private val tileId: Int
    ) : UIView(frame = cValue<CGRect>()) {
        override fun layoutSubviews() {
            super.layoutSubviews()
            val actual = chimeSDK.getRemoteView(tileId) ?: return
            if (actual.superview != this) {
                subviews.forEach { subview ->
                    (subview as? UIView)?.removeFromSuperview()
                }
                addSubview(actual)
                chimeSDK.rebindRemoteView(tileId)
            }
            actual.setFrame(bounds)
        }
    }

    @Composable
    actual fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean) = UIKitView(
        factory = { RemoteVideoContainerView(this, tileId) },
        modifier = modifier,
        update = RemoteVideoContainerView::setNeedsLayout
    )

    actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
        try {
            meetingSession.audioVideo().realtimeSendDataMessageWithTopic(
                topic = topic,
                data = data,
                lifetimeMs = lifetimeMs.toInt(),
                error = null
            )
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: sendRealtimeMessage failed: ${e.message}")
        }
    }

    actual fun setMute(shouldMute: Boolean): Boolean = meetingSession.audioVideo().run {
        return if (shouldMute) realtimeLocalMute() else realtimeLocalUnmute()
    }

    actual fun switchCamera() = meetingSession.audioVideo().switchCamera()

    actual fun switchAudioDevice(device: AudioDevice?) {
        val targetChimeDevice = meetingSession
            .audioVideo()
            .listAudioDevices()
            .filterIsInstance<MediaDevice>()
            .firstOrNull { it.label() == device?.label } ?: return
        meetingSession.audioVideo().chooseAudioDeviceWithMediaDevice(targetChimeDevice)
    }

    actual fun subscribeToTopic(topic: String, listener: (ChimeMessage) -> Unit) =
        meetingSession.audioVideo().addRealtimeDataMessageObserverWithTopic(
            topic = topic,
            observer = DataMessageObserverImpl(listener)
        )

    actual fun unsubscribeFromTopic(topic: String) = meetingSession.audioVideo()
        .removeRealtimeDataMessageObserverFromTopicWithTopic(topic = topic)

    fun getRemoteView(tileId: Int): UIView? = remoteTiles[tileId.toLong()]

    fun rebindLocalView() {
        val tileId = localTileId ?: return
        localRenderView.setFrame(localVideoContainer.bounds)
        meetingSession.audioVideo().bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
    }

    fun rebindRemoteView(tileId: Int) {
        val renderView = remoteTiles[tileId.toLong()] ?: return
        meetingSession.audioVideo().bindVideoViewWithVideoView(videoView = renderView, tileId = tileId.toLong())
    }

    private fun configureAudioSession() {
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVideoChat,
            options = AVAudioSessionCategoryOptionAllowBluetoothHFP or AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null
        )
        audioSession.setActive(true, null)
    }


    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession.audioVideo()
                .bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalVideoTileAdded?.invoke(tileId.toInt())
        } else {
            val attendeeId = tileState.attendeeId()
            val oldTileId = attendeeTileMap[attendeeId]
            if (oldTileId != null && oldTileId != tileId) {
                meetingSession.audioVideo().unbindVideoViewWithTileId(tileId = oldTileId)
                remoteTiles.remove(oldTileId)
                attendeeTileMap.remove(attendeeId)
                onRemoteTileRemoved?.invoke()
            }
            val renderView = remoteTiles.getOrPut(tileId) {
                DefaultVideoRenderView().also { it.setMirror(false) }
            }
            attendeeTileMap[attendeeId] = tileId
            renderView.setFrame(renderView.superview?.bounds ?: renderView.bounds)
            onRemoteTileAdded?.invoke(tileId.toInt())
        }
    }

    override fun videoTileDidRemoveWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        meetingSession.audioVideo().unbindVideoViewWithTileId(tileId = tileId)

        if (tileId == localTileId) {
            localTileId = null
            onLocalVideoTileRemoved?.invoke()
        } else if (remoteTiles.containsKey(tileId)) {
            remoteTiles.remove(tileId)
            if (attendeeTileMap[tileState.attendeeId()] == tileId) {
                attendeeTileMap.remove(tileState.attendeeId())
            }
            onRemoteTileRemoved?.invoke()
        }
    }

    override fun videoTileDidPauseWithTileState(tileState: VideoTileState) {}

    override fun videoTileDidResumeWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()
        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession.audioVideo().bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalVideoTileAdded?.invoke(tileId.toInt())
        } else {
            val renderView = remoteTiles.getOrPut(tileId) {
                DefaultVideoRenderView().also { it.setMirror(false) }
            }
            attendeeTileMap[tileState.attendeeId()] = tileId
            renderView.setFrame(renderView.superview?.bounds ?: renderView.bounds)
            onRemoteTileAdded?.invoke(tileId.toInt())
        }
    }

    override fun videoTileSizeDidChangeWithTileState(tileState: VideoTileState) {}
}