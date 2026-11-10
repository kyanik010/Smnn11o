package com.idealplayer.app.core.player

import android.content.Context
import android.os.Build
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import timber.log.Timber

@OptIn(UnstableApi::class)
class IdealPlayerRenderersFactory(
    context: Context,
    preferHardwareVideoDecoding: Boolean
) : DefaultRenderersFactory(context) {

    private val preferSoftwareVideoDecoding =
        shouldPreferSoftwareVideoDecoding(preferHardwareVideoDecoding)

    init {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF)
        setMediaCodecSelector(
            PreferredDecoderSelector(
                preferSoftwareVideoDecoding = preferSoftwareVideoDecoding
            )
        )
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )

        try {
            out += FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
            Timber.d("Media3 FFmpeg audio renderer appended for codec fallback")
        } catch (throwable: Throwable) {
            Timber.w(throwable, "FFmpeg audio renderer unavailable, continuing with platform decoders only")
        }
    }

    private fun shouldPreferSoftwareVideoDecoding(preferHardwareVideoDecoding: Boolean): Boolean {
        if (!preferHardwareVideoDecoding) {
            return true
        }

        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()

        val isEmulator = fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("sdk_gphone") ||
            model.contains("android sdk built for x86") ||
            model.contains("emulator") ||
            manufacturer.contains("genymotion") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            product.contains("sdk") ||
            product.contains("emulator")

        if (isEmulator) {
            Timber.w(
                "Emulator environment detected; preferring software video decoders to avoid green output"
            )
        }

        return isEmulator
    }
}

@OptIn(UnstableApi::class)
private class PreferredDecoderSelector(
    private val preferSoftwareVideoDecoding: Boolean
) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val decoderInfos = MediaCodecUtil.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )
        if (!mimeType.startsWith("video/") || decoderInfos.size < 2) {
            return decoderInfos
        }

        val ordered = if (preferSoftwareVideoDecoding) {
            decoderInfos.sortedWith(
                compareByDescending<MediaCodecInfo> { it.softwareOnly }
                    .thenBy { isKnownProblematicVideoDecoder(it) }
                    .thenByDescending { it.hardwareAccelerated }
                    .thenBy { it.name }
            )
        } else {
            decoderInfos.sortedWith(
                compareBy<MediaCodecInfo> { isKnownProblematicVideoDecoder(it) }
                    .thenByDescending { it.hardwareAccelerated && !it.softwareOnly }
                    .thenBy { it.softwareOnly }
                    .thenBy { it.name }
            )
        }

        Timber.d(
            "Video decoder order for %s: %s",
            mimeType,
            ordered.joinToString { it.name }
        )
        return ordered
    }

    private fun isKnownProblematicVideoDecoder(decoderInfo: MediaCodecInfo): Boolean {
        val name = decoderInfo.name.lowercase()
        return name.contains("goldfish") || name.contains("c2.goldfish")
    }
}
