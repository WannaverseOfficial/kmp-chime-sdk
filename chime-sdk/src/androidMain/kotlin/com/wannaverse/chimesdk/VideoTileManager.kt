package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState

class VideoTileManager(
    private val onLocalTileAdded: (Int) -> Unit,
    private val onLocalTileRemoved: () -> Unit,
    private val onRemoteTileAdded: (Int) -> Unit,
    private val onRemoteTileRemoved: () -> Unit
) : VideoTileObserver {
    companion object {
        private const val LOG_TAG = "VIDEO_TILE_MANAGER"
    }

    var localTileId: Int? = null
        private set
    var remoteTileId: Int? = null
        private set

    private fun logTileInfo(event: String, tileState: VideoTileState) {
        println("$LOG_TAG | $event | TileId=${tileState.tileId} | IsLocal=${tileState.isLocalTile} | AttendeeId=${tileState.attendeeId} | Dimensions=${tileState.videoStreamContentWidth}x${tileState.videoStreamContentHeight} | Paused=${tileState.pauseState}")
    }

    override fun onVideoTileAdded(tileState: VideoTileState) {
        logTileInfo("TILE_ADDED", tileState)

        if (tileState.isLocalTile) {
            localTileId = tileState.tileId
            onLocalTileAdded(tileState.tileId)
        }
        else {
            remoteTileId = tileState.tileId
            onRemoteTileAdded(tileState.tileId)
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        logTileInfo("TILE_REMOVED", tileState)

        if (tileState.isLocalTile) {
            localTileId = null
            onLocalTileRemoved()
        }
        else {
            remoteTileId = null
            onRemoteTileRemoved()
        }
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {
        logTileInfo("TILE_PAUSED", tileState)

        if (tileState.isLocalTile) {
            println("$LOG_TAG | LOCAL_VIDEO_PAUSED | Local video feed paused (user action or app backgrounded)")
        } else {
            println("$LOG_TAG | REMOTE_VIDEO_PAUSED | Remote video feed paused (network/bandwidth issues)")
        }
    }

    override fun onVideoTileResumed(tileState: VideoTileState) {
        logTileInfo("TILE_RESUMED", tileState)

        if (tileState.isLocalTile) {
            println("$LOG_TAG | LOCAL_VIDEO_RESUMED | Local video feed resumed (user action or app foregrounded)")
        } else {
            println("$LOG_TAG | REMOTE_VIDEO_RESUMED | Remote video feed resumed (network/bandwidth improved)")
        }
    }

    override fun onVideoTileSizeChanged(tileState: VideoTileState) {
        logTileInfo("TILE_SIZE_CHANGED", tileState)

        if (tileState.isLocalTile) {
            println("$LOG_TAG | LOCAL_TILE_SIZE_CHANGED | Local tile size changed to ${tileState.videoStreamContentWidth}x${tileState.videoStreamContentHeight}")
        } else {
            println("$LOG_TAG | REMOTE_TILE_SIZE_CHANGED | Remote tile size changed to ${tileState.videoStreamContentWidth}x${tileState.videoStreamContentHeight}")
        }
    }

    private val boundViews = mutableMapOf<Int, Any>()

    fun isLocalTile(tileId: Int?): Boolean = tileId != null && tileId == localTileId

    fun isRemoteTile(tileId: Int?): Boolean = tileId != null && tileId == remoteTileId

    fun updateBoundView(tileId: Int, view: Any) = boundViews.set(tileId, view)

    fun isAlreadyBound(tileId: Int, view: Any): Boolean = boundViews[tileId] === view

    fun clearBoundView(tileId: Int) = boundViews.remove(tileId)

    fun clearAll() {
        println("VIDEO_TILE_VIEW | clearAll | Unbinding all video tiles (${boundViews.size})")

        boundViews.forEach { (tileId, _) ->
            try {
                println("VIDEO_TILE_VIEW | clearAll | Unbinding tile $tileId")
//                meetingSession?.audioVideo?.unbindVideoView(tileId)
            } catch (e: Exception) {
                println("VIDEO_TILE_VIEW | clearAll | Failed to unbind tile $tileId: ${e.message}")
            }
        }

        boundViews.clear()
        localTileId = null
    }
}
