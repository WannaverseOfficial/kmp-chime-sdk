@file:OptIn(ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import cocoapods.AmazonChimeSDK.ActiveSpeakerObserverProtocol
import cocoapods.AmazonChimeSDK.AttendeeInfo
import cocoapods.AmazonChimeSDK.AudioVideoObserverProtocol
import cocoapods.AmazonChimeSDK.ConsoleLogger
import cocoapods.AmazonChimeSDK.DataMessage
import cocoapods.AmazonChimeSDK.DataMessageObserverProtocol
import cocoapods.AmazonChimeSDK.DefaultActiveSpeakerPolicy
import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.DefaultVideoRenderView
import cocoapods.AmazonChimeSDK.DeviceChangeObserverProtocol
import cocoapods.AmazonChimeSDK.LogLevelINFO
import cocoapods.AmazonChimeSDK.MediaDevice
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBluetooth
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBuiltInSpeaker
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioHandset
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioWiredHeadset
import cocoapods.AmazonChimeSDK.MeetingSessionConfiguration
import cocoapods.AmazonChimeSDK.MeetingSessionCredentials
import cocoapods.AmazonChimeSDK.MeetingSessionStatus
import cocoapods.AmazonChimeSDK.MeetingSessionStatusCodeVideoAtCapacityViewOnly
import cocoapods.AmazonChimeSDK.MeetingSessionURLs
import cocoapods.AmazonChimeSDK.RealtimeObserverProtocol
import cocoapods.AmazonChimeSDK.RemoteVideoSource
import cocoapods.AmazonChimeSDK.SignalUpdate
import cocoapods.AmazonChimeSDK.VideoTileObserverProtocol
import cocoapods.AmazonChimeSDK.VideoTileState
import cocoapods.AmazonChimeSDK.VolumeUpdate
import kotlinx.cinterop.BetaInteropApi
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
import platform.objc.class_addProtocol
import platform.objc.objc_getProtocol
import platform.objc.object_getClass
import kotlin.collections.orEmpty
import kotlin.collections.set

private val logger = ConsoleLogger(name = "ChimeSDK", level = LogLevelINFO)

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession
) : NSObject(),
    AudioVideoObserverProtocol,
    VideoTileObserverProtocol,
    DeviceChangeObserverProtocol,
    ActiveSpeakerObserverProtocol {
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

    private var localTileId: Long? = null
    private val remoteTiles: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()
    private val attendeeTileMap: MutableMap<String, Long> = mutableMapOf()
    private val remoteVideoSources: MutableMap<String, RemoteVideoSource> = mutableMapOf()

    val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView().also { it.setMirror(true) }
    internal val localVideoContainer: LocalVideoContainerView = LocalVideoContainerView(localRenderView).also { it.addSubview(localRenderView) }
    var onConnectionStatusChanged: ((ConnectionStatus) -> Unit)? = null
    var onActiveSpeakersChanged: ((Set<String>) -> Unit)? = null
    var onLocalVideoTileAdded: ((Int) -> Unit)? = null
    var onRemoteVideoAvailable: ((Boolean, Int) -> Unit)? = null
    var onCameraSendAvailable: ((Boolean) -> Unit)? = null
    var onSessionError: ((String, Boolean) -> Unit)? = null
    var onVideoNeedsRestart: (() -> Unit)? = null
    var onLocalVideoTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: ((Int) -> Unit)? = null
    var onRemoteTileRemoved: (() -> Unit)? = null
    var realTimeListener: RealTimeEventListener? = null

    override fun observerId(): String = "KotlinChimeMeeting"

    private data class ObserverProtocolDescriptor(
        val label: String,
        val candidates: List<String>
    )

    init {
        @Suppress("UNUSED_VARIABLE") val _1: AudioVideoObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _3: VideoTileObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _4: DeviceChangeObserverProtocol = this
        @Suppress("UNUSED_VARIABLE") val _5: ActiveSpeakerObserverProtocol = this

        forceRegisterObserverProtocols()
    }

    private fun resolveProtocolName(descriptor: ObserverProtocolDescriptor): String? =
        descriptor.candidates.firstOrNull { objc_getProtocol(it) != null }

    @OptIn(BetaInteropApi::class)
    private fun forceRegisterObserverProtocols() {
        val cls = object_getClass(this) ?: return

        val observerProtocols = listOf(
            ObserverProtocolDescriptor(
                label = "audio",
                candidates = listOf("AudioVideoObserver", "_TtP14AmazonChimeSDK18AudioVideoObserver_")
            ),
            ObserverProtocolDescriptor(
                label = "realtime",
                candidates = listOf("RealtimeObserver", "_TtP14AmazonChimeSDK16RealtimeObserver_")
            ),
            ObserverProtocolDescriptor(
                label = "tile",
                candidates = listOf("VideoTileObserver", "_TtP14AmazonChimeSDK17VideoTileObserver_")
            ),
            ObserverProtocolDescriptor(
                label = "device",
                candidates = listOf("DeviceChangeObserver", "_TtP14AmazonChimeSDK20DeviceChangeObserver_")
            ),
            ObserverProtocolDescriptor(
                label = "speaker",
                candidates = listOf("ActiveSpeakerObserver", "_TtP14AmazonChimeSDK21ActiveSpeakerObserver_")
            ),
            ObserverProtocolDescriptor(
                label = "data",
                candidates = listOf("DataMessageObserver", "_TtP14AmazonChimeSDK19DataMessageObserver_")
            )
        )

        for (descriptor in observerProtocols) {
            val protocolName = resolveProtocolName(descriptor) ?: continue
            val protocol = objc_getProtocol(protocolName)
            if (protocol != null) class_addProtocol(cls, protocol)
        }
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

        this.onActiveSpeakersChanged = onActiveSpeakersChanged
        this.onLocalVideoTileAdded = onLocalTileAdded
        this.onConnectionStatusChanged = onConnectionStatusChanged
        this.onRemoteVideoAvailable = onRemoteVideoAvailable
        this.onCameraSendAvailable = onCameraSendAvailable
        this.onSessionError = onSessionError
        this.onVideoNeedsRestart = onVideoNeedsRestart
        this.onLocalVideoTileRemoved = onLocalTileRemoved
        this.onRemoteTileAdded = onRemoteTileAdded
        this.onRemoteTileRemoved = onRemoteTileRemoved

//        isFrontCamera = (cameraFacing == CameraFacing.FRONT)
//        isJoiningOnMute = startMuted

        meetingSession.audioVideo().addVideoTileObserverWithObserver(observer = this)
        meetingSession.audioVideo().addAudioVideoObserverWithObserver(observer = this)
        meetingSession.audioVideo().addDeviceChangeObserverWithObserver(observer = this)
        meetingSession.audioVideo().addActiveSpeakerObserverWithPolicy(
            policy = DefaultActiveSpeakerPolicy(),
            observer = this
        )

        configureAudioSession()
//        meetingSession.audioVideo().listAudioDevices().filterIsInstance<MediaDevice>().firstOrNull()?.let {
//            meetingSession.audioVideo().chooseAudioDeviceWithMediaDevice(mediaDevice = it)
//        }

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
        meetingSession.audioVideo().removeAudioVideoObserverWithObserver(observer = this)
        meetingSession.audioVideo().removeDeviceChangeObserverWithObserver(observer = this)
        meetingSession.audioVideo().removeActiveSpeakerObserverWithObserver(observer = this)
        localTileId = null
        remoteTiles.clear()
        attendeeTileMap.clear()
        remoteVideoSources.clear()
        isJoiningOnMute = false
        isLocalVideoStarted = false
        didStartLocalVideo = false
        isRemoteVideoStarted = false
        clearCallbacks()
    }

    private fun clearCallbacks() {
        onConnectionStatusChanged = null
        onActiveSpeakersChanged = null
        onLocalVideoTileAdded = null
        onRemoteVideoAvailable = null
        onCameraSendAvailable = null
        onSessionError = null
        onVideoNeedsRestart = null
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
        meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = renderView, tileId = tileId.toLong())
    }

    private fun configureAudioSession() {
        val s = AVAudioSession.sharedInstance()
        s.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVideoChat,
            options = AVAudioSessionCategoryOptionAllowBluetoothHFP or
                    AVAudioSessionCategoryOptionAllowBluetoothA2DP,
            error = null
        )
        s.setActive(true, error = null)
    }

    private var isLocalVideoStarted = false
    override fun audioSessionDidStartConnectingWithReconnecting(reconnecting: Boolean) {
        onConnectionStatusChanged?.invoke(
            if (reconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING
        )
    }

    override fun audioSessionDidStartWithReconnecting(reconnecting: Boolean) {
        onConnectionStatusChanged?.invoke(ConnectionStatus.CONNECTED)
    }

    override fun audioSessionDidDrop() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.RECONNECTING)
        onSessionError?.invoke("Audio dropped, reconnecting...", true)
    }

    override fun audioSessionDidStopWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {
        onConnectionStatusChanged?.invoke(ConnectionStatus.DISCONNECTED)
        onSessionError?.invoke("Session ended: ${sessionStatus.statusCode()}", false)
    }

    override fun audioSessionDidCancelReconnect() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.ERROR)
        onSessionError?.invoke("Failed to reconnect", false)
    }

    override fun connectionDidBecomePoor() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.POOR_CONNECTION)
    }

    override fun connectionDidRecover() {
        onConnectionStatusChanged?.invoke(ConnectionStatus.CONNECTED)
    }

    override fun videoSessionDidStartConnecting() {}

    override fun videoSessionDidStartWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {
        if (sessionStatus.statusCode() == MeetingSessionStatusCodeVideoAtCapacityViewOnly) {
            onSessionError?.invoke("Video at capacity. View only.", false)
        }
    }

    override fun videoSessionDidStopWithStatusWithSessionStatus(sessionStatus: MeetingSessionStatus) {}

    override fun remoteVideoSourcesDidBecomeAvailableWithSources(sources: List<*>) {
        val newSources = sources.filterIsInstance<RemoteVideoSource>()

        newSources.forEach { remoteVideoSources[it.attendeeId()] = it }

        onRemoteVideoAvailable?.invoke(true, newSources.size)
    }

    override fun remoteVideoSourcesDidBecomeUnavailableWithSources(sources: List<*>) {
        val unavailable = sources.filterIsInstance<RemoteVideoSource>()
        unavailable.forEach { remoteVideoSources.remove(it.attendeeId()) }
        onRemoteVideoAvailable?.invoke(false, unavailable.size)
    }

    private var didStartLocalVideo = false

    override fun cameraSendAvailabilityDidChangeWithAvailable(available: Boolean) {
        onCameraSendAvailable?.invoke(available)

        val session = meetingSession ?: return

        if (available && !didStartLocalVideo) {
            session.audioVideo().startLocalVideoAndReturnError(null)
            didStartLocalVideo = true
            isLocalVideoStarted = true
        }
    }

    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalVideoTileAdded?.invoke(tileId.toInt())
        } else {
            val attendeeId = tileState.attendeeId()
            val oldTileId = attendeeTileMap[attendeeId]
            if (oldTileId != null && oldTileId != tileId) {
                meetingSession?.audioVideo()?.unbindVideoViewWithTileId(tileId = oldTileId)
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

        meetingSession?.audioVideo()?.unbindVideoViewWithTileId(tileId = tileId)

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
            meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
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

    override fun audioDeviceDidChangeWithFreshAudioDeviceList(freshAudioDeviceList: List<*>) {}

    override fun activeSpeakerDidDetectWithAttendeeInfo(attendeeInfo: List<*>) {
        val ids = attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.externalUserId() }.toSet()
        onActiveSpeakersChanged?.invoke(ids)
    }

    override fun activeSpeakerScoreDidChangeWithScores(scores: Map<Any?, *>) {}
}