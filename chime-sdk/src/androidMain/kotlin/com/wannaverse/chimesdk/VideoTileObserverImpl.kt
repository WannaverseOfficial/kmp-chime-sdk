package com.wannaverse.chimesdk

import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileObserver
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.VideoTileState
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.gl.TextureRenderView
import com.amazonaws.services.chime.sdk.meetings.session.MeetingSession

private fun VideoTileState.toVideoTileStateCommon() = VideoTileState(
    tileId, attendeeId, videoStreamContentWidth, videoStreamContentHeight, isLocalTile, isContent
)

class VideoTileObserverImpl(
    private val meetingSession: MeetingSession,
    private val videoTileEventListener: VideoTileEventListener
) : VideoTileObserver {
    internal val localRenderView = TextureRenderView(ChimeSDK.activity)
    internal val remoteRenderView = mutableMapOf<Int, TextureRenderView>()

    override fun onVideoTileAdded(tileState: VideoTileState) {
        if (tileState.isLocalTile) {
            meetingSession.audioVideo.bindVideoView(localRenderView, tileState.tileId)
            videoTileEventListener.onLocalVideoTileAdded(tileState.toVideoTileStateCommon())
        } else {
            remoteRenderView[tileState.tileId] = TextureRenderView(ChimeSDK.activity)
            meetingSession.audioVideo.bindVideoView(remoteRenderView[tileState.tileId]!!, tileState.tileId)
            videoTileEventListener.onRemoteVideoTileAdded(tileState.toVideoTileStateCommon())
        }
    }

    override fun onVideoTileRemoved(tileState: VideoTileState) {
        meetingSession.audioVideo.unbindVideoView(tileState.tileId)

        if (tileState.isLocalTile) {
            videoTileEventListener.onLocalVideoTileRemoved(tileState.toVideoTileStateCommon())
        } else {
            remoteRenderView -= tileState.tileId
            videoTileEventListener.onRemoteVideoTileRemoved(tileState.toVideoTileStateCommon())
        }
    }

    override fun onVideoTilePaused(tileState: VideoTileState) {}

    override fun onVideoTileResumed(tileState: VideoTileState) {}

    override fun onVideoTileSizeChanged(tileState: VideoTileState) {}
}
