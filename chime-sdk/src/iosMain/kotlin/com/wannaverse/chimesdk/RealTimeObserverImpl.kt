package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.AttendeeInfo
import cocoapods.AmazonChimeSDK.RealtimeObserverProtocol
import cocoapods.AmazonChimeSDK.SignalUpdate
import cocoapods.AmazonChimeSDK.VolumeUpdate
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private fun AttendeeInfo.toAttendeeInfoCommon() = com.wannaverse.chimesdk.AttendeeInfo(attendeeId(), externalUserId())

@OptIn(ExperimentalForeignApi::class)
class RealTimeObserverImpl(val listener: RealTimeEventListener) :
    NSObject(),
    RealtimeObserverProtocol {
    init {
        ProtocolDescriptor(
            candidates = listOf("RealtimeObserver", "_TtP14AmazonChimeSDK16RealtimeObserver_")
        ).forceRegisterProtocol(this)
    }

    override fun attendeesDidUnmuteWithAttendeeInfo(attendeeInfo: List<*>) = listener.onAttendeesUnmuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map(AttendeeInfo::toAttendeeInfoCommon))

    override fun attendeesDidDropWithAttendeeInfo(attendeeInfo: List<*>) = listener.onAttendeesDropped(attendeeInfo.filterIsInstance<AttendeeInfo>().map(AttendeeInfo::toAttendeeInfoCommon))

    override fun attendeesDidJoinWithAttendeeInfo(attendeeInfo: List<*>) = listener.onAttendeesJoined(attendeeInfo.filterIsInstance<AttendeeInfo>().map(AttendeeInfo::toAttendeeInfoCommon))

    override fun attendeesDidMuteWithAttendeeInfo(attendeeInfo: List<*>) = listener.onAttendeesMuted(attendeeInfo.filterIsInstance<AttendeeInfo>().map(AttendeeInfo::toAttendeeInfoCommon))

    override fun attendeesDidLeaveWithAttendeeInfo(attendeeInfo: List<*>) = listener.onAttendeesLeft(attendeeInfo.filterIsInstance<AttendeeInfo>().map(AttendeeInfo::toAttendeeInfoCommon))

    override fun volumeDidChangeWithVolumeUpdates(volumeUpdates: List<*>) = volumeUpdates.filterIsInstance<VolumeUpdate>().forEach { update ->
        listener.onVolumeChanged(
            attendeeInfo = update.attendeeInfo().toAttendeeInfoCommon(),
            volume = update.volumeLevel().toInt()
        )
    }

    override fun signalStrengthDidChangeWithSignalUpdates(signalUpdates: List<*>) = signalUpdates.filterIsInstance<SignalUpdate>().forEach { update ->
        listener.onSignalStrengthChanged(
            attendeeInfo = update.attendeeInfo().toAttendeeInfoCommon(),
            signal = update.signalStrength().toInt()
        )
    }
}
