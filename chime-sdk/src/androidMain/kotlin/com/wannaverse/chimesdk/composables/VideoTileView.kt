package com.wannaverse.chimesdk.composables

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.DefaultVideoRenderView
import com.amazonaws.services.chime.sdk.meetings.session.DefaultMeetingSession
import com.wannaverse.chimesdk.CameraFacing
import com.wannaverse.chimesdk.VideoTileObserverImpl

@Composable
fun VideoTileView(
    tileId: Int?,
    modifier: Modifier,
    cameraFacing: CameraFacing? = null,
    isOnTop: Boolean,
    meetingSession: DefaultMeetingSession,
    videoTileObserverImpl: VideoTileObserverImpl
) {
    val context = LocalContext.current
    if (tileId == null) return

    val mirror = cameraFacing == CameraFacing.FRONT

    DisposableEffect(tileId) {
        onDispose {
            try {
                meetingSession.audioVideo.unbindVideoView(tileId)
                videoTileObserverImpl.clearBoundView(tileId)
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = {
            DefaultVideoRenderView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setZOrderMediaOverlay(isOnTop)
                this.mirror = mirror
                meetingSession.audioVideo.bindVideoView(this, tileId)
                videoTileObserverImpl.updateBoundView(tileId, this)
            }
        },
        update = { view ->
            view.mirror = mirror
            view.setZOrderMediaOverlay(isOnTop)
            if (!videoTileObserverImpl.isAlreadyBound(tileId, view)) {
                meetingSession.audioVideo.bindVideoView(view, tileId)
                videoTileObserverImpl.updateBoundView(tileId, view)
            }
        },
        modifier = modifier
    )
}
