package com.wannaverse.chimesdk

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.amazonaws.services.chime.sdk.meetings.analytics.DefaultEventAnalyticsController
import com.amazonaws.services.chime.sdk.meetings.analytics.DefaultMeetingStatsCollector
import com.amazonaws.services.chime.sdk.meetings.audiovideo.audio.activespeakerpolicy.DefaultActiveSpeakerPolicy
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.CameraCaptureSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultCameraCaptureSource
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.capture.DefaultSurfaceTextureCaptureSourceFactory
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.gl.DefaultEglCoreFactory
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.device.MediaDeviceType
import com.amazonaws.services.chime.sdk.meetings.ingestion.DefaultAppStateMonitor
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionConfiguration
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionCredentials
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSessionURLs
import com.amazonaws.services.chime.sdk.meetings.utils.logger.ConsoleLogger
import com.amazonaws.services.chime.sdk.meetings.utils.logger.LogLevel
import com.wannaverse.chimesdk.composables.VideoTileView

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession,
    private val eventAnalyticsController: DefaultEventAnalyticsController,
    private val eglCoreFactory: DefaultEglCoreFactory
) {
    actual companion object {
        private val logger = ConsoleLogger(LogLevel.INFO)

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
            val meetingSessionConfiguration = MeetingSessionConfiguration(
                meetingId = meetingId,
                externalMeetingId = externalMeetingId,
                credentials = MeetingSessionCredentials(
                    attendeeId = attendeeId,
                    externalUserId = externalUserId,
                    joinToken = joinToken
                ),
                urls = MeetingSessionURLs(
                    _audioFallbackURL = audioFallbackURL,
                    _audioHostURL = audioHostURL,
                    _ingestionURL = ingestionURL,
                    _signalingURL = signalingURL,
                    _turnControlURL = turnControlURL,
                    urlRewriter = { it }
                )
            )

            val eventAnalyticsController = DefaultEventAnalyticsController(
                logger = logger,
                meetingSessionConfiguration = meetingSessionConfiguration,
                meetingStatsCollector = DefaultMeetingStatsCollector(logger),
                appStateMonitor = DefaultAppStateMonitor(logger)
            )

            val eglCoreFactory = DefaultEglCoreFactory()

            val meetingSession =
                DefaultMeetingSession(meetingSessionConfiguration, logger, appContext, eglCoreFactory)

            return ChimeSDK(meetingSession, eventAnalyticsController, eglCoreFactory)
        }
    }

    actual fun getAvailableInputDevices(): List<AudioDevice> =
        meetingSession.audioVideo
            .listAudioDevices()
            .mapNotNull { device ->
                val type = when (device.type) {
                    MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                    MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                    MediaDeviceType.AUDIO_HANDSET -> AudioDeviceType.BUILT_IN_MIC
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label
                )
            }

    actual fun getAvailableOutputDevices(): List<AudioDevice> =
        meetingSession.audioVideo
            .listAudioDevices()
            .mapNotNull { device ->
                val type = when (device.type) {
                    MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                    MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                    MediaDeviceType.AUDIO_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
                    else -> return@mapNotNull null
                }

                AudioDevice(
                    type = type,
                    label = device.label
                )
            }

    private lateinit var realTimeObserver: RealTimeObserverImpl
    private lateinit var deviceObserver: DeviceObserverImpl
    private lateinit var videoTileObserver: VideoTileObserverImpl
    private lateinit var audioVideoObserver: AudioVideoObserverImpl
    private lateinit var activeSpeakerObserver: ActiveSpeakerObserverImpl
    private lateinit var dataMessageObserver: DataMessageObserverImpl

    private var cameraCaptureSource: CameraCaptureSource? = null
    private var cachedVideoDevices: List<MediaDevice>? = null
    private var currentCameraFacing = CameraFacing.FRONT
    private var cameraOn = false

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
        realTimeObserver = RealTimeObserverImpl(realTimeListener)
        meetingSession.audioVideo.addRealtimeObserver(realTimeObserver)

        deviceObserver = DeviceObserverImpl(
            meetingSession = meetingSession,
            realTimeEventListener = realTimeListener
        )
        meetingSession.audioVideo.addDeviceChangeObserver(deviceObserver)

        meetingSession.audioVideo.listAudioDevices()
            .firstOrNull { it.label == selectedAudioInputDevice }
            ?.let(deviceObserver::selectAudioDevice)

        videoTileObserver = VideoTileObserverImpl(
            meetingSession = meetingSession,
            onLocalTileAdded = onLocalTileAdded,
            onLocalTileRemoved = onLocalTileRemoved,
            onRemoteTileAdded = onRemoteTileAdded,
            onRemoteTileRemoved = onRemoteTileRemoved
        )
        meetingSession.audioVideo.addVideoTileObserver(videoTileObserver)

        audioVideoObserver = AudioVideoObserverImpl(
            meetingSession = meetingSession,
            onConnectionStatusChanged = onConnectionStatusChanged,
            onRemoteVideoAvailable = onRemoteVideoAvailable,
            onCameraSendAvailable = onCameraSendAvailable,
            onSessionError = onSessionError,
            onVideoNeedsRestart = onVideoNeedsRestart,
            isJoiningOnMute = isJoiningOnMute
        )
        meetingSession.audioVideo.addAudioVideoObserver(audioVideoObserver)

        activeSpeakerObserver = ActiveSpeakerObserverImpl(onActiveSpeakersChanged)
        meetingSession.audioVideo.addActiveSpeakerObserver(
            observer = activeSpeakerObserver,
            policy = DefaultActiveSpeakerPolicy()
        )

        dataMessageObserver = DataMessageObserverImpl(meetingSession)

        try {
            meetingSession.audioVideo.start()
            meetingSession.audioVideo.startRemoteVideo()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    actual fun leaveMeeting() {
        try {
            if (cameraOn || cameraCaptureSource != null) {
                cameraCaptureSource?.stop()
                meetingSession.audioVideo.stopLocalVideo()
                cameraOn = false
                cameraCaptureSource = null
            }

            meetingSession.audioVideo.removeRealtimeObserver(realTimeObserver)
            meetingSession.audioVideo.removeDeviceChangeObserver(deviceObserver)
            meetingSession.audioVideo.removeVideoTileObserver(videoTileObserver)
            meetingSession.audioVideo.removeAudioVideoObserver(audioVideoObserver)
            meetingSession.audioVideo.removeActiveSpeakerObserver(activeSpeakerObserver)
            dataMessageObserver.clearListeners()

            meetingSession.audioVideo.stopRemoteVideo()
            meetingSession.audioVideo.realtimeLocalMute()
            meetingSession.audioVideo.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videoTileObserver.clearAll()
    }

    actual fun startLocalVideo() {
        val videoDevices = cachedVideoDevices ?: run {
            val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            MediaDevice.listVideoDevices(cm).also { cachedVideoDevices = it }
        }

        val desiredType = if (currentCameraFacing == CameraFacing.FRONT)
            MediaDeviceType.VIDEO_FRONT_CAMERA else MediaDeviceType.VIDEO_BACK_CAMERA

        val device = videoDevices.firstOrNull { it.type == desiredType }
            ?: throw IllegalStateException("No camera found for $desiredType")

        if (cameraCaptureSource == null) {
            val factory = DefaultSurfaceTextureCaptureSourceFactory(logger, eglCoreFactory)
            cameraCaptureSource = DefaultCameraCaptureSource(
                appContext, logger, factory,
                eventAnalyticsController = eventAnalyticsController
            )
        }

        cameraCaptureSource!!.device = device
        cameraCaptureSource!!.start()
        meetingSession.audioVideo.startLocalVideo(cameraCaptureSource!!)
        cameraOn = true
    }

    actual fun stopLocalVideo() {
        try {
            meetingSession.audioVideo.stopLocalVideo()
            cameraCaptureSource?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cameraOn = false
        }
    }

    @Composable
    actual fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean) =
        VideoTileView(
            tileId = videoTileObserver.localTileId!!,
            modifier = modifier,
            cameraFacing = cameraFacing,
            isOnTop = isOnTop,
            meetingSession = meetingSession,
            videoTileObserverImpl = videoTileObserver
        )

    @Composable
    actual fun RemoteVideoView(modifier: Modifier, tileId: Int, isOnTop: Boolean) = VideoTileView(
        tileId = tileId,
        modifier = modifier,
        isOnTop = isOnTop,
        meetingSession = meetingSession,
        videoTileObserverImpl = videoTileObserver
    )

    actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) =
        meetingSession.audioVideo.realtimeSendDataMessage(topic, data, lifetimeMs.toInt())

    actual fun setMute(shouldMute: Boolean): Boolean =
        if (shouldMute) meetingSession.audioVideo.realtimeLocalMute() else meetingSession.audioVideo.realtimeLocalUnmute()

    actual fun switchCamera() {
        val source = cameraCaptureSource ?: return

        val previous = currentCameraFacing
        currentCameraFacing = if (currentCameraFacing == CameraFacing.FRONT) CameraFacing.BACK else CameraFacing.FRONT

        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val devices = MediaDevice.listVideoDevices(cm)
        cachedVideoDevices = devices

        val desiredType = if (currentCameraFacing == CameraFacing.FRONT)
            MediaDeviceType.VIDEO_FRONT_CAMERA else MediaDeviceType.VIDEO_BACK_CAMERA

        val newDevice = devices.firstOrNull { it.type == desiredType }
        if (newDevice == null) {
            currentCameraFacing = previous
            return
        }
        source.device = newDevice
    }

    actual fun switchAudioDevice(device: AudioDevice?) {
        meetingSession.audioVideo.listAudioDevices()
            .firstOrNull { it.label == device?.label }
            ?.let(meetingSession.audioVideo::chooseAudioDevice)
    }

    actual fun subscribeToTopic(topic: String, listener: (ChimeMessage) -> Unit) =
        dataMessageObserver.addListener(topic, listener)

    actual fun unsubscribeFromTopic(topic: String) = dataMessageObserver.removeListener(topic)
}
