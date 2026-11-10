package com.idealplayer.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.database.ChannelEntity
import com.idealplayer.app.core.database.EpisodeEntity
import com.idealplayer.app.core.database.MovieEntity
import com.idealplayer.app.core.database.SeriesEntity
import org.junit.Test

/**
 * Verifies that a playlist refresh (delete + re-insert) preserves the primary key and
 * per-row user state for content matched by a stable natural key. This is the regression
 * guard for K-1: without it, favorites and continue-watching break on every refresh.
 */
class PlaylistContentMergeTest {

    // ─── Stable keys ────────────────────────────────────────────────────────────

    @Test
    fun `channel stable key prefers streamId then url then name`() {
        assertThat(channel(streamId = 7, streamUrl = "http://a/x").stableContentKey())
            .isEqualTo("stream:7")
        assertThat(channel(streamId = 0, streamUrl = "http://a/x").stableContentKey())
            .isEqualTo("url:http://a/x")
        assertThat(channel(streamId = 0, streamUrl = "", name = "BBC", group = "UK").stableContentKey())
            .isEqualTo("name:bbc:uk")
    }

    @Test
    fun `channel url key ignores playback header suffix`() {
        // parsePlaybackSource strips pipe-suffixed headers, so the same stream with and
        // without a header resolves to the same key.
        val withHeader = channel(streamId = 0, streamUrl = "http://a/x|User-Agent=Foo")
        val without = channel(streamId = 0, streamUrl = "http://a/x")
        assertThat(withHeader.stableContentKey()).isEqualTo(without.stableContentKey())
    }

    // ─── Channels ───────────────────────────────────────────────────────────────

    @Test
    fun `refreshed channel keeps previous id favorite and online state`() {
        val previous = channel(id = 42, streamId = 7, isFavorite = true, lastWatched = 999L)
            .copy(isOnline = false)
        val incoming = channel(id = 0, streamId = 7, isFavorite = false, lastWatched = 0L)

        val merged = listOf(incoming).withPreservedChannelState(
            mapOf(previous.stableContentKey() to previous)
        ).single()

        assertThat(merged.id).isEqualTo(42)
        assertThat(merged.isFavorite).isTrue()
        assertThat(merged.lastWatched).isEqualTo(999L)
        assertThat(merged.isOnline).isFalse()
        // Non-user fields come from the incoming (fresh) row.
        assertThat(merged.streamUrl).isEqualTo(incoming.streamUrl)
    }

    @Test
    fun `new channel without a match keeps fresh id`() {
        val incoming = channel(id = 0, streamId = 9)
        val merged = listOf(incoming).withPreservedChannelState(emptyMap()).single()
        assertThat(merged.id).isEqualTo(0L)
    }

    @Test
    fun `duplicate incoming natural keys never reuse one persisted id twice`() {
        val previous = channel(id = 42, streamId = 7)
        val incoming = listOf(
            channel(streamId = 7, name = "First duplicate"),
            channel(streamId = 7, name = "Second duplicate")
        )

        val merged = incoming.withPreservedChannelState(
            mapOf(previous.stableContentKey() to previous)
        )

        assertThat(merged.map { it.id }).containsExactly(42L, 0L).inOrder()
    }

    @Test
    fun `channel refresh preserves a previously repaired logo`() {
        val previous = channel(id = 42, streamId = 7).copy(
            logoUrl = "https://images.example/station.png"
        )
        val incoming = channel(streamId = 7).copy(logoUrl = "")

        val merged = listOf(incoming).withPreservedChannelState(
            mapOf(previous.stableContentKey() to previous)
        ).single()

        assertThat(merged.logoUrl).isEqualTo(previous.logoUrl)
    }

    @Test
    fun `channel quality aliases inherit one valid station logo`() {
        val channels = listOf(
            channel(streamId = 1, name = "TR • Tele 1 FHD").copy(
                logoUrl = "https://images.example/tele1.png",
                epgChannelId = "tele1.tr"
            ),
            channel(streamId = 2, name = "TR : Tele 1 HD").copy(epgChannelId = "tele1.tr"),
            channel(streamId = 3, name = "TR • Tele 1 TV")
        )

        val resolved = channels.withInheritedChannelArtwork()

        assertThat(resolved.map(ChannelEntity::logoUrl))
            .containsExactly(
                "https://images.example/tele1.png",
                "https://images.example/tele1.png",
                "https://images.example/tele1.png"
            )
            .inOrder()
    }

    @Test
    fun `different stations with a shared word do not inherit logo`() {
        val channels = listOf(
            channel(streamId = 1, name = "TR • Star TV FHD").copy(
                logoUrl = "https://images.example/star-tv.png"
            ),
            channel(streamId = 2, name = "TR • Star Turk")
        )

        val resolved = channels.withInheritedChannelArtwork()

        assertThat(resolved[1].logoUrl).isEmpty()
    }

