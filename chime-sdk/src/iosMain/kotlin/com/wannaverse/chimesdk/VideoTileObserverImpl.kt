package com.wannaverse.chimesdk

import cocoapods.AmazonChimeSDK.DefaultMeetingSession
import cocoapods.AmazonChimeSDK.DefaultVideoRenderView
import cocoapods.AmazonChimeSDK.VideoTileObserverProtocol
import cocoapods.AmazonChimeSDK.VideoTileState
import com.wannaverse.chimesdk.ChimeSDK.LocalVideoContainerView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView
import platform.darwin.NSObject
import kotlin.collections.get
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

    var localTileId: Long? = null
        private set

    val localRenderView: DefaultVideoRenderView = DefaultVideoRenderView().also { it.setMirror(true) }
    internal val localVideoContainer: LocalVideoContainerView = LocalVideoContainerView(localRenderView).also { it.addSubview(localRenderView) }

    private val attendeeTileMap: MutableMap<String, Long> = mutableMapOf()
    private val remoteTiles: MutableMap<Long, DefaultVideoRenderView> = mutableMapOf()

    override fun videoTileDidAddWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession.audioVideo()
                .bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalTileAdded(tileId.toInt())
        } else {
            val attendeeId = tileState.attendeeId()
            val oldTileId = attendeeTileMap[attendeeId]
            if (oldTileId != null && oldTileId != tileId) {
                meetingSession.audioVideo().unbindVideoViewWithTileId(tileId = oldTileId)
                remoteTiles.remove(oldTileId)
                attendeeTileMap.remove(attendeeId)
                onRemoteTileRemoved()
            }
            val renderView = remoteTiles.getOrPut(tileId) {
                DefaultVideoRenderView().also { it.setMirror(false) }
            }
            attendeeTileMap[attendeeId] = tileId
            renderView.setFrame(renderView.superview?.bounds ?: renderView.bounds)
            onRemoteTileAdded(tileId.toInt())
        }
    }

    override fun videoTileDidRemoveWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()

        meetingSession.audioVideo().unbindVideoViewWithTileId(tileId = tileId)

        if (tileId == localTileId) {
            localTileId = null
            onLocalTileRemoved()
        } else if (remoteTiles.containsKey(tileId)) {
            remoteTiles.remove(tileId)
            if (attendeeTileMap[tileState.attendeeId()] == tileId) {
                attendeeTileMap.remove(tileState.attendeeId())
            }
            onRemoteTileRemoved()
        }
    }

    override fun videoTileDidPauseWithTileState(tileState: VideoTileState) {}

    override fun videoTileDidResumeWithTileState(tileState: VideoTileState) {
        val tileId = tileState.tileId()
        if (tileState.isLocalTile()) {
            localTileId = tileId
            meetingSession.audioVideo().bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
            onLocalTileAdded(tileId.toInt())
        } else {
            val renderView = remoteTiles.getOrPut(tileId) {
                DefaultVideoRenderView().also { it.setMirror(false) }
            }
            attendeeTileMap[tileState.attendeeId()] = tileId
            renderView.setFrame(renderView.superview?.bounds ?: renderView.bounds)
            onRemoteTileAdded(tileId.toInt())
        }
    }

    override fun videoTileSizeDidChangeWithTileState(tileState: VideoTileState) {}

    fun getRemoteView(tileId: Int): UIView? = remoteTiles[tileId.toLong()]

    fun rebindLocalView() {
        val tileId = localTileId ?: return
        localRenderView.setFrame(localVideoContainer.bounds)
        meetingSession.audioVideo().bindVideoViewWithVideoView(videoView = localRenderView, tileId = tileId)
    }

    fun rebindRemoteView(tileId: Int) {
        val renderView = remoteTiles[tileId.toLong()] ?: return
        meetingSession.audioVideo().bindVideoViewWithVideoView(videoView = renderView, tileId = tileId.toLong())
    }

}