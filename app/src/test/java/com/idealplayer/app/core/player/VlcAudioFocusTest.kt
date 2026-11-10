package com.idealplayer.app.core.player

import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VlcAudioFocusTest {

    @Test
    fun `temporary loss pauses while retaining focus`() {
        assertThat(vlcAudioFocusAction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, pausedByFocus = false))
            .isEqualTo(VlcAudioFocusAction.PAUSE_RETAINING_FOCUS)
    }

    @Test
    fun `permanent loss pauses and abandons focus`() {
        assertThat(vlcAudioFocusAction(AudioManager.AUDIOFOCUS_LOSS, pausedByFocus = false))
            .isEqualTo(VlcAudioFocusAction.PAUSE_AND_ABANDON)
    }

    @Test
    fun `gain resumes only when focus caused the pause`() {
        assertThat(vlcAudioFocusAction(AudioManager.AUDIOFOCUS_GAIN, pausedByFocus = true))
            .isEqualTo(VlcAudioFocusAction.RESTORE_AND_RESUME)
        assertThat(vlcAudioFocusAction(AudioManager.AUDIOFOCUS_GAIN, pausedByFocus = false))
            .isEqualTo(VlcAudioFocusAction.RESTORE)
    }
}
