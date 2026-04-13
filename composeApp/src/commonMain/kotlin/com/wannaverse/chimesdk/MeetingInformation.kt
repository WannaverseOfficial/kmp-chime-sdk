package com.wannaverse.chimesdk

data class MeetingInformation(
    val externalMeetingId: String = "778889999",
    val meetingId: String = "00e094e6-4ef6-480a-a733-ab0bb2173049",
    val audioHostURL: String = "eb4fd73515a481d2c6e78a937adb4809.k.m3.ew2.app.chime.aws:3478",
    val audioFallbackURL: String = "wss://wss.k.m3.ew2.app.chime.aws:443/calls/00e094e6-4ef6-480a-a733-ab0bb2173049",
    val turnControlURL: String = "https://3049.cell.eu-west-2.meetings.chime.aws/v2/turn_sessions",
    val signalingURL: String = "wss://signal.m3.ew2.app.chime.aws/control/00e094e6-4ef6-480a-a733-ab0bb2173049",
    val ingestionURL: String = "https://data.svc.ew2.ingest.chime.aws/v1/client-events",
    val attendeeId: String = "64d2ad14-bebf-ecff-d5cc-0480c1bf7821",
    val externalUserId: String = "778889999",
    val joinToken: String = "NjRkMmFkMTQtYmViZi1lY2ZmLWQ1Y2MtMDQ4MGMxYmY3ODIxOjllN2QxNTYxLTg0ZWQtNDk4Yi04Nzk1LTA2YjRiYTk1MmY0Mg"
)
