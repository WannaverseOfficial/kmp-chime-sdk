package com.wannaverse.chimesdk

/**
 * Callbacks for real-time attendee and audio events within a meeting session.
 *
 * Implement this interface and pass it to [joinMeeting] to receive live updates.
 */
interface RealTimeEventListener {
    /** Invoked when one or more attendees join the meeting. */
    fun onAttendeesJoined(attendeeIds: List<String>)

    /** Invoked when one or more attendees are dropped unexpectedly. */
    fun onAttendeesDropped(attendeeIds: List<String>)

    /** Invoked when one or more attendees leave the meeting cleanly. */
    fun onAttendeesLeft(attendeeIds: List<String>)

    /** Invoked when one or more attendees mute themselves. */
    fun onAttendeesMuted(attendeeIds: List<String>)

    /** Invoked when one or more attendees unmute themselves. */
    fun onAttendeesUnmuted(attendeeIds: List<String>)

    /**
     * Invoked when an attendee's signal strength changes.
     *
     * @param attendeeId Chime attendee ID.
     * @param externalAttendeeId Your app-defined user identifier.
     * @param signal Signal strength in the range [0, 1] where 1 is strongest.
     */
    fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int)

    /**
     * Invoked when an attendee's audio volume changes.
     *
     * @param attendeeId Chime attendee ID.
     * @param externalAttendeeId Your app-defined user identifier.
     * @param volume Volume level in the range [0, 1] where 1 is loudest.
     */
    fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int)

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
