package com.idealplayer.app.core.update

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class AppUpdateSecurityTest {

    @Test
    fun `secure update URLs reject cleartext and URL credentials`() {
        assertThat("https://updates.example.test/latest.json".toHttpUrl().isTrustedUpdateUrl()).isTrue()
        assertThat("http://updates.example.test/latest.json".toHttpUrl().isTrustedUpdateUrl()).isFalse()
        assertThat("https://user:pass@updates.example.test/latest.json".toHttpUrl().isTrustedUpdateUrl())
            .isFalse()
    }

    @Test
    fun `redirects support HTTPS relatives but reject downgrade and credentials`() {
        val current = "https://updates.example.test/releases/latest.json".toHttpUrl()

        assertThat(resolveTrustedUpdateRedirect(current, "../app.apk")?.toString())
            .isEqualTo("https://updates.example.test/app.apk")
        assertThat(resolveTrustedUpdateRedirect(current, "http://updates.example.test/app.apk"))
            .isNull()
        assertThat(resolveTrustedUpdateRedirect(current, "https://user:pass@updates.example.test/app.apk"))
            .isNull()
    }

    @Test
    fun `signer verification accepts identical signer sets and validated single signer rotation`() {
        assertThat(
            isTrustedUpdateSigner(
                installedCurrentSigners = setOf("old"),
                archiveCurrentSigners = setOf("old"),
                archiveSignerLineage = setOf("old")
            )
        ).isTrue()
        assertThat(
            isTrustedUpdateSigner(
                installedCurrentSigners = setOf("old"),
                archiveCurrentSigners = setOf("new"),
                archiveSignerLineage = setOf("new", "old")
            )
        ).isTrue()
    }

    @Test
    fun `signer verification rejects unrelated and partial signer sets`() {
        assertThat(
            isTrustedUpdateSigner(
                installedCurrentSigners = setOf("old"),
                archiveCurrentSigners = setOf("new"),
                archiveSignerLineage = setOf("new")
            )
        ).isFalse()
        assertThat(
            isTrustedUpdateSigner(
                installedCurrentSigners = setOf("one", "two"),
                archiveCurrentSigners = setOf("one"),
                archiveSignerLineage = setOf("one", "two")
            )
        ).isFalse()
    }
}
