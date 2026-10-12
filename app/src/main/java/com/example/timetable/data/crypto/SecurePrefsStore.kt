package com.example.timetable.data.crypto

import com.example.timetable.domain.importer.SchoolCredentials
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * 敏感字段专用存储：只存教务账号密码，非敏感项一律走 DataStore。
 *
 * 线程约束：**所有公开方法线程安全**，内部由 [ReentrantLock] 串行化读写与清空。
 * 方法本身是阻塞式的（不是 suspend）—— [com.example.timetable.domain.repository.SchoolRepository]
 * 的同名方法不是 suspend，改成挂起会破坏该契约；锁因此用 ReentrantLock 而不是协程 Mutex。
 */
@Singleton
class SecurePrefsStore @Inject constructor(
    private val keyValueStore: SecureKeyValueStore,
    private val fieldCipher: FieldCipher,
) {

    private val lock = ReentrantLock()

    /** @return 成功仅代表值已交给落地层；写盘由落地层异步完成。 */
    fun putCredentials(
        accountKey: String,
        credentials: SchoolCredentials,
    ): SecurePrefsResult<Unit> = lock.withLock {
        val plainText = credentials.username + FIELD_SEPARATOR + credentials.password
        runCatching {
            fieldCipher.encrypt(plainText, aadContextOf(accountKey))
        }.fold(
            onSuccess = { payload ->
                keyValueStore.put(keyFor(accountKey), payload)
                SecurePrefsResult.Success(Unit)
            },
            onFailure = { error -> SecurePrefsResult.Failure(asSecurePrefsError(error)) },
        )
    }

    /**
     * 读取凭据。三种返回语义必须分清：
     *
     * - Success(null) —— **从未存过**这个 accountKey。这是正常状态，不是错误。
     * - Success(credentials) —— 有密文且解密成功。
     * - Failure(KeyMissing) —— 主密钥既取不到也生成不出来。与 Success(null) 的区别是：
     *   这里**可能存过凭据**，只是密钥没了，用户需要重新登录而不仅仅是「没存过」。
     * - Failure(IntegrityViolation) —— 密文存在但解密失败：tag 校验不过、AAD 不匹配或载荷损坏。
     *   主密钥被系统重置导致的旧密文解密失败也落在这里（密码学上无法与篡改区分）。
     * - Failure(UnsupportedFormat) —— 版本前缀不认识，通常是降级安装或数据被外部写入。
     * - Failure(Unrecoverable) —— 其余不可恢复错误。
     *
     * 读到 v1 之前的旧密文时会自动用当前 AAD 重新加密为 v1 并写回；写回失败不影响本次结果。
     */
    fun readCredentials(accountKey: String): SecurePrefsResult<SchoolCredentials?> = lock.withLock {
        val storageKey = keyFor(accountKey)
        val payload = keyValueStore.get(storageKey)
            ?: return@withLock SecurePrefsResult.Success<SchoolCredentials?>(null)

        val aadContext = aadContextOf(accountKey)
        when (val decrypted = fieldCipher.decrypt(payload, aadContext)) {
            is FieldDecryptionResult.Failure -> SecurePrefsResult.Failure(decrypted.error)
            is FieldDecryptionResult.Success -> {
                if (decrypted.legacyPayload) {
                    migrateToV1(storageKey, decrypted.plainText, aadContext)
                }
                val parts = decrypted.plainText.split(FIELD_SEPARATOR)
                if (parts.size != 2) {
                    SecurePrefsResult.Failure(
                        SecurePrefsError.IntegrityViolation("凭据明文字段数不是 2"),
                    )
                } else {
                    SecurePrefsResult.Success(SchoolCredentials(parts[0], parts[1]))
                }
            }
        }
    }

    /** 幂等：键不存在时同样成功。 */
    fun removeCredentials(accountKey: String): SecurePrefsResult<Unit> = lock.withLock {
        runCatching { keyValueStore.remove(keyFor(accountKey)) }.fold(
            onSuccess = { SecurePrefsResult.Success(Unit) },
            onFailure = { error -> SecurePrefsResult.Failure(asSecurePrefsError(error)) },
        )
    }

    /**
     * 清除本存储的所有键（供「退出登录」或「重置」使用）。
     *
     * **不删除 Keystore 主密钥**：主密钥还被 SQLCipher 整库加密使用，删掉会让数据库彻底打不开。
     * 幂等：对空存储调用同样返回成功。
     */
    fun clearAllCredentials(): SecurePrefsResult<Unit> = lock.withLock {
        runCatching { keyValueStore.clear() }.fold(
            onSuccess = { SecurePrefsResult.Success(Unit) },
            onFailure = { error -> SecurePrefsResult.Failure(asSecurePrefsError(error)) },
        )
    }

    /**
     * 旧格式（无版本前缀、空 AAD）读出后立刻用当前 AAD 重新加密为 v1 并写回。
     * 写回失败**不改变本次读的结果** —— 仍然返回明文，下次读再试一次。
     */
    private fun migrateToV1(storageKey: String, plainText: String, aadContext: String) {
        val upgraded = runCatching { fieldCipher.encrypt(plainText, aadContext) }.getOrNull()
            ?: return
        runCatching { keyValueStore.put(storageKey, upgraded) }
    }

    private fun asSecurePrefsError(error: Throwable): SecurePrefsError =
        error as? SecurePrefsError ?: SecurePrefsError.Unrecoverable(error)

    private fun keyFor(accountKey: String): String = KEY_PREFIX + accountKey

    /** AAD 取到记录级：只绑字段用途的话，把 A 账号的密文搬到 B 账号字段仍能解密。 */
    private fun aadContextOf(accountKey: String): String = AAD_PREFIX_CREDENTIALS + accountKey

    internal companion object {
        /** AAD 前缀：用途 + 记录标识。见 docs/conventions.md 第 12 条。 */
        const val AAD_PREFIX_CREDENTIALS = "credentials:"

        private const val KEY_PREFIX = "credentials_"
        private const val FIELD_SEPARATOR = "\u0000"
    }
}
