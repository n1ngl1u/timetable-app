package com.example.timetable.data.crypto

import com.example.timetable.testing.FakeCipherEngine
import com.example.timetable.testing.FakeMasterKeySource
import java.util.Base64
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FieldCipherTest {

    private lateinit var engine: FakeCipherEngine

    private lateinit var keySource: FakeMasterKeySource

    private lateinit var cipher: FieldCipher

    @Before
    fun setUp() {
        engine = FakeCipherEngine()
        keySource = FakeMasterKeySource()
        cipher = FieldCipher(keySource, engine)
    }

    @Test
    fun roundTripWithRecordScopedAad() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)

        val result = cipher.decrypt(payload, AAD_A)

        assertEquals(FieldDecryptionResult.Success("alice\u0000secret", legacyPayload = false), result)
        assertEquals(listOf(AAD_A), engine.aadContexts)
    }

    @Test
    fun wrongAadFailsWithIntegrityViolation() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)

        val result = cipher.decrypt(payload, AAD_B)

        assertTrue(result is FieldDecryptionResult.Failure)
        assertTrue((result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation)
    }

    /** 证明 AAD 确实被传下去，而不是被实现悄悄忽略。 */
    @Test
    fun v1PayloadCannotBeDecryptedWithEmptyAad() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)

        val result = cipher.decrypt(payload, FieldCipher.LEGACY_AAD_CONTEXT)

        assertTrue(result is FieldDecryptionResult.Failure)
    }

    @Test
    fun legacyPayloadDecryptsWithEmptyAadAndIsFlagged() {
        val payload = legacyShapedPayload("alice\u0000secret")

        val result = cipher.decrypt(payload, AAD_A)

        assertEquals(FieldDecryptionResult.Success("alice\u0000secret", legacyPayload = true), result)
        assertEquals("旧密文必须用空 AAD 解密", "", engine.aadContexts.last())
    }

    @Test
    fun tamperedCipherTextFails() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)

        val result = cipher.decrypt(tamper(payload), AAD_A)

        assertTrue(result is FieldDecryptionResult.Failure)
        assertTrue((result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation)
    }

    @Test
    fun unknownVersionReportsThePrefix() {
        val payload = "v2:" + Base64.getEncoder().encodeToString(ByteArray(12)) +
            ":" + Base64.getEncoder().encodeToString(ByteArray(16))

        val result = cipher.decrypt(payload, AAD_A)

        assertEquals(
            FieldDecryptionResult.Failure(SecurePrefsError.UnsupportedFormat("v2:")),
            result,
        )
    }

    @Test
    fun structuralDamageIsIntegrityViolation() {
        listOf("", "abc", "only-one-part", "a:b:c:d").forEach { payload ->
            val result = cipher.decrypt(payload, AAD_A)
            assertTrue(
                "载荷「" + payload + "」应为 IntegrityViolation",
                result is FieldDecryptionResult.Failure &&
                    (result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation,
            )
        }
    }

    @Test
    fun missingMasterKeyIsReportedAsKeyMissing() {
        val payload = cipherPayload()
        keySource.failure = SecurePrefsError.KeyMissing

        val result = cipher.decrypt(payload, AAD_A)

        assertEquals(FieldDecryptionResult.Failure(SecurePrefsError.KeyMissing), result)
    }

    @Test
    fun nonTagSecurityFailureIsUnrecoverable() {
        engine.failEncryptWith = null
        val payload = cipher.encrypt("x", AAD_A)
        val freshEngine = FakeCipherEngine()
        freshEngine.failEncryptWith = java.security.NoSuchAlgorithmException("provider 故障")
        val failing = FieldCipher(keySource, freshEngine)

        val result = failing.decrypt(payload, AAD_A)

        assertTrue(result is FieldDecryptionResult.Failure)
        assertTrue((result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation)
    }

    @Test
    fun encryptPropagatesKeyMissing() {
        keySource.failure = SecurePrefsError.KeyMissing

        val thrown = runCatching { cipher.encrypt("x", AAD_A) }.exceptionOrNull()

        assertEquals(SecurePrefsError.KeyMissing, thrown)
    }

    @Test
    fun engineTagFailureMapsToIntegrityViolation() {
        val payload = cipher.encrypt("x", AAD_A)
        val failing = FieldCipher(keySource, ThrowingEngine(AEADBadTagException("tag 坏了")))

        val result = failing.decrypt(payload, AAD_A)

        assertTrue(result is FieldDecryptionResult.Failure)
        assertTrue((result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation)
    }

    /** 用当前实现产出一条密文，再剥掉版本前缀，得到「旧格式形状」的载荷。 */
    private fun legacyShapedPayload(plainText: String): String =
        cipher.encrypt(plainText, FieldCipher.LEGACY_AAD_CONTEXT)
            .removePrefix(CipherEnvelope.VERSION_PREFIX_V1)

    private fun cipherPayload(): String = cipher.encrypt("x", AAD_A)

    private fun tamper(payload: String): String {
        val parts = payload.split(":")
        val cipherBytes = Base64.getDecoder().decode(parts[2])
        cipherBytes[0] = (cipherBytes[0].toInt() xor 0x01).toByte()
        return parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(cipherBytes)
    }

    private class ThrowingEngine(private val error: Throwable) : CipherEngine {
        override fun encrypt(key: javax.crypto.SecretKey, aadContext: String, plainText: ByteArray) =
            throw error

        override fun decrypt(
            key: javax.crypto.SecretKey,
            aadContext: String,
            iv: ByteArray,
            cipherText: ByteArray,
        ) = throw error
    }

    private companion object {
        const val AAD_A = "credentials:account-a"
        const val AAD_B = "credentials:account-b"
    }
}
