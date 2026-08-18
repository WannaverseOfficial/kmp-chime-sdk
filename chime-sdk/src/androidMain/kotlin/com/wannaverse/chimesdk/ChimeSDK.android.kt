package com.wannaverse.chimesdk

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession,
    private val eventAnalyticsController: DefaultEventAnalyticsController,
    private val eglCoreFactory: DefaultEglCoreFactory
) {
    actual companion object {
        internal lateinit var applicationContext: Context

        fun initialize(applicationContext: Context) {
            this.applicationContext = applicationContext
        }

        context(activity: ComponentActivity)
        fun initialize() = initialize(applicationContext)

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
                DefaultMeetingSession(
                    meetingSessionConfiguration,
                    logger,
                    applicationContext,
                    eglCoreFactory
                )

            return ChimeSDK(meetingSession, eventAnalyticsController, eglCoreFactory)
        }
    }

    private lateinit var realTimeObserver: RealTimeObserverImpl
    private lateinit var deviceObserver: DeviceObserverImpl
    private lateinit var videoTileObserver: VideoTileObserverImpl
    private lateinit var audioVideoObserver: AudioVideoObserverImpl
    private lateinit var activeSpeakerObserver: ActiveSpeakerObserverImpl
    private lateinit var dataMessageObserver: DataMessageObserverImpl

    private var cameraCaptureSource: DefaultCameraCaptureSource? = null

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

    actual fun joinMeeting(
        realTimeListener: RealTimeEventListener,
        onActiveSpeakersChanged: (Set<String>) -> Unit,
        onConnectionStatusChanged: (ConnectionStatus) -> Unit,
        onRemoteVideoAvailable: (Boolean, Int) -> Unit,
        onCameraSendAvailable: (Boolean) -> Unit,
        onSessionError: (String, Boolean) -> Unit,
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
            isJoiningOnMute = isJoiningOnMute
        )
        meetingSession.audioVideo.addAudioVideoObserver(audioVideoObserver)

        activeSpeakerObserver = ActiveSpeakerObserverImpl(onActiveSpeakersChanged)
        meetingSession.audioVideo.addActiveSpeakerObserver(
            observer = activeSpeakerObserver,
            policy = DefaultActiveSpeakerPolicy()
        )

        dataMessageObserver = DataMessageObserverImpl(meetingSession)

        meetingSession.audioVideo.start()
        meetingSession.audioVideo.startRemoteVideo()
    }

    actual fun getActiveAudioDevice(): AudioDevice? = meetingSession.audioVideo
        .getActiveAudioDevice()
        ?.let { device ->
            val type = when (device.type) {
                MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                MediaDeviceType.AUDIO_HANDSET -> AudioDeviceType.BUILT_IN_MIC
                MediaDeviceType.AUDIO_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
                else -> return null
            }

            return AudioDevice(
                type = type,
                label = device.label
            )
        }

    actual fun leaveMeeting() {
        cameraCaptureSource?.stop()
        cameraCaptureSource = null

        meetingSession.audioVideo.removeRealtimeObserver(realTimeObserver)
        meetingSession.audioVideo.removeDeviceChangeObserver(deviceObserver)
        meetingSession.audioVideo.removeVideoTileObserver(videoTileObserver)
        meetingSession.audioVideo.removeAudioVideoObserver(audioVideoObserver)
        meetingSession.audioVideo.removeActiveSpeakerObserver(activeSpeakerObserver)
        dataMessageObserver.clearListeners()

        meetingSession.audioVideo.stopLocalVideo()
        meetingSession.audioVideo.stopRemoteVideo()
        meetingSession.audioVideo.stop()
    }

    actual fun startLocalVideo(cameraFacing: CameraFacing) {
        val cameraManager = applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camera = MediaDevice.listVideoDevices(cameraManager).first {
            it.type == if (cameraFacing == CameraFacing.FRONT) MediaDeviceType.VIDEO_FRONT_CAMERA else MediaDeviceType.VIDEO_BACK_CAMERA
        }

        val factory = DefaultSurfaceTextureCaptureSourceFactory(logger, eglCoreFactory)
        cameraCaptureSource = DefaultCameraCaptureSource(
            context = applicationContext,
            logger = logger,
            surfaceTextureCaptureSourceFactory = factory,
            eventAnalyticsController = eventAnalyticsController
        ).apply {
            device = camera
            start()

            meetingSession.audioVideo.startLocalVideo(this)
        }
    }

    actual fun stopLocalVideo() {
        meetingSession.audioVideo.stopLocalVideo()
        cameraCaptureSource?.stop()
        cameraCaptureSource = null
    }

    @Composable
    actual fun LocalVideoView(cameraFacing: CameraFacing, modifier: Modifier) {
        val mirror = remember(cameraFacing) { cameraFacing == CameraFacing.FRONT }

        AndroidView(
            factory = {
                videoTileObserver.localRenderView.apply { this.mirror = mirror }
            },
            modifier = modifier,
            update = {
                it.mirror = mirror
            }
        )
    }

    @Composable
    actual fun RemoteVideoView(tileId: Int, modifier: Modifier) = AndroidView(
        factory = {
            videoTileObserver.getRemoteRenderView(tileId)
                ?: throw IllegalStateException("Remote view for tile $tileId not found")
        },
        modifier = modifier,
        update = {}
    )

    actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) =
        meetingSession.audioVideo.realtimeSendDataMessage(topic, data, lifetimeMs.toInt())

    actual fun setMute(shouldMute: Boolean): Boolean =
        if (shouldMute) meetingSession.audioVideo.realtimeLocalMute() else meetingSession.audioVideo.realtimeLocalUnmute()

    actual fun switchCamera() {
        cameraCaptureSource?.switchCamera()
    }

    actual fun torchAvailable(): Boolean = true

    actual fun torchEnabled(): Boolean = cameraCaptureSource?.torchEnabled ?: false

    actual fun setTorchEnabled(enabled: Boolean) {
        cameraCaptureSource?.torchEnabled = enabled
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
