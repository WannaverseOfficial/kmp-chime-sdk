package com.wannaverse.chimesdk

/**
 * Callbacks for real-time attendee and audio events within a meeting session.
 *
 * Implement this interface and pass it to [joinMeeting] to receive live updates.
 */
interface RealTimeEventListener {
    /** Invoked when one or more attendees join the meeting. */
    fun onAttendeesJoined(attendees: List<Attendee>)

    /** Invoked when one or more attendees are dropped unexpectedly. */
    fun onAttendeesDropped(attendees: List<Attendee>)

    /** Invoked when one or more attendees leave the meeting cleanly. */
    fun onAttendeesLeft(attendees: List<Attendee>)

    /** Invoked when one or more attendees mute themselves. */
    fun onAttendeesMuted(attendees: List<Attendee>)

    /** Invoked when one or more attendees unmute themselves. */
    fun onAttendeesUnmuted(attendees: List<Attendee>)

    /**
     * Invoked when an attendee's signal strength changes.
     *
     * @param attendee The attendee whose signal strength changed.
     * @param signal Signal strength in the range [0, 1] where 1 is strongest.
     */
    fun onSignalStrengthChanged(attendee: Attendee, signal: Int)

    /**
     * Invoked when an attendee's audio volume changes.
     *
     * @param attendee The attendee whose volume changed.
     * @param volume Volume level in the range [0, 1] where 1 is loudest.
     */
    fun onVolumeChanged(attendee: Attendee, volume: Int)

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
