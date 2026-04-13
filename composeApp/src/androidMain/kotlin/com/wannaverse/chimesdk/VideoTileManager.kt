package com.wannaverse.chimesdk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object VideoTileManager : VideoTileObserver {
    var localTileId by mutableStateOf<Int?>(null)
    var remoteTileId by mutableStateOf<Int?>(null)

    var onLocalTileAdded: (() -> Unit)? = null
    var onLocalTileRemoved: (() -> Unit)? = null
    var onRemoteTileAdded: (() -> Unit)? = null
    var onRemoteTileRemoved: (() -> Unit)? = null

    private val boundViews = mutableMapOf<Int, Any?>()

    override fun onVideoTileAdded(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            if (tileState.isLocalTile) {
                localTileId = tileState.tileId
                onLocalTileAdded?.invoke()
            } else {
                remoteTileId = tileState.tileId
                onRemoteTileAdded?.invoke()
            }
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            when (tileState.tileId) {
                localTileId -> {
                    localTileId = null
                    onLocalTileRemoved?.invoke()
                }
                remoteTileId -> {
                    remoteTileId = null
                    onRemoteTileRemoved?.invoke()
                }
            }
        }
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {}

    override fun onVideoTileResumed(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            if (tileState.isLocalTile) localTileId = tileState.tileId
            else remoteTileId = tileState.tileId
        }
    }

    override fun onVideoTileSizeChanged(tileState: VideoTileState) {
        CoroutineScope(Dispatchers.Main).launch {
            if (tileState.isLocalTile) localTileId = tileState.tileId
            else remoteTileId = tileState.tileId
        }
    }

    fun updateBoundView(tileId: Int, view: Any) {
        boundViews[tileId] = view
    }

    fun isAlreadyBound(tileId: Int, view: Any): Boolean = boundViews[tileId] === view

    fun clearBoundView(tileId: Int) {
        boundViews.remove(tileId)
    }

    fun clearAll() {
        boundViews.forEach { (tileId, _) ->
            try {
                meetingSession?.audioVideo?.unbindVideoView(tileId)
            } catch (_: Exception) {}
        }
        boundViews.clear()
        localTileId = null
        remoteTileId = null
        onLocalTileAdded = null
        onLocalTileRemoved = null
        onRemoteTileAdded = null
        onRemoteTileRemoved = null
    }
}
