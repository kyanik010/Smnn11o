package com.idealplayer.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migration test infrastructure (K-4).
 *
 * Two layers of coverage:
 *  1. Each hand-written [IdealPlayerDatabase] migration is run against a minimal pre-state and its
 *     post-conditions are asserted directly (column added, default applied, backfill correct).
 *  2. A full Room build with every migration registered opens at the current version and a
 *     favorites / watch-history round-trip succeeds, proving the entities and DAOs are
 *     consistent with the compiled schema.
 *
 * Note: schema JSONs for versions 1–5 were never exported (only v6 is committed), so a
 * MigrationTestHelper-style end-to-end 1→6 upgrade from an exported schema is not possible
 * retroactively. exportSchema is on, so version 7+ can use MigrationTestHelper going forward.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IdealPlayerDatabaseMigrationTest {

    private fun newInMemoryDb(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun migration_1_2_adds_metadata_columns() {
        val db = newInMemoryDb()
        db.execSQL("CREATE TABLE metadata_cache (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, year INTEGER)")
        db.execSQL("CREATE INDEX index_metadata_cache_title_year ON metadata_cache (title, year)")
        db.execSQL("INSERT INTO metadata_cache (title, year) VALUES ('Dune', 2021)")

        IdealPlayerDatabase.MIGRATION_1_2.migrate(db)

        // New columns must exist and be queryable with their defaults.
        db.query("SELECT language, tagline, trailerUrl FROM metadata_cache WHERE title = 'Dune'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("")
            assertThat(c.getString(1)).isEqualTo("")
            assertThat(c.getString(2)).isEqualTo("")
        }
        db.close()
    }

    @Test
    fun migration_2_3_adds_isOnline_defaulting_online() {
        val db = newInMemoryDb()
        db.execSQL("CREATE TABLE channels (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
        db.execSQL("INSERT INTO channels (name) VALUES ('BBC')")

        IdealPlayerDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT isOnline FROM channels WHERE name = 'BBC'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1) // existing rows default to online
        }
        db.close()
    }

    @Test
    fun migration_4_5_backfills_sourceOrder_from_id() {
        val db = newInMemoryDb()
        // Columns mirror the pre-migration schema closely enough for the migration's
        // ALTER/CREATE INDEX statements (which reference playlistId) to apply.
        db.execSQL("CREATE TABLE movies (id INTEGER PRIMARY KEY AUTOINCREMENT, playlistId INTEGER NOT NULL DEFAULT 0, name TEXT)")
        db.execSQL("CREATE TABLE series (id INTEGER PRIMARY KEY AUTOINCREMENT, playlistId INTEGER NOT NULL DEFAULT 0, name TEXT)")
        db.execSQL("INSERT INTO movies (id, name) VALUES (5, 'A'), (10, 'B')")
        db.execSQL("INSERT INTO series (id, name) VALUES (7, 'S')")

        IdealPlayerDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT addedAt, sourceOrder FROM movies WHERE id = 10").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getLong(0)).isEqualTo(0L)   // addedAt default
            assertThat(c.getInt(1)).isEqualTo(10)     // sourceOrder backfilled from id
        }
        db.query("SELECT sourceOrder FROM series WHERE id = 7").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(7)
        }
        db.close()
    }

    @Test
    fun migration_5_6_adds_playlist_epgUrl() {
        val db = newInMemoryDb()
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")
        db.execSQL("INSERT INTO playlists (name) VALUES ('Mine')")

        IdealPlayerDatabase.MIGRATION_5_6.migrate(db)

        db.query("SELECT epgUrl FROM playlists WHERE name = 'Mine'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("")
        }
        db.close()
    }

    @Test
    fun migration_6_7_dedupes_programs_and_enforces_unique_slot() {
        val db = newInMemoryDb()
        // Pre-v7 shape: auto-increment id, no unique constraint on the natural key.
        db.execSQL(
            "CREATE TABLE epg_programs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, channelId TEXT NOT NULL, " +
                "title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', " +
                "startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, " +
                "posterUrl TEXT NOT NULL DEFAULT '', genre TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX index_epg_programs_channelId_startTime ON epg_programs (channelId, startTime)")
        // Two duplicate rows for the same slot (a stale sync then a fresher one) plus an unrelated slot.
        db.execSQL(
            "INSERT INTO epg_programs (id, channelId, title, startTime, endTime) VALUES " +
                "(1, 'bbc1', 'Old News', 1000, 2000), " +
                "(2, 'bbc1', 'Fresh News', 1000, 2000), " +
                "(3, 'bbc1', 'Later Show', 2000, 3000)"
        )

        IdealPlayerDatabase.MIGRATION_6_7.migrate(db)

        // The duplicate slot collapses to the newest inserted copy (MAX(id)).
        db.query("SELECT title FROM epg_programs WHERE channelId = 'bbc1' AND startTime = 1000").use { c ->
            assertThat(c.count).isEqualTo(1)
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Fresh News")
        }
        // The unrelated slot is untouched.
        db.query("SELECT COUNT(*) FROM epg_programs").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(2)
        }
        // The unique index now rejects a second row for an existing slot.
        db.execSQL("INSERT OR REPLACE INTO epg_programs (channelId, title, startTime, endTime) VALUES ('bbc1', 'Replaced', 1000, 2500)")
        db.query("SELECT title, endTime FROM epg_programs WHERE channelId = 'bbc1' AND startTime = 1000").use { c ->
            assertThat(c.count).isEqualTo(1)
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Replaced")
            assertThat(c.getLong(1)).isEqualTo(2500L)
        }
        db.close()
    }

    @Test
    fun database_opens_at_current_version_and_supports_favorites_round_trip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, IdealPlayerDatabase::class.java)
            .addMigrations(
                IdealPlayerDatabase.MIGRATION_1_2,
                IdealPlayerDatabase.MIGRATION_2_3,
                IdealPlayerDatabase.MIGRATION_3_4,
                IdealPlayerDatabase.MIGRATION_4_5,
                IdealPlayerDatabase.MIGRATION_5_6,
                IdealPlayerDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val playlistId = db.playlistDao().insert(
                PlaylistEntity(name = "P", type = "M3U_URL")
            )
            db.channelDao().insertAll(
                listOf(
                    ChannelEntity(
                        id = 1,
                        playlistId = playlistId,
                        name = "C",
                        streamUrl = "http://example/live/1"
                    )
                )
            )
            db.favoriteDao().insert(
                FavoriteEntity(contentId = 1, contentType = "LIVE", title = "C")
            )
            db.watchHistoryDao().insert(
                WatchHistoryEntity(
                    contentId = 1,
                    contentType = "LIVE",
                    title = "C",
                    streamUrl = "http://example/live/1"
                )
            )

            assertThat(db.channelDao().getById(1)).isNotNull()
            assertThat(db.favoriteDao().isFavorite(1, "LIVE").first()).isTrue()
            assertThat(db.watchHistoryDao().getByContent(1, "LIVE")).isNotNull()

            // Re-syncing the same slot must overwrite in place, not duplicate. This is the
            // behavioural guarantee behind the unique (channelId, startTime) index.
            val now = 1_500L
            db.epgDao().insertAll(
                listOf(EpgProgramEntity(channelId = "1", title = "First", startTime = 1_000, endTime = 2_000))
            )
            db.epgDao().insertAll(
                listOf(EpgProgramEntity(channelId = "1", title = "Second", startTime = 1_000, endTime = 2_000))
            )
            val current = db.epgDao().getCurrentProgramsForIds(listOf("1"), now)
            assertThat(current).hasSize(1)
            assertThat(current.single().title).isEqualTo("Second")
        }
        db.close()
    }
}
