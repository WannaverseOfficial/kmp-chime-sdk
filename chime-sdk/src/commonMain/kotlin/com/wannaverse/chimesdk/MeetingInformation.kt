package com.wannaverse.chimesdk

data class MeetingInformation(
    val externalMeetingId: String = "778889999",
    val meetingId: String = "db9b4796-7e91-4295-af4e-d3291a6b3049",
    val audioHostURL: String = "1b2c48c12bb3e379a70c4c9cf62aa9a1.k.m3.ew2.app.chime.aws:3478",
    val audioFallbackURL: String = "wss://wss.k.m3.ew2.app.chime.aws:443/calls/db9b4796-7e91-4295-af4e-d3291a6b3049",
    val turnControlURL: String = "https://3049.cell.eu-west-2.meetings.chime.aws/v2/turn_sessions",
    val signalingURL: String = "wss://signal.m3.ew2.app.chime.aws/control/db9b4796-7e91-4295-af4e-d3291a6b3049",
    val ingestionURL: String = "https://data.svc.ew2.ingest.chime.aws/v1/client-events",
    // iOS attendee credentials (Android overrides these via MainActivity)
    val attendeeId: String = "bb5446b0-c033-fd64-712e-713e63b360e1",
    val externalUserId: String = "778889999",
    val joinToken: String = "YmI1NDQ2YjAtYzAzMy1mZDY0LTcxMmUtNzEzZTYzYjM2MGUxOmViMThjOTBiLTVkMjctNGI3MS05ODgyLTQwYjI1N2I0MWQyYw"
)
