package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.AttendeeInfo
import com.amazonaws.services.chime.sdk.meetings.audiovideo.SignalUpdate
import com.amazonaws.services.chime.sdk.meetings.audiovideo.VolumeUpdate
import com.amazonaws.services.chime.sdk.meetings.realtime.RealtimeObserver

class RealTimeObserverImpl(val listener: RealTimeEventListener) : RealtimeObserver {
    override fun onAttendeesDropped(attendeeInfo: Array<AttendeeInfo>) =
        listener.onAttendeesDropped(attendeeInfo.map(AttendeeInfo::attendeeId))

    override fun onAttendeesJoined(attendeeInfo: Array<AttendeeInfo>) =
        listener.onAttendeesJoined(attendeeInfo.map(AttendeeInfo::attendeeId))

    override fun onAttendeesLeft(attendeeInfo: Array<AttendeeInfo>) =
        listener.onAttendeesLeft(attendeeInfo.map(AttendeeInfo::attendeeId))

    override fun onAttendeesMuted(attendeeInfo: Array<AttendeeInfo>) =
        listener.onAttendeesMuted(attendeeInfo.map(AttendeeInfo::attendeeId))

    override fun onAttendeesUnmuted(attendeeInfo: Array<AttendeeInfo>) =
        listener.onAttendeesUnmuted(attendeeInfo.map(AttendeeInfo::attendeeId))

    override fun onSignalStrengthChanged(signalUpdates: Array<SignalUpdate>) {
        signalUpdates.forEach { update ->
            listener.onSignalStrengthChanged(
                attendeeId = update.attendeeInfo.attendeeId,
                externalAttendeeId = update.attendeeInfo.externalUserId,
                signal = update.signalStrength.value
            )
        }
    }

    override fun onVolumeChanged(volumeUpdates: Array<VolumeUpdate>) {
        volumeUpdates.forEach { update ->
            listener.onVolumeChanged(
                attendeeId = update.attendeeInfo.attendeeId,
                externalAttendeeId = update.attendeeInfo.externalUserId,
                volume = update.volumeLevel.value
            )
        }
    }
}