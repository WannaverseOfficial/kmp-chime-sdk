package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeUpdate
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver

private fun AttendeeInfo.toAttendeeInfoCommon() = com.wannaverse.chimesdk.AttendeeInfo(attendeeId, externalUserId)

class RealTimeObserverImpl(val listener: RealTimeEventListener) : RealtimeObserver {
    override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) = listener.onAttendeesDropped(attendeeInfo.map(AttendeeInfo::toAttendeeInfoCommon))

    override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) = listener.onAttendeesJoined(attendeeInfo.map(AttendeeInfo::toAttendeeInfoCommon))

    override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) = listener.onAttendeesLeft(attendeeInfo.map(AttendeeInfo::toAttendeeInfoCommon))

    override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) = listener.onAttendeesMuted(attendeeInfo.map(AttendeeInfo::toAttendeeInfoCommon))

    override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) = listener.onAttendeesUnmuted(attendeeInfo.map(AttendeeInfo::toAttendeeInfoCommon))

    override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) = signalUpdates.forEach { update ->
        listener.onSignalStrengthChanged(
            attendeeInfo = update.attendeeInfo.toAttendeeInfoCommon(),
            signal = update.signalStrength.value
        )
    }

    override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) = volumeUpdates.forEach { update ->
        listener.onVolumeChanged(
            attendeeInfo = update.attendeeInfo.toAttendeeInfoCommon(),
            volume = update.volumeLevel.value
        )
    }
}
