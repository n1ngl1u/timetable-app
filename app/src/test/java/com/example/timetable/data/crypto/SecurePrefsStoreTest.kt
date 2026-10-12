package com.example.timetable.data.crypto

import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.testing.FakeCipherEngine
import com.example.timetable.testing.FakeMasterKeySource
import com.example.timetable.testing.InMemoryKeyValueStore
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurePrefsStoreTest {

    private lateinit var keyValueStore: InMemoryKeyValueStore

    private lateinit var engine: FakeCipherEngine

    private lateinit var keySource: FakeMasterKeySource

    private lateinit var store: SecurePrefsStore

    private lateinit var cipher: FieldCipher

    @Before
    fun setUp() {
        keyValueStore = InMemoryKeyValueStore()
        engine = FakeCipherEngine()
        keySource = FakeMasterKeySource()
        cipher = FieldCipher(keySource, engine)
        store = SecurePrefsStore(keyValueStore, cipher)
    }

    @Test
    fun roundTripThroughStorage() {
        assertEquals(SecurePrefsResult.Success(Unit), store.putCredentials(ACCOUNT, CREDENTIALS))

        assertEquals(
            SecurePrefsResult.Success<SchoolCredentials?>(CREDENTIALS),
            store.readCredentials(ACCOUNT),
        )
        assertTrue(
            "落盘的必须是 v1 格式",
            keyValueStore.get(STORAGE_KEY)!!.startsWith(CipherEnvelope.VERSION_PREFIX_V1),
        )
    }

    /** Success(null) 与 Failure(KeyMissing) 是两件完全不同的事。 */
    @Test
    fun neverStoredReturnsSuccessNull() {
        assertEquals(
            SecurePrefsResult.Success<SchoolCredentials?>(null),
            store.readCredentials(ACCOUNT),
        )
    }

    @Test
    fun aadIsRecordScopedNotFieldScoped() {
        store.putCredentials(ACCOUNT, CREDENTIALS)

        assertEquals(listOf("credentials:" + ACCOUNT), engine.aadContexts)
    }

    /** 把 A 账号的密文搬到 B 账号字段，必须解不开。 */
    @Test
    fun cipherTextMovedToAnotherAccountFails() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        keyValueStore.put("credentials_other", keyValueStore.get(STORAGE_KEY)!!)

        val result = store.readCredentials("other")

        assertTrue(result is SecurePrefsResult.Failure)
        assertTrue(
            (result as SecurePrefsResult.Failure).error is SecurePrefsError.IntegrityViolation,
        )
    }

    @Test
    fun tamperedCipherTextReturnsIntegrityViolation() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        val payload = keyValueStore.get(STORAGE_KEY)!!
        val parts = payload.split(":")
        val cipherBytes = java.util.Base64.getDecoder().decode(parts[2])
        cipherBytes[0] = (cipherBytes[0].toInt() xor 0x01).toByte()
        keyValueStore.put(
            STORAGE_KEY,
            parts[0] + ":" + parts[1] + ":" + java.util.Base64.getEncoder().encodeToString(cipherBytes),
        )

        val result = store.readCredentials(ACCOUNT)

        assertTrue(result is SecurePrefsResult.Failure)
        assertTrue(
            (result as SecurePrefsResult.Failure).error is SecurePrefsError.IntegrityViolation,
        )
    }

    @Test
    fun legacyPayloadIsReadThenMigratedToV1() {
        keyValueStore.put(STORAGE_KEY, legacyPayload())

        val result = store.readCredentials(ACCOUNT)

        assertEquals(SecurePrefsResult.Success<SchoolCredentials?>(CREDENTIALS), result)
        assertTrue(
            "读旧格式后必须写回 v1",
            keyValueStore.get(STORAGE_KEY)!!.startsWith(CipherEnvelope.VERSION_PREFIX_V1),
        )
    }

    @Test
    fun migrationHappensOnlyOnce() {
        keyValueStore.put(STORAGE_KEY, legacyPayload())
        store.readCredentials(ACCOUNT)
        val encryptsAfterMigration = engine.encryptCount

        store.readCredentials(ACCOUNT)
        store.readCredentials(ACCOUNT)

        assertEquals("已迁移过的载荷不得重复加密", encryptsAfterMigration, engine.encryptCount)
    }

    /** 写回失败不影响本次读：仍然返回明文，下次读再试一次。 */
    @Test
    fun migrationWriteBackFailureStillReturnsPlainText() {
        keyValueStore.put(STORAGE_KEY, legacyPayload())
        keyValueStore.failOnPut = IOException("磁盘满了")

        val result = store.readCredentials(ACCOUNT)

        assertEquals(SecurePrefsResult.Success<SchoolCredentials?>(CREDENTIALS), result)
        assertTrue(
            "写回失败时旧载荷应原样保留",
            !keyValueStore.get(STORAGE_KEY)!!.startsWith(CipherEnvelope.VERSION_PREFIX_V1),
        )
    }

    @Test
    fun migrationReEncryptFailureStillReturnsPlainText() {
        keyValueStore.put(STORAGE_KEY, legacyPayload())
        // 旧格式解密用掉一次加密配额之后，让重加密失败
        store.readCredentials(ACCOUNT)
        keyValueStore.put(STORAGE_KEY, legacyPayload())
        // CipherEngine 的异常契约只允许 GeneralSecurityException / IllegalArgumentException
        engine.failEncryptWith = java.security.GeneralSecurityException("加密层故障")

        val result = store.readCredentials(ACCOUNT)

        assertTrue("重加密失败不得把读变成失败", result is SecurePrefsResult.Success)
        assertEquals(CREDENTIALS, (result as SecurePrefsResult.Success).value)
    }

    @Test
    fun unknownVersionIsReported() {
        keyValueStore.put(STORAGE_KEY, "v7:AAAA:BBBB")

        val result = store.readCredentials(ACCOUNT)

        assertEquals(
            SecurePrefsResult.Failure(SecurePrefsError.UnsupportedFormat("v7:")),
            result,
        )
    }

    @Test
    fun missingMasterKeyIsDistinctFromNeverStored() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        keySource.failure = SecurePrefsError.KeyMissing

        val result = store.readCredentials(ACCOUNT)

        assertEquals(SecurePrefsResult.Failure(SecurePrefsError.KeyMissing), result)
    }

    @Test
    fun clearAllCredentialsIsIdempotent() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        store.putCredentials("other", CREDENTIALS)

        assertEquals(SecurePrefsResult.Success(Unit), store.clearAllCredentials())
        assertEquals(SecurePrefsResult.Success(Unit), store.clearAllCredentials())
        assertTrue(keyValueStore.snapshot().isEmpty())
    }

    /**
     * 清空后读接口返回 Success(null) 而不是 KeyMissing：
     * clearAllCredentials 按契约**不删除 Keystore 主密钥**（主密钥还给 SQLCipher 用），
     * 所以「密钥没了」这个前提不成立，正确的语义是「从未存过」。
     */
    @Test
    fun readAfterClearReportsNeverStored() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        store.clearAllCredentials()

        assertEquals(
            SecurePrefsResult.Success<SchoolCredentials?>(null),
            store.readCredentials(ACCOUNT),
        )
    }

    @Test
    fun clearFailureIsReportedAsUnrecoverable() {
        keyValueStore.failOnClear = IOException("存储不可写")

        val result = store.clearAllCredentials()

        assertTrue(result is SecurePrefsResult.Failure)
        assertTrue(
            (result as SecurePrefsResult.Failure).error is SecurePrefsError.Unrecoverable,
        )
    }

    @Test
    fun removeCredentialsIsIdempotent() {
        store.putCredentials(ACCOUNT, CREDENTIALS)

        assertEquals(SecurePrefsResult.Success(Unit), store.removeCredentials(ACCOUNT))
        assertEquals(SecurePrefsResult.Success(Unit), store.removeCredentials(ACCOUNT))
        assertNull(keyValueStore.get(STORAGE_KEY))
    }

    /** 复刻骨架期算法：空 AAD、无版本前缀。刻意不复用迁移后的写路径。 */
    private fun legacyPayload(): String =
        cipher.encrypt(
            CREDENTIALS.username + "\u0000" + CREDENTIALS.password,
            FieldCipher.LEGACY_AAD_CONTEXT,
        ).removePrefix(CipherEnvelope.VERSION_PREFIX_V1)

    private companion object {
        const val ACCOUNT = "account-a"
        const val STORAGE_KEY = "credentials_account-a"
        val CREDENTIALS = SchoolCredentials(username = "alice", password = "s3cret")
    }
}
