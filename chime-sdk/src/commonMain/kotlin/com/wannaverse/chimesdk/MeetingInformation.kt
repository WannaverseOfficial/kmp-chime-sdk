package com.wannaverse.chimesdk

/**
 * All credentials required to join a Chime meeting as a single attendee.
 *
 * Obtain these values from the AWS Chime SDK `CreateMeeting` and `CreateAttendee` API responses.
 */
data class MeetingInformation(
    /** Your app-defined meeting identifier. */
    val externalMeetingId: String = "",
    /** Chime meeting ID returned by CreateMeeting. */
    val meetingId: String = "",
    /** Media server host for audio (UDP/SRTP). */
    val audioHostURL: String = "",
    /** WebSocket fallback URL used when UDP is blocked. */
    val audioFallbackURL: String = "",
    /** TURN credential endpoint. */
    val turnControlURL: String = "",
    /** WebSocket signaling endpoint. */
    val signalingURL: String = "",
    /** Client event ingestion endpoint. */
    val ingestionURL: String = "",
    /** Chime attendee ID returned by CreateAttendee. */
    val attendeeId: String = "",
    /** Your app-defined user identifier. */
    val externalUserId: String = "",
    /** Attendee join token returned by CreateAttendee. */
    val joinToken: String = ""
)