    // ─── Movies ─────────────────────────────────────────────────────────────────

    @Test
    fun `refreshed movie keeps id favorite progress and original addedAt`() {
        val previous = movie(id = 5, streamId = 3, isFavorite = true)
            .copy(lastPosition = 1234L, totalDuration = 5000L, lastWatched = 88L, addedAt = 100L)
        val incoming = movie(id = 0, streamId = 3)

        val merged = listOf(incoming).withPreservedMovieState(
            mapOf(previous.stableContentKey() to previous), syncTime = 777L
        ).single()

        assertThat(merged.id).isEqualTo(5)
        assertThat(merged.isFavorite).isTrue()
        assertThat(merged.lastPosition).isEqualTo(1234L)
        assertThat(merged.totalDuration).isEqualTo(5000L)
        assertThat(merged.lastWatched).isEqualTo(88L)
        assertThat(merged.addedAt).isEqualTo(100L)
    }

    @Test
    fun `new movie gets syncTime as addedAt`() {
        val merged = listOf(movie(id = 0, streamId = 3))
            .withPreservedMovieState(emptyMap(), syncTime = 777L).single()
        assertThat(merged.id).isEqualTo(0L)
        assertThat(merged.addedAt).isEqualTo(777L)
    }

    @Test
    fun `movie refresh keeps enriched metadata when catalog row is incomplete`() {
        val previous = movie(id = 5, streamId = 3).copy(
            posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/w780/backdrop.jpg",
            plot = "Enriched plot",
            genre = "Drama",
            year = 2024,
            tmdbId = 99
        )
        val incoming = movie(streamId = 3).copy(posterUrl = "null")

        val merged = listOf(incoming).withPreservedMovieState(
            mapOf(previous.stableContentKey() to previous),
            syncTime = 777L
        ).single()

        assertThat(merged.posterUrl).isEqualTo(previous.posterUrl)
        assertThat(merged.backdropUrl).isEqualTo(previous.backdropUrl)
        assertThat(merged.plot).isEqualTo("Enriched plot")
        assertThat(merged.genre).isEqualTo("Drama")
        assertThat(merged.year).isEqualTo(2024)
        assertThat(merged.tmdbId).isEqualTo(99)
    }

    @Test
    fun `movie refresh prefers fresh valid provider artwork`() {
        val previous = movie(id = 5, streamId = 3).copy(
            posterUrl = "https://image.tmdb.org/old.jpg"
        )
        val incoming = movie(streamId = 3).copy(
            posterUrl = "https://provider.example/new.jpg"
        )

        val merged = listOf(incoming).withPreservedMovieState(
            mapOf(previous.stableContentKey() to previous),
            syncTime = 777L
        ).single()

        assertThat(merged.posterUrl).isEqualTo(incoming.posterUrl)
    }

    @Test
    fun `duplicate movie editions share missing poster and backdrop`() {
        val artwork = movie(streamId = 1, streamUrl = "http://host/movie/1").copy(
            name = "Synthetic Film (2024)",
            year = 2024,
            posterUrl = "https://images.example/poster.jpg",
            backdropUrl = "https://images.example/backdrop.jpg"
        )
        val missing = movie(streamId = 2, streamUrl = "http://host/movie/2").copy(
            name = "Synthetic Film [2024]",
            year = 2024
        )

        val resolved = listOf(artwork, missing).withInheritedMovieArtwork()

        assertThat(resolved[1].posterUrl).isEqualTo(artwork.posterUrl)
        assertThat(resolved[1].backdropUrl).isEqualTo(artwork.backdropUrl)
    }

    @Test
    fun `same movie title from a different year does not inherit artwork`() {
        val oldMovie = movie(streamId = 1, streamUrl = "http://host/movie/1").copy(
            name = "Synthetic Film (1999)",
            year = 1999,
            posterUrl = "https://images.example/old.jpg"
        )
        val remake = movie(streamId = 2, streamUrl = "http://host/movie/2").copy(
            name = "Synthetic Film (2025)",
            year = 2025
        )

        val resolved = listOf(oldMovie, remake).withInheritedMovieArtwork()

        assertThat(resolved[1].posterUrl).isEmpty()
    }

    @Test
    fun `catalog metadata queue only skips complete movie rows`() {
        val complete = movie().copy(
            posterUrl = "https://image.tmdb.org/poster.jpg",
            backdropUrl = "https://image.tmdb.org/backdrop.jpg",
            plot = "Plot",
            genre = "Drama",
            year = 2024,
            tmdbId = 42
        )

        assertThat(complete.needsCatalogMetadata()).isFalse()
        assertThat(complete.copy(plot = "").needsCatalogMetadata()).isTrue()
        assertThat(complete.copy(posterUrl = "null").needsCatalogMetadata()).isTrue()
    }

