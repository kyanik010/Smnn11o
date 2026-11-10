package com.idealplayer.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.database.ChannelEntity
import com.idealplayer.app.core.database.FavoriteEntity
import com.idealplayer.app.core.database.IdealPlayerDatabase
import com.idealplayer.app.core.database.PlaylistEntity
import com.idealplayer.app.core.database.WatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room-level regression guard for K-1. The production repository uses the same snapshot,
 * delete, merge, and reinsert sequence; this test ensures the preserved primary key keeps the
 * separate favorites/watch-history references valid after Room writes it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaylistRefreshPersistenceTest {

    @Test
    fun `refresh retains favorite and watch history references for a stable channel`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IdealPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val playlistId = db.playlistDao().insert(PlaylistEntity(name = "P", type = "M3U_URL"))
            db.channelDao().insertAll(
                listOf(
                    ChannelEntity(
                        playlistId = playlistId,
                        streamId = 7,
                        name = "Original",
                        streamUrl = "https://stream.example.test/live/7",
                        isFavorite = true,
                        lastWatched = 123L
                    )
                )
            )
            val previous = db.channelDao().getByPlaylistSnapshot(playlistId).single()
            db.favoriteDao().insert(
                FavoriteEntity(contentId = previous.id, contentType = "LIVE", title = previous.name)
            )
            db.watchHistoryDao().insert(
                WatchHistoryEntity(
                    contentId = previous.id,
                    contentType = "LIVE",
                    title = previous.name,
                    streamUrl = previous.streamUrl
                )
            )

            db.withTransaction {
                val existing = db.channelDao().getByPlaylistSnapshot(playlistId)
                    .associateBy { it.stableContentKey() }
                db.channelDao().deleteByPlaylist(playlistId)
                db.channelDao().insertAll(
                    listOf(
                        ChannelEntity(
                            playlistId = playlistId,
                            streamId = 7,
                            name = "Renamed upstream",
                            streamUrl = "https://stream.example.test/live/7?rotated=true"
                        )
                    ).withPreservedChannelState(existing)
                )
            }

            val refreshed = db.channelDao().getByPlaylistSnapshot(playlistId).single()
            assertThat(refreshed.id).isEqualTo(previous.id)
            assertThat(refreshed.isFavorite).isTrue()
            assertThat(refreshed.lastWatched).isEqualTo(123L)
            assertThat(db.favoriteDao().isFavorite(refreshed.id, "LIVE").first()).isTrue()
            assertThat(db.watchHistoryDao().getByContent(refreshed.id, "LIVE")).isNotNull()
        } finally {
            db.close()
        }
    }
}
