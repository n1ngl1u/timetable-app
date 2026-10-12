package com.example.timetable.data.crypto

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CipherEnvelopeTest {

    @Test
    fun encodeProducesV1PrefixedPayload() {
        val payload = CipherEnvelope.encode(iv(12), cipherText(16))

        assertTrue(payload.startsWith("v1:"))
        assertEquals(3, payload.split(":").size)
        assertTrue(CipherEnvelope.isVersioned(payload))
    }

    @Test
    fun roundTripThroughParseIsV1() {
        val iv = iv(12)
        val cipherText = cipherText(16)

        val parsed = CipherEnvelope.parse(CipherEnvelope.encode(iv, cipherText))

        assertTrue(parsed is CipherEnvelope.Parsed.V1)
        assertTrue((parsed as CipherEnvelope.Parsed.V1).iv.contentEquals(iv))
        assertTrue(parsed.cipherText.contentEquals(cipherText))
    }

    /**
     * 硬编码的旧格式密文 fixture，由骨架期版本生成。
     * IV = 0x00..0x0B，密文 = 0x10..0x1F。
     * **不能用新代码现场生成** —— 那样只能证明「新代码写得跟新代码一样」，测不出真实兼容性。
     */
    @Test
    fun skeletonEraFixtureIsClassifiedAsLegacy() {
        val parsed = CipherEnvelope.parse(LEGACY_FIXTURE)

        assertTrue(parsed is CipherEnvelope.Parsed.Legacy)
        val legacy = parsed as CipherEnvelope.Parsed.Legacy
        assertEquals(12, legacy.iv.size)
        assertEquals(16, legacy.cipherText.size)
        assertEquals(0x00, legacy.iv[0].toInt())
        assertEquals(0x0B, legacy.iv[11].toInt())
        assertEquals(0x10, legacy.cipherText[0].toInt())
        assertFalse(CipherEnvelope.isVersioned(LEGACY_FIXTURE))
    }

    @Test
    fun sameFixtureWithV1PrefixIsClassifiedAsV1() {
        val parsed = CipherEnvelope.parse(V1_FIXTURE)

        assertTrue(parsed is CipherEnvelope.Parsed.V1)
        assertTrue((parsed as CipherEnvelope.Parsed.V1).iv.contentEquals(Base64.getDecoder().decode(FIXTURE_IV_B64)))
    }

    @Test
    fun unknownVersionPrefixIsReportedNotGuessed() {
        listOf("v2", "v9", "v10").forEach { version ->
            val parsed = CipherEnvelope.parse(version + ":" + FIXTURE_IV_B64 + ":" + FIXTURE_CT_B64)

            assertTrue("版本 " + version + " 应判为 Unsupported", parsed is CipherEnvelope.Parsed.Unsupported)
            assertEquals(
                version + ":",
                (parsed as CipherEnvelope.Parsed.Unsupported).prefix,
            )
        }
    }

    @Test
    fun structuralDamageIsInvalid() {
        val invalidCases = listOf(
            "只有一段没有冒号",
            FIXTURE_IV_B64,
            FIXTURE_IV_B64 + ":" + FIXTURE_CT_B64 + ":多余段",
            "v1:" + FIXTURE_IV_B64,
            "!!!:" + FIXTURE_CT_B64,
            FIXTURE_IV_B64 + ":!!!!",
        )
        invalidCases.forEach { payload ->
            val parsed = CipherEnvelope.parse(payload)
            assertTrue("载荷「" + payload + "」应判为 Invalid", parsed is CipherEnvelope.Parsed.Invalid)
        }
    }

    @Test
    fun ivLengthMustBeTwelveOrSixteen() {
        listOf(11 to false, 12 to true, 15 to false, 16 to true, 17 to false, 32 to false)
            .forEach { (size, allowed) ->
                val payload = CipherEnvelope.encode(iv(size), cipherText(16))
                val parsed = CipherEnvelope.parse(payload)
                if (allowed) {
                    assertTrue("IV " + size + " 字节应被接受", parsed is CipherEnvelope.Parsed.V1)
                } else {
                    assertTrue(
                        "IV " + size + " 字节应被拒绝",
                        parsed is CipherEnvelope.Parsed.Invalid,
                    )
                }
            }
    }

    @Test
    fun cipherTextShorterThanTagIsInvalid() {
        listOf(0, 1, 15).forEach { size ->
            val parsed = CipherEnvelope.parse(CipherEnvelope.encode(iv(12), cipherText(size)))
            assertTrue("密文 " + size + " 字节应被拒绝", parsed is CipherEnvelope.Parsed.Invalid)
        }
        assertTrue(CipherEnvelope.parse(CipherEnvelope.encode(iv(12), cipherText(16))) is CipherEnvelope.Parsed.V1)
    }

    private fun iv(size: Int): ByteArray = ByteArray(size) { index -> index.toByte() }

    private fun cipherText(size: Int): ByteArray = ByteArray(size) { index -> (index + 16).toByte() }

    private companion object {
        const val FIXTURE_IV_B64 = "AAECAwQFBgcICQoL"
        const val FIXTURE_CT_B64 = "EBESExQVFhcYGRobHB0eHw=="
        const val LEGACY_FIXTURE = "AAECAwQFBgcICQoL:EBESExQVFhcYGRobHB0eHw=="
        const val V1_FIXTURE = "v1:AAECAwQFBgcICQoL:EBESExQVFhcYGRobHB0eHw=="
    }
}
