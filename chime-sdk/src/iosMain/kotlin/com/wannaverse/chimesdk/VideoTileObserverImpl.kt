package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.DefaultVideoRenderView
import cocoapods.AmazonChimeSDK.VideoTileObserverProtocol
import cocoapods.AmazonChimeSDK.VideoTileState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject
import kotlin.collections.set

@OptIn(ExperimentalForeignApi::class)
class VideoTileObserverImpl(
    private val meetingSession: DefaultMeetingSession,
    private val onLocalTileAdded: (Int) -> Unit,
    private val onLocalTileRemoved: () -> Unit,
    private val onRemoteTileAdded: (Int) -> Unit,
    private val onRemoteTileRemoved: () -> Unit
): NSObject(), VideoTileObserverProtocol {
    init {
        val _this: VideoTileObserverProtocol = this

        ProtocolDescriptor(
            candidates = listOf("VideoTileObserver", "_TtP14AmazonChimeSDK17VideoTileObserver_")
        ).forceRegisterProtocol(this)
    }

    internal val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView()
    private val remoteRenderView: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()

    fun getRemoteView(tileId: Int): DefaultVideoRenderView? = remoteRenderView[tileId.toLong()]

    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        if (tileState.isLocalTile()) {
            meetingSession.audioVideo()
                .bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalTileAdded(tileId.toInt())
        } else {
            remoteRenderView[tileId] = DefaultVideoRenderView()
            meetingSession.audioVideo()
                .bindVideoViewWithVideoView(videoView = remoteRenderView[tileId]!!, tileId = tileId)
            onRemoteTileAdded(tileId.toInt())
        }
    }

    override fun videoTileDidRemoveWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        meetingSession.audioVideo().unbindVideoViewWithTileId(tileId = tileId)

        if (tileState.isLocalTile()) {
            onLocalTileRemoved()
        } else if (remoteRenderView.containsKey(tileId)) {
            remoteRenderView -= tileId
            onRemoteTileRemoved()
        }
    }

    override fun videoTileDidPauseWithTileState(tileState: VideoTileState) {}

    override fun videoTileDidResumeWithTileState(tileState: VideoTileState) {}

    override fun videoTileSizeDidChangeWithTileState(tileState: VideoTileState) {}
}