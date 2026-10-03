package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.DefaultVideoRenderView
import cocoapods.AmazonChimeSDK.VideoTileObserverProtocol
import cocoapods.AmazonChimeSDK.VideoTileState
import kotlin.collections.set
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private fun VideoTileState.toVideoTileStateCommon() = VideoTileState(
    tileId = tileId().toInt(),
    attendeeId = attendeeId(),
    videoStreamContentWidth = videoStreamContentWidth().toInt(),
    videoStreamContentHeight = videoStreamContentHeight().toInt(),
    isLocalTile = isLocalTile(),
    isContent = isContent()
)

@OptIn(ExperimentalForeignApi::class)
class VideoTileObserverImpl(
    private val meetingSession: DefaultMeetingSession,
    private val videoTileEventListener: VideoTileEventListener
) : NSObject(),
    VideoTileObserverProtocol {
    init {
        ProtocolDescriptor(
            candidates = listOf("VideoTileObserver", "_TtP14AmazonChimeSDK17VideoTileObserver_")
        ).forceRegisterProtocol(this)
    }

    internal val localRenderView = DefaultVideoRenderView()
    internal val remoteRenderView = mutableMapOf<Int, DefaultVideoRenderView>()

    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId().toInt()

        if (tileState.isLocalTile()) {
            meetingSession.audioVideo()
                .bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileState.tileId())
            videoTileEventListener.onLocalVideoTileAdded(tileState.toVideoTileStateCommon())
        } else {
            remoteRenderView[tileId] = DefaultVideoRenderView()
            meetingSession.audioVideo()
                .bindVideoViewWithVideoView(videoView = remoteRenderView[tileId]!!, tileId = tileState.tileId())
            videoTileEventListener.onRemoteVideoTileAdded(tileState.toVideoTileStateCommon())
        }
    }

    override fun videoTileDidRemoveWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId().toInt()

        meetingSession.audioVideo().unbindVideoViewWithTileId(tileId = tileState.tileId())

        if (tileState.isLocalTile()) {
            videoTileEventListener.onLocalVideoTileRemoved(tileState.toVideoTileStateCommon())
        } else if (remoteRenderView.containsKey(tileId)) {
            remoteRenderView -= tileId
            videoTileEventListener.onRemoteVideoTileRemoved(tileState.toVideoTileStateCommon())
        }
    }

    override fun videoTileDidPauseWithTileState(tileState: VideoTileState) {}

    override fun videoTileDidResumeWithTileState(tileState: VideoTileState) {}

    override fun videoTileSizeDidChangeWithTileState(tileState: VideoTileState) {}
}
