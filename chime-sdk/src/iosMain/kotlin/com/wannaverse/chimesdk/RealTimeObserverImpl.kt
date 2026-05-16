package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.AttendeeInfo
import cocoapods.AmazonChimeSDK.RealtimeObserverProtocol
import cocoapods.AmazonChimeSDK.SignalUpdate
import cocoapods.AmazonChimeSDK.VolumeUpdate
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class RealTimeObserverImpl(val listener: RealTimeEventListener) : NSObject(), RealtimeObserverProtocol {
    init {
        val _this: RealtimeObserverProtocol = this
    }

    override fun attendeesDidUnmuteWithAttendeeInfo(attendeeInfo: List<*>) =
        listener.onAttendeesDropped(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })

    override fun attendeesDidDropWithAttendeeInfo(attendeeInfo: List<*>) =
        listener.onAttendeesJoined(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })

    override fun attendeesDidJoinWithAttendeeInfo(attendeeInfo: List<*>) =
        listener.onAttendeesJoined(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })

    override fun attendeesDidMuteWithAttendeeInfo(attendeeInfo: List<*>) =
        listener.onAttendeesMuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })

    override fun attendeesDidLeaveWithAttendeeInfo(attendeeInfo: List<*>) =
        listener.onAttendeesLeft(attendeeInfo.filterIsInstance<AttendeeInfo>().map { it.attendeeId() })

    override fun volumeDidChangeWithVolumeUpdates(volumeUpdates: List<*>) =
        volumeUpdates.filterIsInstance<VolumeUpdate>().forEach { update ->
            listener.onVolumeChanged(
                attendeeId = update.attendeeInfo().attendeeId(),
                externalAttendeeId = update.attendeeInfo().externalUserId(),
                volume = update.volumeLevel().toInt()
            )
        }

    override fun signalStrengthDidChangeWithSignalUpdates(signalUpdates: List<*>) =
        signalUpdates.filterIsInstance<SignalUpdate>().forEach { update ->
            listener.onSignalStrengthChanged(
                attendeeId = update.attendeeInfo().attendeeId(),
                externalAttendeeId = update.attendeeInfo().externalUserId(),
                signal = update.signalStrength().toInt()
            )
        }
}