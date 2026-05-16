package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.device.DeviceChangeObserver
import com.amazonaws.services.chime.sdk.meetings.device.MediaDevice
import com.amazonaws.services.chime.sdk.meetings.device.MediaDeviceType
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession

class DeviceObserver(
    private val meetingSession: DefaultMeetingSession,
    private val realTimeEventListener: RealTimeEventListener
) : DeviceChangeObserver {
    private var currentSelectedDevice: MediaDevice? = null

    override fun onAudioDeviceChanged(freshAudioDeviceList: List<MediaDevice>) {
        val devices = freshAudioDeviceList.map { device ->
            val type = when (device.type) {
                MediaDeviceType.AUDIO_BLUETOOTH -> AudioDeviceType.BLUETOOTH
                MediaDeviceType.AUDIO_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER
                MediaDeviceType.AUDIO_WIRED_HEADSET -> AudioDeviceType.WIRED_HEADSET
                MediaDeviceType.AUDIO_USB_HEADSET -> AudioDeviceType.EARPIECE
                MediaDeviceType.AUDIO_HANDSET -> AudioDeviceType.BUILT_IN_MIC
                else -> AudioDeviceType.UNKNOWN
            }

            AudioDevice(
                label = device.label,
                type = type,
            )
        }

        if (!freshAudioDeviceList.contains(currentSelectedDevice)) selectAudioDevice(
            freshAudioDeviceList.first()
        )

        val selected = devices.firstOrNull { it.label == currentSelectedDevice?.label }
        realTimeEventListener.onAudioDevicesUpdated(devices, selectedDevice = selected)
    }

    fun selectAudioDevice(device: MediaDevice) {
        currentSelectedDevice = device
        meetingSession.audioVideo.chooseAudioDevice(device)
    }

    fun clearCurrentDevice() {
        currentSelectedDevice = null
    }
}
