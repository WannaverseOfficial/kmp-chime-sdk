package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.gl.TextureRenderView
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSession

class VideoTileObserverImpl(
    private val meetingSession: MeetingSession,
    private val onLocalTileAdded: (Int) -> Unit,
    private val onLocalTileRemoved: () -> Unit,
    private val onRemoteTileAdded: (Int) -> Unit,
    private val onRemoteTileRemoved: () -> Unit
) : VideoTileObserver {
    internal val localRenderView = TextureRenderView(appContext)
    private val remoteRenderView: MutableMap<Int, TextureRenderView> = mutableMapOf()

    fun getRemoteRenderView(tileId: Int): TextureRenderView? = remoteRenderView[tileId]

    override fun onVideoTileAdded(tileState: VideoTileState) {
        if (tileState.isLocalTile) {
            meetingSession.audioVideo.bindVideoView(localRenderView, tileState.tileId)
            onLocalTileAdded(tileState.tileId)
        } else {
            remoteRenderView[tileState.tileId] = TextureRenderView(appContext)
            meetingSession.audioVideo.bindVideoView(remoteRenderView[tileState.tileId]!!, tileState.tileId)
            onRemoteTileAdded(tileState.tileId)
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        if (tileState.isLocalTile) {
            meetingSession.audioVideo.unbindVideoView(tileState.tileId)
            onLocalTileRemoved()
        } else {
            meetingSession.audioVideo.unbindVideoView(tileState.tileId)
            remoteRenderView.remove(tileState.tileId)
            onRemoteTileRemoved()
        }
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {}

    override fun onVideoTileResumed(tileState: VideoTileState) {}

    override fun onVideoTileSizeChanged(tileState: VideoTileState) {}
}
