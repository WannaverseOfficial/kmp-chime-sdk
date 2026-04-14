package com.wannaverse.chimesdk.composables

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.amazonaws.services.chime.sdk.meetings.audiovideo.video.DefaultVideoRenderView
import com.wannaverse.chimesdk.CameraFacing
import com.wannaverse.chimesdk.VideoTileManager
import com.wannaverse.chimesdk.meetingSession

@Composable
fun VideoTileView(
    tileId: Int?,
    modifier: Modifier,
    cameraFacing: CameraFacing? = null,
    isOnTop: Boolean
) {
    val context = LocalContext.current
    if (tileId == null) return

    val mirror = cameraFacing == CameraFacing.FRONT

    DisposableEffect(tileId) {
        onDispose {
            try {
                meetingSession?.audioVideo?.unbindVideoView(tileId)
                VideoTileManager.clearBoundView(tileId)
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
                meetingSession?.audioVideo?.bindVideoView(this, tileId)
                VideoTileManager.updateBoundView(tileId, this)
            }
        },
        update = { view ->
            view.mirror = mirror
            view.setZOrderMediaOverlay(isOnTop)
            if (!VideoTileManager.isAlreadyBound(tileId, view)) {
                meetingSession?.audioVideo?.bindVideoView(view, tileId)
                VideoTileManager.updateBoundView(tileId, view)
            }
        },
        modifier = modifier
    )
}
