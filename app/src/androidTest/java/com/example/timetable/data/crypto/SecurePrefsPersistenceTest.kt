package com.example.timetable.data.crypto

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timetable.domain.importer.SchoolCredentials
import java.security.KeyStore
import java.security.SecureRandom
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主密钥的持久化：密钥落在 Keystore 而不是进程内存，因此换一个 KeyStore 实例、
 * 换一套 provider/engine/store 之后，已落盘的密文仍然解得开。
 *
 * 注意这只是**近似**：真正的「杀进程重启后仍可解密」无法在 instrumented 测试里完成
 * （force-stop 会把 instrumentation 一起带走），该项以手工步骤登记在
 * docs/device-verification.md 第 35 条。
 */
@RunWith(AndroidJUnit4::class)
class SecurePrefsPersistenceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var store: SecurePrefsStore

    @Before
    fun setUp() {
        store = newStore()
        store.clearAllCredentials()
    }

    @After
    fun tearDown() {
        store.clearAllCredentials()
    }

    @Test
    fun credentialsWrittenByOneInstanceAreReadableByAnother() {
        assertEquals(SecurePrefsResult.Success(Unit), store.putCredentials(ACCOUNT, CREDENTIALS))

        // 全新实例：新的 KeyStore 视图、新的 provider/engine/store
        val reopened = newStore()

        assertEquals(
            SecurePrefsResult.Success<SchoolCredentials?>(CREDENTIALS),
            reopened.readCredentials(ACCOUNT),
        )
    }

    @Test
    fun payloadOnDiskIsVersionedAndRecordScoped() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payload = preferences.getString("credentials_" + ACCOUNT, null)

        assertTrue("落盘载荷必须带版本前缀", payload!!.startsWith(CipherEnvelope.VERSION_PREFIX_V1))

        // AAD 取到记录级：换个账号键去读同一条密文必须失败
        preferences.edit().putString("credentials_other", payload).commit()
        val crossed = newStore().readCredentials("other")
        assertTrue(crossed is SecurePrefsResult.Failure)
    }

    @Test
    fun clearAllCredentialsRemovesEveryStoredKey() {
        store.putCredentials(ACCOUNT, CREDENTIALS)
        store.putCredentials("other", CREDENTIALS)

        assertEquals(SecurePrefsResult.Success(Unit), store.clearAllCredentials())

        assertEquals(
            SecurePrefsResult.Success<SchoolCredentials?>(null),
            newStore().readCredentials(ACCOUNT),
        )
        assertEquals(
            SecurePrefsResult.Success<SchoolCredentials?>(null),
            newStore().readCredentials("other"),
        )
        assertTrue(
            "清空后不得留下任何 credentials_ 键",
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .all.keys.none { it.startsWith("credentials_") },
        )
    }

    /** 每次都新建 KeyStore 视图与整条加密链，确保读到的密钥来自 Keystore 而非内存。 */
    private fun newStore(): SecurePrefsStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val keyProvider = CryptoKeyProvider(
            context = context,
            keyStore = keyStore,
            secureRandom = SecureRandom(),
            secretKeyGenerator = KeystoreSecretKeyGenerator(),
        )
        return SecurePrefsStore(
            keyValueStore = SharedPreferencesKeyValueStore(context),
            fieldCipher = FieldCipher(keyProvider, KeystoreCipherEngine()),
        )
    }

    private companion object {
        const val PREFS_NAME = "timetable_secure"
        const val ACCOUNT = "account-a"
        val CREDENTIALS = SchoolCredentials(username = "alice", password = "s3cret")
    }
}
