package it.rfmariano.denaro.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UuidV7Test {
    @Test
    fun encodesTimestampVersionAndVariant() {
        val timestampMillis = 1_751_234_567_890L

        val uuid = UUID.fromString(UuidV7.generate(timestampMillis))

        assertEquals(timestampMillis, uuid.mostSignificantBits ushr 16)
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }

    @Test
    fun producesUniqueIdsForTheSameTimestamp() {
        val ids = List(100) { UuidV7.generate(1_751_234_567_890L) }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.map(UUID::fromString).any { it.leastSignificantBits != Long.MIN_VALUE })
    }

    @Test
    fun acceptsTimestampBounds() {
        assertTrue(UuidV7.generate(0).isNotBlank())
        assertTrue(UuidV7.generate(0xFFFFFFFFFFFFL).isNotBlank())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeTimestamp() {
        UuidV7.generate(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimestampLargerThan48Bits() {
        UuidV7.generate(0x1000000000000L)
    }
}