    @Test
    fun `catalog cursor wraps and keeps advancing`() {
        assertThat(nextCircularOffset(currentOffset = 90, processed = 36, total = 100))
            .isEqualTo(26)
        assertThat(nextCircularOffset(currentOffset = 10, processed = 0, total = 100))
            .isEqualTo(0)
    }

    // ─── Series ─────────────────────────────────────────────────────────────────

    @Test
    fun `refreshed series keeps id favorite and last watched episode`() {
        val previous = series(id = 11, seriesId = 2)
            .copy(isFavorite = true, lastWatchedEpisodeId = 314L, addedAt = 50L)
        val incoming = series(id = 0, seriesId = 2)

        val merged = listOf(incoming).withPreservedSeriesState(
            mapOf(previous.stableContentKey() to previous), syncTime = 600L
        ).single()

        assertThat(merged.id).isEqualTo(11)
        assertThat(merged.isFavorite).isTrue()
        assertThat(merged.lastWatchedEpisodeId).isEqualTo(314L)
        assertThat(merged.addedAt).isEqualTo(50L)
    }

    @Test
    fun `series refresh keeps enriched metadata missing from catalog response`() {
        val previous = series(id = 11, seriesId = 2).copy(
            backdropUrl = "https://image.tmdb.org/backdrop.jpg",
            plot = "Series plot",
            year = 2023,
            tmdbId = 88,
            seasonCount = 3,
            episodeCount = 24
        )
        val incoming = series(seriesId = 2)

        val merged = listOf(incoming).withPreservedSeriesState(
            mapOf(previous.stableContentKey() to previous),
            syncTime = 600L
        ).single()

        assertThat(merged.backdropUrl).isEqualTo(previous.backdropUrl)
        assertThat(merged.plot).isEqualTo("Series plot")
        assertThat(merged.year).isEqualTo(2023)
        assertThat(merged.tmdbId).isEqualTo(88)
        assertThat(merged.seasonCount).isEqualTo(3)
        assertThat(merged.episodeCount).isEqualTo(24)
    }

    @Test
    fun `duplicate series entry inherits available artwork`() {
        val artwork = series(seriesId = 1).copy(
            name = "Synthetic Series (2025)",
            year = 2025,
            posterUrl = "https://images.example/series.jpg"
        )
        val missing = series(seriesId = 2).copy(
            name = "Synthetic Series [2025]",
            year = 2025
        )

        val resolved = listOf(artwork, missing).withInheritedSeriesArtwork()

        assertThat(resolved[1].posterUrl).isEqualTo(artwork.posterUrl)
    }

    // ─── Episodes ─────────────────────────────────────────────────────────────────

    @Test
    fun `refreshed episode keeps id and progress by stream url`() {
        val previous = episode(id = 20, streamUrl = "http://a/ep1")
            .copy(lastPosition = 42L, totalDuration = 100L, lastWatched = 7L, isFavorite = true)
        val incoming = episode(id = 0, streamUrl = "http://a/ep1")

        val merged = listOf(incoming).withPreservedEpisodeState(
            mapOf(previous.stableContentKey() to previous)
        ).single()

        assertThat(merged.id).isEqualTo(20)
        assertThat(merged.lastPosition).isEqualTo(42L)
        assertThat(merged.totalDuration).isEqualTo(100L)
        assertThat(merged.lastWatched).isEqualTo(7L)
        assertThat(merged.isFavorite).isTrue()
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────

    private fun channel(
        id: Long = 0,
        streamId: Int = 0,
        streamUrl: String = "http://host/live/1",
        name: String = "Channel",
        group: String = "Group",
        isFavorite: Boolean = false,
        lastWatched: Long = 0L
    ) = ChannelEntity(
        id = id,
        playlistId = 1,
        streamId = streamId,
        name = name,
        groupTitle = group,
        streamUrl = streamUrl,
        isFavorite = isFavorite,
        lastWatched = lastWatched
    )

    private fun movie(
        id: Long = 0,
        streamId: Int = 0,
        streamUrl: String = "http://host/movie/1",
        name: String = "Movie",
        isFavorite: Boolean = false
    ) = MovieEntity(
        id = id,
        playlistId = 1,
        streamId = streamId,
        name = name,
        streamUrl = streamUrl,
        isFavorite = isFavorite
    )

    private fun series(
        id: Long = 0,
        seriesId: Int = 0,
        name: String = "Series"
    ) = SeriesEntity(
        id = id,
        playlistId = 1,
        seriesId = seriesId,
        name = name
    )

    private fun episode(
        id: Long = 0,
        streamUrl: String = "http://host/series/1"
    ) = EpisodeEntity(
        id = id,
        seriesId = 1,
        seasonNumber = 1,
        episodeNumber = 1,
        name = "Episode",
        streamUrl = streamUrl
    )
}
