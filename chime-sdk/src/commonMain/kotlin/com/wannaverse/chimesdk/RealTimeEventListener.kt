package com.wannaverse.chimesdk

/**
 * Callbacks for real-time attendee and audio events within a meeting session.
 *
 * Implement this interface and pass it to [joinMeeting] to receive live updates.
 */
interface RealTimeEventListener {
    /** Invoked when one or more attendees join the meeting. */
    fun onAttendeesJoined(attendeeInfos: List<AttendeeInfo>)

    /** Invoked when one or more attendees are dropped unexpectedly. */
    fun onAttendeesDropped(attendeeInfos: List<AttendeeInfo>)

    /** Invoked when one or more attendees leave the meeting cleanly. */
    fun onAttendeesLeft(attendeeInfos: List<AttendeeInfo>)

    /** Invoked when one or more attendees mute themselves. */
    fun onAttendeesMuted(attendeeInfos: List<AttendeeInfo>)

    /** Invoked when one or more attendees unmute themselves. */
    fun onAttendeesUnmuted(attendeeInfos: List<AttendeeInfo>)

    /**
     * Invoked when an attendee's signal strength changes.
     *
     * @param attendeeInfo The attendee whose signal strength changed.
     * @param signal Signal strength in the range [0, 1] where 1 is strongest.
     */
    fun onSignalStrengthChanged(attendeeInfo: AttendeeInfo, signal: Int)

    /**
     * Invoked when an attendee's audio volume changes.
     *
     * @param attendeeInfo The attendee whose volume changed.
     * @param volume Volume level in the range [0, 1] where 1 is loudest.
     */
    fun onVolumeChanged(attendeeInfo: AttendeeInfo, volume: Int)

    /**
     * Invoked when the list of available audio devices changes or the active device changes.
     *
     * @param audioDevices All currently available audio devices.
     * @param selectedDevice The currently active device, or null if none is selected.
     */
    fun onAudioDevicesUpdated(
        audioDevices: List<AudioDevice>,
        selectedDevice: AudioDevice?
    )
}
