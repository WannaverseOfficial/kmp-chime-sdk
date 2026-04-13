import Foundation
import AmazonChimeSDK
import ComposeApp

/// Manages video tile binding between Chime SDK tiles and render views.
/// Creates one DefaultVideoRenderView per remote tile on demand.
/// Tracks tiles by attendeeId so that a camera flip (which reassigns tileId)
/// atomically replaces the old tile rather than momentarily showing two.
class VideoTileManager: NSObject, VideoTileObserver {

    private let localView: DefaultVideoRenderView
    private weak var audioVideo: (any AudioVideoFacade)?

    private var localTileId: Int?
    /// tileId → render view
    private var remoteTiles: [Int: DefaultVideoRenderView] = [:]
    /// attendeeId → current tileId (prevents duplicate tiles on camera flip)
    private var attendeeTileMap: [String: Int] = [:]

    init(
        localView: DefaultVideoRenderView,
        audioVideo: any AudioVideoFacade
    ) {
        self.localView = localView
        self.audioVideo = audioVideo
    }

    // MARK: – Public accessor for Compose bridge

    func remoteView(for tileId: Int) -> DefaultVideoRenderView? {
        return remoteTiles[tileId]
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
                let attendeeId = tileState.attendeeId

                // If this attendee already owns a different tile (camera flip scenario),
                // atomically remove the old tile before adding the new one.
                if let oldTileId = self.attendeeTileMap[attendeeId], oldTileId != tileState.tileId {
                    self.audioVideo?.unbindVideoView(tileId: oldTileId)
                    self.remoteTiles.removeValue(forKey: oldTileId)
                    self.attendeeTileMap.removeValue(forKey: attendeeId)
                    ChimeSdkBridge.shared.eventDelegate?.onRemoteTileRemoved(tileId: Int32(oldTileId))
                }

                let renderView: DefaultVideoRenderView
                if let existing = self.remoteTiles[tileState.tileId] {
                    renderView = existing
                } else {
                    renderView = DefaultVideoRenderView()
                    renderView.mirror = false  // remote video must never be mirrored
                }
                self.remoteTiles[tileState.tileId] = renderView
                self.attendeeTileMap[attendeeId] = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: renderView, tileId: tileState.tileId)
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
            } else if self.remoteTiles[tileState.tileId] != nil {
                self.remoteTiles.removeValue(forKey: tileState.tileId)
                // Only remove from attendeeTileMap if it still points to this tile
                // (may already have been updated by a camera-flip add above)
                if self.attendeeTileMap[tileState.attendeeId] == tileState.tileId {
                    self.attendeeTileMap.removeValue(forKey: tileState.attendeeId)
                }
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileRemoved(tileId: Int32(tileState.tileId))
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
                let renderView: DefaultVideoRenderView
                if let existing = self.remoteTiles[tileState.tileId] {
                    renderView = existing
                } else {
                    renderView = DefaultVideoRenderView()
                    renderView.mirror = false
                }
                self.remoteTiles[tileState.tileId] = renderView
                self.attendeeTileMap[tileState.attendeeId] = tileState.tileId
                self.audioVideo?.bindVideoView(videoView: renderView, tileId: tileState.tileId)
                ChimeSdkBridge.shared.eventDelegate?.onRemoteTileAdded(tileId: Int32(tileState.tileId))
            }
        }
    }

    func videoTileSizeDidChange(tileState: VideoTileState) {}
}
