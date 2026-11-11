package com.idealplayer.app.core.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated secondary player exclusively for external audio streams.
 * Completely isolated from the main video player lifecycle.
 */
class ExternalAudioPlayer(
    private val context: Context,
    private val onBuffering: () -> Unit,
    private val onPlaying: () -> Unit,
    private val onError: (errorName: String) -> Unit,
    private val onEnded: () -> Unit
) {
    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generationId = AtomicLong(0)
    private var currentVolume = 1.0f

    val isPlaying: Boolean
        get() = exoPlayer?.isPlaying == true

    val currentPosition: Long
        get() = exoPlayer?.currentPosition ?: 0L

    val duration: Long
        get() = exoPlayer?.duration ?: 0L

    fun play(url: String, mimeType: String? = null, requestHeaders: Map<String, String> = emptyMap()) {
        val gen = generationId.incrementAndGet()
        mainHandler.post {
            if (gen != generationId.get()) return@post
            releasePlayer()

            try {
                val audioOnlySelector = DefaultTrackSelector(context).apply {
                    setParameters(buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true))
                }
                val userAgent = requestHeaders.entries
                    .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                    ?.value
                    ?: AudioStreamInspector.USER_AGENT
                val additionalHeaders = requestHeaders.filterKeys {
                    !it.equals("User-Agent", ignoreCase = true)
                }
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setDefaultRequestProperties(additionalHeaders)
                val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
                val player = ExoPlayer.Builder(context)
                    .setTrackSelector(audioOnlySelector)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build().apply {
                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
                    setAudioAttributes(audioAttrs, false) // Do not handle focus stealing automatically
                    volume = currentVolume
                }
                exoPlayer = player

                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (gen != generationId.get() || exoPlayer !== player) return
                        when (state) {
                            Player.STATE_BUFFERING -> onBuffering()
                            Player.STATE_READY -> onPlaying()
                            Player.STATE_ENDED -> onEnded()
                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (gen != generationId.get() || exoPlayer !== player) return
                        if (isPlaying) {
                            onPlaying()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (gen != generationId.get() || exoPlayer !== player) return
                        Timber.w("ExternalAudioPlayer error: ${error.errorCodeName}")
                        onError(error.errorCodeName)
                    }
                })

                val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
                when (mimeType) {
                    "aac" -> mediaItemBuilder.setMimeType(MimeTypes.AUDIO_AAC)
                    "mp3" -> mediaItemBuilder.setMimeType(MimeTypes.AUDIO_MPEG)
                }

                player.setMediaItem(mediaItemBuilder.build())
                player.prepare()
                player.play()
            } catch (e: Exception) {
                Timber.e(e, "Could not initialize ExternalAudioPlayer")
                onError(e.message ?: "INIT_FAILED")
            }
        }
    }

    fun pause() {
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    fun resume() {
        mainHandler.post {
            exoPlayer?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        mainHandler.post {
            exoPlayer?.setPlaybackSpeed(speed)
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        mainHandler.post {
            exoPlayer?.volume = currentVolume
        }
    }

    fun stop() {
        generationId.incrementAndGet()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releasePlayer()
        } else {
            mainHandler.post { releasePlayer() }
        }
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {
                // The player may already be in an error or released state.
            } finally {
                try { player.release() } catch (_: Exception) {}
            }
        }
        exoPlayer = null
    }
}
