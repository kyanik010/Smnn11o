package com.idealplayer.app.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash verifies matching pin`() {
        val hash = PinHasher.hash("1234")

        assertThat(PinHasher.verify("1234", hash)).isTrue()
        assertThat(PinHasher.verify("4321", hash)).isFalse()
    }

    @Test
    fun `hash uses salt so same pin has different encoded values`() {
        val first = PinHasher.hash("1234")
        val second = PinHasher.hash("1234")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `legacy sha256 hash verifies and needs rehash`() {
        val legacy = PinHasher.legacySha256("1234")

        assertThat(PinHasher.verify("1234", legacy)).isTrue()
        assertThat(PinHasher.needsRehash(legacy)).isTrue()
    }

    @Test
    fun `malformed hash fails closed`() {
        assertThat(PinHasher.verify("1234", "not-a-valid-hash")).isFalse()
        assertThat(PinHasher.needsRehash("not-a-valid-hash")).isTrue()
    }
}
