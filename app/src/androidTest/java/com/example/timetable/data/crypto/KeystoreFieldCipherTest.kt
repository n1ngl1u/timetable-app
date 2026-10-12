package com.example.timetable.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyFactory
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实 AndroidKeyStore 上的加解密路径。JVM 与 Robolectric 都模拟不了 Keystore，
 * 所以这些用例只能在这里跑。
 */
@RunWith(AndroidJUnit4::class)
class KeystoreFieldCipherTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var keyStore: KeyStore

    private lateinit var keyProvider: CryptoKeyProvider

    private lateinit var cipher: FieldCipher

    @Before
    fun setUp() {
        resetCryptoState()
        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyProvider = CryptoKeyProvider(
            context = context,
            keyStore = keyStore,
            secureRandom = SecureRandom(),
            secretKeyGenerator = KeystoreSecretKeyGenerator(),
        )
        cipher = FieldCipher(keyProvider, KeystoreCipherEngine())
    }

    @After
    fun tearDown() {
        resetCryptoState()
    }

    @Test
    fun roundTripOnRealKeystore() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)

        assertTrue("新密文必须带 v1 前缀", payload.startsWith("v1:"))
        assertEquals(
            FieldDecryptionResult.Success("alice\u0000secret", legacyPayload = false),
            cipher.decrypt(payload, AAD_A),
        )
    }

    @Test
    fun tamperedCipherTextFails() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)
        val parts = payload.split(":")
        val bytes = Base64.getDecoder().decode(parts[2])
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        val tampered = parts[0] + ":" + parts[1] + ":" + Base64.getEncoder().encodeToString(bytes)

        val result = cipher.decrypt(tampered, AAD_A)

        assertTrue(result is FieldDecryptionResult.Failure)
        assertTrue(
            "真 Keystore 上 tag 校验必须拦下来",
            (result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation,
        )
    }

    @Test
    fun aadMismatchFails() {
        val payload = cipher.encrypt("alice\u0000secret", AAD_A)

        val result = cipher.decrypt(payload, AAD_B)

        assertTrue(result is FieldDecryptionResult.Failure)
        assertTrue((result as FieldDecryptionResult.Failure).error is SecurePrefsError.IntegrityViolation)
    }

    /** 旧格式密文由**骨架期算法**现场生成（空 AAD、无前缀），不是用新代码产出后剥前缀。 */
    @Test
    fun skeletonEraCipherTextIsReadableAndMigrated() {
        val legacy = legacyEncrypt("alice\u0000secret")
        assertTrue("旧格式不该带前缀", !legacy.startsWith("v1:"))

        val result = cipher.decrypt(legacy, AAD_A)

        assertEquals(
            FieldDecryptionResult.Success("alice\u0000secret", legacyPayload = true),
            result,
        )
    }

    /**
     * 硬编码 fixture 只能证明格式分类，不能证明可解密 ——
     * 密文是用**本机 Keystore 里的密钥**加密的，换一台设备就解不开，
     * 因此「硬编码密文能解开」这件事在物理上无法成立。
     */
    @Test
    fun hardcodedSkeletonFixtureIsClassifiedAsLegacy() {
        val parsed = CipherEnvelope.parse(LEGACY_FIXTURE)

        assertTrue(parsed is CipherEnvelope.Parsed.Legacy)
        assertEquals(12, (parsed as CipherEnvelope.Parsed.Legacy).iv.size)
    }

    @Test
    fun strongBoxPathIsUsedWhenSupported() {
        assumeTrue("设备不支持 StrongBox，跳过（并在验证清单里记录）", strongBoxAvailable())

        cipher.encrypt("probe", AAD_A)

        val key = keyProvider.secretKey()
        val keyInfo = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(key, android.security.keystore.KeyInfo::class.java)
            as android.security.keystore.KeyInfo

        assertEquals(
            "支持 StrongBox 的设备上主密钥必须落在安全芯片里",
            KeyProperties.SECURITY_LEVEL_STRONGBOX,
            keyInfo.securityLevel,
        )
        // strongBoxRequired = false 那条路径只有在 StrongBox 失败回落时才走到，
        // 支持 StrongBox 的设备上永远走不到，因此由 JVM 侧的 StrongBoxKeyFactoryTest 覆盖。
    }

    /** 复刻骨架期算法：空 AAD、iv:ct、无版本前缀。刻意不复用生产代码。 */
    private fun legacyEncrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(cipher.iv) + ":" + encoder.encodeToString(encrypted)
    }

    private fun strongBoxAvailable(): Boolean = try {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                STRONGBOX_PROBE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(true)
                .build(),
        )
        generator.generateKey()
        true
    } catch (error: ProviderException) {
        false
    } finally {
        runCatching { keyStore.deleteEntry(STRONGBOX_PROBE_ALIAS) }
    }

    private fun resetCryptoState() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(CryptoKeyProvider.KEY_ALIAS)
        }
        // 主密钥被删后已落盘的 SQLCipher 口令必然解不开，数据库文件必须一并清掉，
        // 否则同一进程里后跑的用例会拉起 MainActivity 并在开库时崩掉
        context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        SecurePrefsStore(SharedPreferencesKeyValueStore(context), cipher).clearAllCredentials()
        context.deleteDatabase(DB_NAME)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val STRONGBOX_PROBE_ALIAS = "strongbox-probe"
        const val KEY_PREFS_NAME = "timetable_secure_key"
        const val DB_NAME = "timetable.db"
        const val AAD_A = "credentials:account-a"
        const val AAD_B = "credentials:account-b"

        /** 由骨架期版本生成：IV = 0x00..0x0B，密文 = 0x10..0x1F。 */
        const val LEGACY_FIXTURE = "AAECAwQFBgcICQoL:EBESExQVFhcYGRobHB0eHw=="
    }
}
