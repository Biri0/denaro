package it.rfmariano.denaro.data.local

import java.security.SecureRandom
import java.util.UUID

object UuidV7 {
    fun generate(timestampMillis: Long = System.currentTimeMillis()): String {
        require(timestampMillis in 0..MAX_TIMESTAMP_MILLIS) {
            "UUIDv7 timestamp must fit in 48 bits"
        }

        val randomBytes = ByteArray(RANDOM_BYTE_COUNT).also(random::nextBytes)
        val mostSignificantBits =
            (timestampMillis shl 16) or
                    (VERSION.toLong() shl 12) or
                    ((randomBytes[0].toLong() and 0x0F) shl 8) or
                    (randomBytes[1].toLong() and 0xFF)
        var leastSignificantBits = randomBytes[2].toLong() and 0x3F
        for (index in 3 until RANDOM_BYTE_COUNT) {
            leastSignificantBits =
                (leastSignificantBits shl 8) or
                        (randomBytes[index].toLong() and 0xFF)
        }
        leastSignificantBits = leastSignificantBits or (VARIANT.toLong() shl 62)

        return UUID(mostSignificantBits, leastSignificantBits).toString()
    }

    private val random = SecureRandom()

    private const val VERSION = 7
    private const val VARIANT = 2
    private const val RANDOM_BYTE_COUNT = 10
    private const val MAX_TIMESTAMP_MILLIS = 0xFFFFFFFFFFFFL
}
