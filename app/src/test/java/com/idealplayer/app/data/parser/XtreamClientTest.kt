package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.network.XtreamApi
import com.idealplayer.app.core.network.dto.XtreamAuthResponse
import com.idealplayer.app.core.network.dto.XtreamCategory
import com.idealplayer.app.core.network.dto.XtreamLiveStream
import com.idealplayer.app.core.network.dto.XtreamSeriesInfo
import com.idealplayer.app.core.network.dto.XtreamUserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import org.junit.Test

class XtreamClientTest {
    private val api = mockk<XtreamApi>()
    private val client = XtreamClient(api)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `content refresh fails instead of returning a destructive partial snapshot`() = runTest {
        mockActiveAuthentication()
        coEvery {
            api.getLiveCategories(any(), any(), any(), any())
        } returns listOf(XtreamCategory(categoryId = "1", categoryName = "News"))
        coEvery {
            api.getLiveStreams(any(), any(), any(), any(), any())
        } returns listOf(XtreamLiveStream(streamId = 1, name = "Synthetic live", categoryId = "1"))
        coEvery {
            api.getVodCategories(any(), any(), any(), any())
        } throws IOException("Synthetic VOD timeout")

        val error = runCatching {
            client.loadContent(
                serverUrl = "https://provider.example",
                username = "user",
                password = "pass",
                playlistId = 1L
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `live only provider keeps a successful live snapshot when optional catalogs are unsupported`() = runTest {
        mockActiveAuthentication()
        coEvery {
            api.getLiveCategories(any(), any(), any(), any())
        } returns listOf(XtreamCategory(categoryId = "1", categoryName = "News"))
        coEvery {
            api.getLiveStreams(any(), any(), any(), any(), any())
        } returns listOf(XtreamLiveStream(streamId = 1, name = "Synthetic live", categoryId = "1"))
        coEvery {
            api.getVodCategories(any(), any(), any(), any())
        } throws unsupportedCatalogHttpException()
        coEvery {
            api.getSeriesCategories(any(), any(), any(), any())
        } throws unsupportedCatalogHttpException()

        val result = client.loadContent(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            playlistId = 1L
        )

        assertThat(result.channels).hasSize(1)
        assertThat(result.loadedSections).containsExactly(XtreamContentSection.LIVE)
    }

    @Test
    fun `first content refresh authenticates the entered password before loading catalogs`() = runTest {
        coEvery {
            api.authenticate(any(), "user", "wrong-pass")
        } returns XtreamAuthResponse(
            userInfo = XtreamUserInfo(status = "Banned")
        )

        val error = runCatching {
            client.loadContent(
                serverUrl = "https://provider.example",
                username = "user",
                password = "wrong-pass",
                playlistId = 1L
            )
        }.exceptionOrNull()

        assertThat(error).hasMessageThat().contains("Account not active")
        coVerify(exactly = 0) {
            api.getLiveStreams(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `live stream uses a provider supported output format`() = runTest {
        mockActiveAuthentication(allowedOutputFormats = listOf("ts"))
        coEvery {
            api.getLiveCategories(any(), any(), any(), any())
        } returns listOf(XtreamCategory(categoryId = "1", categoryName = "News"))
        coEvery {
            api.getLiveStreams(any(), any(), any(), any(), any())
        } returns listOf(XtreamLiveStream(streamId = 1, name = "Synthetic live", categoryId = "1"))
        coEvery {
            api.getVodCategories(any(), any(), any(), any())
        } throws unsupportedCatalogHttpException()
        coEvery {
            api.getSeriesCategories(any(), any(), any(), any())
        } throws unsupportedCatalogHttpException()

        val result = client.loadContent(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            playlistId = 1L
        )

        assertThat(result.channels.single().streamUrl)
            .isEqualTo("https://provider.example/live/user/pass/1.ts")
    }

    @Test
    fun `series episodes accept mixed provider value types`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "Season 2": [
                    {
                      "id": 12345,
                      "episode_num": "3",
                      "title": "Synthetic Episode",
                      "container_extension": "mkv",
                      "info": {
                        "duration_secs": "1800",
                        "rating": "8.4"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes).hasSize(1)
        assertThat(episodes.single().seasonNumber).isEqualTo(2)
        assertThat(episodes.single().episodeNumber).isEqualTo(3)
        assertThat(episodes.single().duration).isEqualTo(1800)
        assertThat(episodes.single().rating).isWithin(0.001).of(8.4)
        assertThat(episodes.single().streamUrl)
            .isEqualTo("https://provider.example/series/user/pass/12345.mkv")
    }

    @Test
    fun `series episodes survive malformed optional info`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "1": [
                    {
                      "id": "67890",
                      "episode_num": 1,
                      "title": "Synthetic Episode",
                      "container_extension": "mp4",
                      "info": []
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes).hasSize(1)
        assertThat(episodes.single().name).isEqualTo("Synthetic Episode")
        assertThat(episodes.single().streamUrl)
            .isEqualTo("https://provider.example/series/user/pass/67890.mp4")
    }

    @Test
    fun `series episodes prefer provider direct source and avoid duplicate extension`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "1": [
                    {
                      "id": "12345.mkv",
                      "episode_num": 1,
                      "container_extension": "mkv"
                    },
                    {
                      "id": "67890",
                      "episode_num": 2,
                      "container_extension": "mp4",
                      "direct_source": "https://cdn.example/synthetic/episode.mp4",
                      "info": ""
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes.map { it.streamUrl }).containsExactly(
            "https://provider.example/series/user/pass/12345.mkv",
            "https://cdn.example/synthetic/episode.mp4"
        ).inOrder()
    }

    @Test
    fun `series episodes accept alternate stream id and extension keys`() = runTest {
        coEvery {
            api.getSeriesInfo(any(), any(), any(), any(), any())
        } returns XtreamSeriesInfo(
            episodes = json.parseToJsonElement(
                """
                {
                  "3": [
                    {
                      "stream_id": "24680",
                      "episode_num": 4,
                      "title": "Synthetic Episode",
                      "extension": "ts"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val episodes = client.loadSeriesEpisodes(
            serverUrl = "https://provider.example",
            username = "user",
            password = "pass",
            seriesId = 99,
            dbSeriesId = 7L
        )

        assertThat(episodes).hasSize(1)
        assertThat(episodes.single().streamUrl)
            .isEqualTo("https://provider.example/series/user/pass/24680.ts")
    }

    private fun mockActiveAuthentication(allowedOutputFormats: List<String> = listOf("m3u8", "ts")) {
        coEvery {
            api.authenticate(any(), "user", "pass")
        } returns XtreamAuthResponse(
            userInfo = XtreamUserInfo(
                username = "user",
                password = "pass",
                status = "Active",
                allowedOutputFormats = allowedOutputFormats
            )
        )
    }

    private fun unsupportedCatalogHttpException(): HttpException = HttpException(
        Response.error<Any>(
            404,
            "unsupported catalog".toResponseBody("text/plain".toMediaType())
        )
    )
}
