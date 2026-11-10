package com.idealplayer.app.ui.player

import android.graphics.Rect
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.idealplayer.app.core.player.ExoPlayerEngine
import com.idealplayer.app.core.player.PlayerEngine
import com.idealplayer.app.core.player.PlayerState
import com.idealplayer.app.core.player.VlcPlayerEngine

enum class PlayerShellMode {
    MOBILE,
    TV
}

data class PlayerSurfaceHostState(
    val playerReady: Boolean,
    val playbackActive: Boolean,
    val shellMode: PlayerShellMode
)

@Composable
fun PlayerSurfaceHost(
    modifier: Modifier = Modifier,
    playerEngine: PlayerEngine?,
    playerState: PlayerState,
    surfaceState: PlayerSurfaceHostState,
    onSurfaceBoundsChanged: (Rect) -> Unit = {},
    placeholder: @Composable (() -> Unit)? = null
) {
    if (!surfaceState.playerReady || playerEngine == null) {
        placeholder?.invoke()
        return
    }

    key(surfaceState.shellMode, playerEngine.engineName) {
        BoxWithConstraints(
            modifier = modifier.onGloballyPositioned { coordinates ->
                onSurfaceBoundsChanged(coordinates.boundsInWindow().toAndroidRect())
            },
            contentAlignment = Alignment.Center
        ) {
            val viewportAspectRatio = if (maxHeight > 0.dp) {
                maxWidth.value / maxHeight.value
            } else {
                null
            }

            when (playerEngine) {
                is ExoPlayerEngine -> {
                    ExoPlayerRenderer(
                        playerEngine = playerEngine,
                        playerState = playerState,
                        surfaceState = surfaceState,
                        viewportAspectRatio = viewportAspectRatio
                    )
                }
                is VlcPlayerEngine -> {
                    VlcRenderer(
                        playerEngine = playerEngine,
                        playerState = playerState,
                        surfaceState = surfaceState,
                        viewportAspectRatio = viewportAspectRatio
                    )
                }
            }
        }
    }
}

private fun ComposeRect.toAndroidRect(): Rect = Rect(
    left.toInt(),
    top.toInt(),
    right.toInt(),
    bottom.toInt()
)

@Composable
private fun ExoPlayerRenderer(
    playerEngine: ExoPlayerEngine,
    playerState: PlayerState,
    surfaceState: PlayerSurfaceHostState,
    viewportAspectRatio: Float?
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                keepScreenOn = surfaceState.playbackActive
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (surfaceState.shellMode == PlayerShellMode.TV) {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
                playerEngine.attachPlayerView(this)
            }
        },
        update = { view ->
            view.keepScreenOn = surfaceState.playbackActive
            if (surfaceState.shellMode == PlayerShellMode.TV) {
                view.isFocusable = false
                view.isFocusableInTouchMode = false
            }
            playerEngine.attachPlayerView(view)
        },
        modifier = Modifier.playerViewport(
            targetAspectRatio = playerEngine.getViewportAspectRatio(playerState.aspectRatioMode),
            viewportAspectRatio = viewportAspectRatio
        )
    )

    DisposableEffect(playerEngine) {
        onDispose {
            playerEngine.clearPlayerView()
        }
    }
}

@Composable
private fun VlcRenderer(
    playerEngine: VlcPlayerEngine,
    playerState: PlayerState,
    surfaceState: PlayerSurfaceHostState,
    viewportAspectRatio: Float?
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                keepScreenOn = surfaceState.playbackActive
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (surfaceState.shellMode == PlayerShellMode.TV) {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
                playerEngine.attachSurface(this)
            }
        },
        update = { surfaceView ->
            surfaceView.keepScreenOn = surfaceState.playbackActive
            if (surfaceState.shellMode == PlayerShellMode.TV) {
                surfaceView.isFocusable = false
                surfaceView.isFocusableInTouchMode = false
            }
            playerEngine.attachSurface(surfaceView)
            playerEngine.updateSurfaceSize(surfaceView.width, surfaceView.height)
        },
        modifier = Modifier.playerViewport(
            targetAspectRatio = viewportAspectRatioFor(playerState),
            viewportAspectRatio = viewportAspectRatio
        )
    )

    DisposableEffect(playerEngine) {
        onDispose {
            playerEngine.detachSurface()
        }
    }
}

private fun viewportAspectRatioFor(playerState: PlayerState): Float? {
    val width = playerState.videoWidth
    val height = playerState.videoHeight
    if (width <= 0 || height <= 0) {
        return null
    }

    return when (playerState.aspectRatioMode) {
        com.idealplayer.app.core.player.AspectRatioMode.FORCE_16_9 -> 16f / 9f
        com.idealplayer.app.core.player.AspectRatioMode.FORCE_4_3 -> 4f / 3f
        else -> width.toFloat() / height.toFloat()
    }
}

internal fun Modifier.playerViewport(
    targetAspectRatio: Float?,
    viewportAspectRatio: Float?
): Modifier {
    if (
        targetAspectRatio == null ||
        viewportAspectRatio == null ||
        targetAspectRatio <= 0f ||
        viewportAspectRatio <= 0f
    ) {
        return fillMaxSize()
    }

    return if (viewportAspectRatio > targetAspectRatio) {
        fillMaxHeight()
            .aspectRatio(targetAspectRatio, matchHeightConstraintsFirst = true)
    } else {
        fillMaxWidth()
            .aspectRatio(targetAspectRatio)
    }
}
