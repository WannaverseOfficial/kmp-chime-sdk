package com.wannaverse.chimesdk

/**
 * Represents an audio output device available on the current platform.
 *
 * @property type Platform-specific device type constant.
 * @property label Human-readable device name.
 */
data class AudioDevice(val type: AudioDeviceType, val label: String)
