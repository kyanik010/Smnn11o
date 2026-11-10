package com.idealplayer.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.database.EpgProgramEntity
import org.junit.Test

class EpgGuideSelectionTest {

    @Test
    fun `guide uses the first matching channel key and sorts programs`() {
        val secondary = listOf(
            program("secondary", 2_000L, "Later"),
            program("secondary", 1_000L, "Earlier")
        )

        val selected = selectGuideProgramsForCandidates(
            programsByLookupId = mapOf("secondary" to secondary),
            candidates = listOf("primary", "secondary")
        )

        assertThat(selected.map(EpgProgramEntity::title))
            .containsExactly("Earlier", "Later")
            .inOrder()
    }

    @Test
    fun `guide does not combine programs from lower priority aliases`() {
        val selected = selectGuideProgramsForCandidates(
            programsByLookupId = mapOf(
                "primary" to listOf(program("primary", 1_000L, "Primary")),
                "secondary" to listOf(program("secondary", 2_000L, "Secondary"))
            ),
            candidates = listOf("primary", "secondary")
        )

        assertThat(selected.map(EpgProgramEntity::title)).containsExactly("Primary")
    }

    private fun program(channelId: String, start: Long, title: String) = EpgProgramEntity(
        channelId = channelId,
        title = title,
        startTime = start,
        endTime = start + 1_000L
    )
}
