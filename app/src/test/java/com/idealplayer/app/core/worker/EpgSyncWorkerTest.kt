package com.idealplayer.app.core.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpgSyncWorkerTest {

    @Test
    fun `work names are stable opaque and collision resistant`() {
        // Aa and BB are a classic Java String.hashCode collision. With the same prefix the
        // complete legacy URLs also collide, which previously made one EPG schedule replace the
        // other.
        val first = "https://epg.example.test/Aa?username=user&password=secret"
        val second = "https://epg.example.test/BB?username=user&password=secret"

        assertThat(first.hashCode()).isEqualTo(second.hashCode())
        assertThat(EpgSyncWorker.workNameFor(first))
            .isEqualTo(EpgSyncWorker.workNameFor("  $first  "))
        assertThat(EpgSyncWorker.workNameFor(first))
            .isNotEqualTo(EpgSyncWorker.workNameFor(second))
        assertThat(EpgSyncWorker.workNameFor(first)).doesNotContain("secret")
        assertThat(EpgSyncWorker.workNameFor(first).removePrefix(EpgSyncWorker.WORK_NAME_PREFIX))
            .hasLength(64)
    }

    @Test
    fun `scheduled sync only runs for the current active configured source`() {
        assertThat(
            shouldRunScheduledEpgSync(
                requestedUrl = "https://epg.example.test/active.xml",
                configuredUrl = "https://epg.example.test/active.xml",
                autoSyncEnabled = true,
                activePlaylistId = 42L,
                ownerPlaylistId = 42L
            )
        ).isTrue()
        assertThat(
            shouldRunScheduledEpgSync(
                requestedUrl = "https://epg.example.test/old.xml",
                configuredUrl = "https://epg.example.test/active.xml",
                autoSyncEnabled = true,
                activePlaylistId = 42L,
                ownerPlaylistId = 42L
            )
        ).isFalse()
        assertThat(
            shouldRunScheduledEpgSync(
                requestedUrl = "https://epg.example.test/active.xml",
                configuredUrl = "https://epg.example.test/active.xml",
                autoSyncEnabled = false,
                activePlaylistId = 42L,
                ownerPlaylistId = 42L
            )
        ).isFalse()
        assertThat(
            shouldRunScheduledEpgSync(
                requestedUrl = "https://epg.example.test/active.xml",
                configuredUrl = "https://epg.example.test/active.xml",
                autoSyncEnabled = true,
                activePlaylistId = 42L,
                ownerPlaylistId = 7L
            )
        ).isFalse()
    }

    @Test
    fun `legacy ownerless periodic work is discarded but explicit manual refresh is retained`() {
        assertThat(
            isLegacyOwnerlessScheduledEpgWork(
                isManualRun = false,
                requiresActiveSource = false,
                ownerPlaylistId = EpgSyncWorker.NO_PLAYLIST_ID
            )
        ).isTrue()
        assertThat(
            isLegacyOwnerlessScheduledEpgWork(
                isManualRun = true,
                requiresActiveSource = false,
                ownerPlaylistId = EpgSyncWorker.NO_PLAYLIST_ID
            )
        ).isFalse()
    }
}
