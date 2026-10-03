package com.wannaverse.chimesdk

interface VideoTileEventListener {
    fun onLocalVideoTileAdded(videoTileState: VideoTileState)
    fun onLocalVideoTileRemoved(videoTileState: VideoTileState)

    fun onRemoteVideoTileAdded(videoTileState: VideoTileState)
    fun onRemoteVideoTileRemoved(videoTileState: VideoTileState)
}