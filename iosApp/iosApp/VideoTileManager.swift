import Foundation
import AmazonChimeSDK
import ComposeApp

/// Manages video tile binding between Chime SDK tiles and pre-created render views.
class VideoTileManager: NSObject, VideoTileObserver {

    private let localView: DefaultVideoRenderView
    private let remoteView: DefaultVideoRenderView
    private weak var audioVideo: (any AudioVideoFacade)?

    private var localTileId: Int?
    private var remoteTileId: Int?

    init(
        localView: DefaultVideoRenderView,
        remoteView: DefaultVideoRenderView,
        audioVideo: any AudioVideoFacade
    ) {
        self.localView = localView
        self.remoteView = remoteView
        self.audioVideo = audioVideo
    }

    // MARK: – VideoTileObserver

    func videoTileDidAdd(tileState: VideoTileState) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }

            if tileState.isLocalTile {
                self.localTileId = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: self.localView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onLocalVideoTileAdded(tileId: Int32(tileState.tileId))
            } else {
                self.remoteTileId = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: self.remoteView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileAdded(tileId: Int32(tileState.tileId))
            }
        }
    }

    func videoTileDidRemove(tileState: VideoTileState) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.audioVideo?.unbindVideoView(tileId: tileState.tileId)

            if tileState.tileId == self.localTileId {
                self.localTileId = nil
                ChimeSdkBridge.shared.eventDelegate?.onLocalVideoTileRemoved()
            } else if tileState.tileId == self.remoteTileId {
                self.remoteTileId = nil
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileRemoved()
            }
        }
    }

    func videoTileDidPause(tileState: VideoTileState) {}

    func videoTileDidResume(tileState: VideoTileState) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            if tileState.isLocalTile {
                self.localTileId = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: self.localView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onLocalVideoTileAdded(tileId: Int32(tileState.tileId))
            } else {
                self.remoteTileId = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: self.remoteView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileAdded(tileId: Int32(tileState.tileId))
            }
        }
    }

    func videoTileSizeDidChange(tileState: VideoTileState) {}
}
