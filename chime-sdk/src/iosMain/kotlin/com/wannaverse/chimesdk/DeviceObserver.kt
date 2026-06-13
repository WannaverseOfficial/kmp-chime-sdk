package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.DeviceChangeObserverProtocol
import cocoapods.AmazonChimeSDK.MediaDevice
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBluetooth
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioBuiltInSpeaker
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioHandset
import cocoapods.AmazonChimeSDK.MediaDeviceTypeAudioWiredHeadset
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
class DeviceObserver(
    private val meetingSession: DefaultMeetingSession,
    private val realTimeEventListener: RealTimeEventListener
) : DeviceChangeObserverProtocol {
    init {
        val _this: DeviceChangeObserverProtocol = this

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
                    type = type,
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