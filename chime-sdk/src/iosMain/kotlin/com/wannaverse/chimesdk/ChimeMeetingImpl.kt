@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.*
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.Foundation.*
import platform.UIKit.UIView
import platform.darwin.NSObject

// Kotlin `object` cannot extend ObjC classes — use a class with a top-level singleton val.
internal val chimeMeeting = ChimeMeetingImpl()

internal class ChimeMeetingImpl : NSObject(),
    AudioVideoObserverProtocol,
    RealtimeObserverProtocol,
    VideoTileObserverProtocol,
    DeviceChangeObserverProtocol,
    ActiveSpeakerObserverProtocol,
    DataMessageObserverProtocol {

    private var meetingSession: DefaultMeetingSession? = null
    private var isFrontCamera = true
    private val subscribedTopics: MutableSet<String> = mutableSetOf()

    private var localTileId: Long? = null
    private val remoteTiles: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()
    private val attendeeTileMap: MutableMap<String, Long> = mutableMapOf()

    val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView().also { it.setMirror(true) }
    var onConnectionStatusChanged: ((ConnectionStatus) -> Unit)? = null
    var onActiveSpeakersChanged: ((Set<String>) -> Unit)? = null
    var onLocalVideoTileAdded: ((Int?) -> Unit)? = null
    var onRemoteVideoAvailable: ((Boolean, Int) -> Unit)? = null
    var onCameraSendAvailable: ((Boolean) -> Unit)? = null
    var onSessionError: ((String, Boolean) -> Unit)? = null
    var onVideoNeedsRestart: (() -> Unit)? = null
    var onLocalVideoTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: ((Int) -> Unit)? = null
    var onRemoteTileRemoved: ((Int) -> Unit)? = null
    var onLocalAttendeeIdAvailable: ((String) -> Unit)? = null
    var realTimeListener: RealTimeEventListener? = null
    val topicListeners: MutableMap<String, (TextMessage) -> Unit> = mutableMapOf()

    override fun observerId(): String = "KotlinChimeMeeting"

    fun getRemoteView(tileId: Int): UIView? = remoteTiles[tileId.toLong()]

    fun join(
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
    ) {
        val logger = ConsoleLogger(name = "ChimeMeetingImpl", level = LogLevelINFO)

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
            urlRewriter = { it },
            primaryMeetingId = externalMeetingId
        )

        val session = DefaultMeetingSession(configuration = configuration, logger = logger)
        meetingSession = session

        session.audioVideo().addVideoTileObserverWithObserver(observer = this)
        session.audioVideo().addAudioVideoObserverWithObserver(observer = this)
        session.audioVideo().addRealtimeObserverWithObserver(observer = this)
        session.audioVideo().addDeviceChangeObserverWithObserver(observer = this)
        session.audioVideo().addActiveSpeakerObserverWithPolicy(
            policy = DefaultActiveSpeakerPolicy(),
            observer = this
        )

        onLocalAttendeeIdAvailable?.invoke(attendeeId)
        configureAudioSession()

        session.audioVideo().listAudioDevices().filterIsInstance<MediaDevice>().firstOrNull()?.let {
            session.audioVideo().chooseAudioDeviceWithMediaDevice(mediaDevice = it)
        }

        AVAudioSession.sharedInstance().requestRecordPermission { _: Boolean ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _: Boolean ->
                NSOperationQueue.mainQueue.addOperationWithBlock {
                    startAudioAndVideo()
                }
            }
        }
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

    private fun startAudioAndVideo() {
        val session = meetingSession ?: return
        try {
            session.audioVideo().startAndReturnError(error = null)
        } catch (e: Throwable) {
            onSessionError?.invoke("Failed to start audio: ${e.message}", false)
            return
        }
        session.audioVideo().startRemoteVideo()
        try {
            session.audioVideo().startLocalVideoAndReturnError(error = null)
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: local video start failed: ${e.message}")
        }
    }

    fun leave() {
        val session = meetingSession ?: return
        session.audioVideo().stopLocalVideo()
        session.audioVideo().stopRemoteVideo()
        session.audioVideo().stop()
        session.audioVideo().removeVideoTileObserverWithObserver(observer = this)
        session.audioVideo().removeAudioVideoObserverWithObserver(observer = this)
        session.audioVideo().removeRealtimeObserverWithObserver(observer = this)
        session.audioVideo().removeDeviceChangeObserverWithObserver(observer = this)
        session.audioVideo().removeActiveSpeakerObserverWithObserver(observer = this)
        subscribedTopics.forEach {
            session.audioVideo().removeRealtimeDataMessageObserverFromTopicWithTopic(topic = it)
        }
        subscribedTopics.clear()
        meetingSession = null
        localTileId = null
        remoteTiles.clear()
        attendeeTileMap.clear()
        isFrontCamera = true
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
        onLocalAttendeeIdAvailable = null
        realTimeListener = null
        topicListeners.clear()
    }

    fun startLocalVideo() {
        try {
            meetingSession?.audioVideo()?.startLocalVideoAndReturnError(error = null)
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: startLocalVideo failed: ${e.message}")
        }
    }

    fun stopLocalVideo() {
        meetingSession?.audioVideo()?.stopLocalVideo()
    }

    fun switchCamera() {
        val session = meetingSession ?: return
        isFrontCamera = !isFrontCamera
        session.audioVideo().switchCamera()
    }

    fun setMute(shouldMute: Boolean) {
        if (shouldMute) meetingSession?.audioVideo()?.realtimeLocalMute()
        else meetingSession?.audioVideo()?.realtimeLocalUnmute()
    }

    fun switchAudioDevice(deviceId: String?) {
        val id = deviceId ?: return
        meetingSession?.audioVideo()
            ?.listAudioDevices()
            ?.filterIsInstance<MediaDevice>()
            ?.firstOrNull { it.label() == id }
            ?.let { meetingSession?.audioVideo()?.chooseAudioDeviceWithMediaDevice(mediaDevice = it) }
    }

    fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
        try {
            meetingSession?.audioVideo()?.realtimeSendDataMessageWithTopic(
                topic = topic,
                data = data,
                lifetimeMs = lifetimeMs.toInt(),
                error = null
            )
        } catch (e: Throwable) {
            println("ChimeMeetingImpl: sendRealtimeMessage failed: ${e.message}")
        }
    }

    fun subscribeTopic(topic: String) {
        val session = meetingSession ?: return
        subscribedTopics.add(topic)
        session.audioVideo().addRealtimeDataMessageObserverWithTopic(topic = topic, observer = this)
    }

    fun unsubscribeTopic(topic: String) {
        val session = meetingSession ?: return
        subscribedTopics.remove(topic)
        session.audioVideo().removeRealtimeDataMessageObserverFromTopicWithTopic(topic = topic)
    }

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
        onRemoteVideoAvailable?.invoke(true, sources.size)
    }

    override fun remoteVideoSourcesDidBecomeUnavailableWithSources(sources: List<*>) {
        onRemoteVideoAvailable?.invoke(false, sources.size)
    }

    override fun cameraSendAvailabilityDidChangeWithAvailable(available: Boolean) {
        onCameraSendAvailable?.invoke(available)
    }

    override fun attendeesDidJoinWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesJoined(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidLeaveWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesLeft(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidDropWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesDropped(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidMuteWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesMuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun attendeesDidUnmuteWithAttendeeInfo(attendeeInfo: List<*>) {
        realTimeListener?.onAttendeesUnmuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })
    }

    override fun signalStrengthDidChangeWithSignalUpdates(signalUpdates: List<*>) {
        signalUpdates.filterIsInstance<SignalUpdate>().forEach { update ->
            realTimeListener?.onSignalStrengthChanged(
                update.attendeeInfo().attendeeId(),
                update.attendeeInfo().externalUserId(),
                update.signalStrength().toInt()
            )
        }
    }

    override fun volumeDidChangeWithVolumeUpdates(volumeUpdates: List<*>) {
        volumeUpdates.filterIsInstance<VolumeUpdate>().forEach { update ->
            realTimeListener?.onVolumeChanged(
                update.attendeeInfo().attendeeId(),
                update.attendeeInfo().externalUserId(),
                update.volumeLevel().toInt()
            )
        }
    }

    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
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
                    onRemoteTileRemoved?.invoke(oldTileId.toInt())
                }
                val renderView = remoteTiles.getOrPut(tileId) {
                    DefaultVideoRenderView().also { it.setMirror(false) }
                }
                attendeeTileMap[attendeeId] = tileId
                meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = renderView, tileId = tileId)
                onRemoteTileAdded?.invoke(tileId.toInt())
            }
        }
    }

    override fun videoTileDidRemoveWithTileState(tileState: VideoTileState) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
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
                onRemoteTileRemoved?.invoke(tileId.toInt())
            }
        }
    }

    override fun videoTileDidPauseWithTileState(tileState: VideoTileState) {}

    override fun videoTileDidResumeWithTileState(tileState: VideoTileState) {
        NSOperationQueue.mainQueue.addOperationWithBlock {
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
                meetingSession?.audioVideo()?.bindVideoViewWithVideoView(videoView = renderView, tileId = tileId)
                onRemoteTileAdded?.invoke(tileId.toInt())
            }
        }
    }

    override fun videoTileSizeDidChangeWithTileState(tileState: VideoTileState) {}

    override fun audioDeviceDidChangeWithFreshAudioDeviceList(freshAudioDeviceList: List<*>) {}

    override fun activeSpeakerDidDetectWithAttendeeInfo(attendeeInfo: List<*>) {
        val ids = attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.externalUserId() }.toSet()
        onActiveSpeakersChanged?.invoke(ids)
    }

    override fun activeSpeakerScoreDidChangeWithScores(scores: Map<Any?, *>) {}

    override fun dataMessageDidReceivedWithDataMessage(dataMessage: DataMessage) {
        topicListeners[dataMessage.topic()]?.invoke(
            TextMessage(
                topic = dataMessage.topic(),
                senderId = dataMessage.senderAttendeeId(),
                content = dataMessage.text() ?: "",
                timestamp = dataMessage.timestampMs()
            )
        )
    }
}
