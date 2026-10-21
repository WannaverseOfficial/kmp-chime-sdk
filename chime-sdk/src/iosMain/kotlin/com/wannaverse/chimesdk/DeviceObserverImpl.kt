package com.wannaverse.chimesdk

import swiftPMImport.com.wannaverse.chime.sdk.DefaultMeetingSession
import swiftPMImport.com.wannaverse.chime.sdk.DeviceChangeObserverProtocol
import swiftPMImport.com.wannaverse.chime.sdk.MediaDevice
import swiftPMImport.com.wannaverse.chime.sdk.MediaDeviceTypeAudioBluetooth
import swiftPMImport.com.wannaverse.chime.sdk.MediaDeviceTypeAudioBuiltInSpeaker
import swiftPMImport.com.wannaverse.chime.sdk.MediaDeviceTypeAudioHandset
import swiftPMImport.com.wannaverse.chime.sdk.MediaDeviceTypeAudioWiredHeadset
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class DeviceObserverImpl(
    private val meetingSession: DefaultMeetingSession,
    private val realTimeEventListener: RealTimeEventListener
) : NSObject(),
    DeviceChangeObserverProtocol {
    init {
        ProtocolDescriptor(
            candidates = listOf("DeviceChangeObserver", "_TtP14AmazonChimeSDK20DeviceChangeObserver_")
        ).forceRegisterProtocol(this)
    }

    private var currentSelectedDevice: MediaDevice? = null

    override fun audioDeviceDidChangeWithFreshAudioDeviceList(freshAudioDeviceList: List<*>) {
        val devices = freshAudioDeviceList.filterIsInstance<MediaDevice>()
            .map { device ->
                val type = when (device.type()) {
                    MediaDeviceTypeAudioBluetooth -> AudioDeviceType.BLUETOOTH
                    MediaDeviceTypeAudioBuiltInSpeaker -> AudioDeviceType.SPEAKER
                    MediaDeviceTypeAudioWiredHeadset -> AudioDeviceType.WIRED_HEADSET
                    MediaDeviceTypeAudioHandset -> AudioDeviceType.BUILT_IN_MIC
                    else -> AudioDeviceType.UNKNOWN
                }

                AudioDevice(
                    label = device.label(),
                    type = type
                )
            }

        if (!freshAudioDeviceList.contains(currentSelectedDevice))
            selectAudioDevice(freshAudioDeviceList.filterIsInstance<MediaDevice>().first())

        val selected = devices.firstOrNull { it.label == currentSelectedDevice?.label() }
        realTimeEventListener.onAudioDevicesUpdated(devices, selectedDevice = selected)
    }

    fun selectAudioDevice(device: MediaDevice) {
        currentSelectedDevice = device
        meetingSession.audioVideo().chooseAudioDeviceWithMediaDevice(device)
    }
}
