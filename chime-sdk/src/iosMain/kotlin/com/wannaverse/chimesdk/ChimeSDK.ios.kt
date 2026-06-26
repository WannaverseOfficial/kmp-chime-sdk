@file:OptIn(ExperimentalForeignApi::class)

package com.wannaverse.chimesdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import cocoapods.AmazonChimeSDK.ConsoleLogger
import cocoapods.AmazonChimeSDK.DefaultActiveSpeakerPolicy
import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.LogLevelINFO
import cocoapods.AmazonChimeSDK.MediaDevice
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBluetooth
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBuiltInSpeaker
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioHandset
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioWiredHeadset
import cocoapods.AmazonChimeSDK.MeetingSessionConfiguration
import cocoapods.AmazonChimeSDK.MeetingSessionCredentials
import cocoapods.AmazonChimeSDK.MeetingSessionURLs
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothA2DP
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetoothHFP
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVideoChat
import platform.AVFAudio.setActive
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject

private val logger = ConsoleLogger(name = "ChimeSDK", level = LogLevelINFO)

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ChimeSDK(
    private val meetingSession: DefaultMeetingSession
) : NSObject() {
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

            val meetingSession =
                DefaultMeetingSession(configuration = configuration, logger = logger)

            return ChimeSDK(meetingSession)
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

    private lateinit var realtimeObserver: RealTimeObserverImpl
    private lateinit var deviceObserver: DeviceObserverImpl
    private lateinit var videoTileObserver: VideoTileObserverImpl
    private lateinit var audioVideoObserver: AudioVideoObserverImpl
    private lateinit var activeSpeakerObserver: ActiveSpeakerObserverImpl
    private lateinit var dataMessageObserver: DataMessageObserverImpl

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
        realtimeObserver = RealTimeObserverImpl(realTimeListener)
        meetingSession.audioVideo().addRealtimeObserverWithObserver(realtimeObserver)

        deviceObserver = DeviceObserverImpl(meetingSession, realTimeListener)
        meetingSession.audioVideo().addDeviceChangeObserverWithObserver(deviceObserver)

        meetingSession.audioVideo().listAudioDevices()
            .filterIsInstance<MediaDevice>()
            .firstOrNull { it.label() == selectedAudioInputDevice }
            ?.let(deviceObserver::selectAudioDevice)

        videoTileObserver = VideoTileObserverImpl(
            meetingSession = meetingSession,
            onLocalTileAdded = onLocalTileAdded,
            onLocalTileRemoved = onLocalTileRemoved,
            onRemoteTileAdded = onRemoteTileAdded,
            onRemoteTileRemoved = onRemoteTileRemoved
        )
        meetingSession.audioVideo().addVideoTileObserverWithObserver(observer = videoTileObserver)

        audioVideoObserver = AudioVideoObserverImpl(
            meetingSession = meetingSession,
            onConnectionStatusChanged = onConnectionStatusChanged,
            onRemoteVideoAvailable = onRemoteVideoAvailable,
            onCameraSendAvailable = onCameraSendAvailable,
            onSessionError = onSessionError,
            onVideoNeedsRestart = onVideoNeedsRestart,
            isJoiningOnMute = isJoiningOnMute
        )
        meetingSession.audioVideo().addAudioVideoObserverWithObserver(audioVideoObserver)

        activeSpeakerObserver = ActiveSpeakerObserverImpl(onActiveSpeakersChanged)
        meetingSession.audioVideo().addActiveSpeakerObserverWithPolicy(
            policy = DefaultActiveSpeakerPolicy(),
            observer = activeSpeakerObserver
        )

        dataMessageObserver = DataMessageObserverImpl(meetingSession)

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
        meetingSession.audioVideo().removeRealtimeObserverWithObserver(realtimeObserver)
        meetingSession.audioVideo().removeDeviceChangeObserverWithObserver(deviceObserver)
        meetingSession.audioVideo().removeVideoTileObserverWithObserver(videoTileObserver)
        meetingSession.audioVideo().removeAudioVideoObserverWithObserver(audioVideoObserver)
        meetingSession.audioVideo().removeActiveSpeakerObserverWithObserver(activeSpeakerObserver)
        dataMessageObserver.clearListeners()

        meetingSession.audioVideo().stopLocalVideo()
        meetingSession.audioVideo().stopRemoteVideo()
        meetingSession.audioVideo().stop()
    }

    actual fun startLocalVideo() {
        meetingSession.audioVideo().startLocalVideoAndReturnError(error = null)
    }

    actual fun stopLocalVideo() {
        meetingSession.audioVideo().stopLocalVideo()
    }

    @Composable
    actual fun LocalVideoView(cameraFacing: CameraFacing, modifier: Modifier) {
        val mirror = remember(cameraFacing) { cameraFacing == CameraFacing.FRONT }

        UIKitView(
            factory = {
                videoTileObserver.localRenderView.apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                    layer.masksToBounds = true
                    setMirror(mirror)
                }
            },
            modifier = modifier,
            update = {
                it.setMirror(mirror)
            }
        )
    }

    @Composable
    actual fun RemoteVideoView(tileId: Int, modifier: Modifier) = UIKitView(
        factory = {
            (videoTileObserver.getRemoteView(tileId)
                ?: throw IllegalArgumentException("Remote view for tile $tileId not found")
            ).apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                layer.masksToBounds = true
            }
        },
        modifier = modifier,
        update = {}
    )

    actual fun sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long) {
        meetingSession.audioVideo().realtimeSendDataMessageWithTopic(
            topic = topic,
            data = data,
            lifetimeMs = lifetimeMs.toInt(),
            error = null
        )
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
        dataMessageObserver.addListener(topic, listener)

    actual fun unsubscribeFromTopic(topic: String) = dataMessageObserver.removeListener(topic)

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
}