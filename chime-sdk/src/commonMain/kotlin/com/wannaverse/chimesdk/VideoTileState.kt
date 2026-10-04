package com.wannaverse.chimesdk

/**
 * Contains properties related to the current state of the VideoTile
 *
 * @property tileId Unique id associated with this tile
 * @property attendeeId Attendee id of the user associated with this tile
 * @property videoStreamContentWidth Width of video stream content
 * @property videoStreamContentHeight Height of video stream content
 * @property isLocalTile Whether the video tile is for the local attendee
 * @property isContent Whether the video tile is from screen share
 */
data class VideoTileState(
    val tileId: Int,
    val attendeeId: String,
    var videoStreamContentWidth: Int,
    var videoStreamContentHeight: Int,
    val isLocalTile: Boolean,
    val isContent: Boolean
)