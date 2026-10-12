package com.example.timetable.data.crypto

import java.security.ProviderException
import javax.crypto.SecretKey

/**
 * StrongBox 优先与回落。刻意做成不依赖 Context 的纯对象：
 * [CryptoKeyProvider] 的构造函数需要 Context，JVM 单测里根本构造不出来，
 * 把这段分支留在他内部就等于放弃测试。
 */
internal object StrongBoxKeyFactory {

    /**
     * 先要求 StrongBox；失败则删除可能残留的半成品条目，再用普通 Keystore 重试一次。
     *
     * 删条目这步不能省：StrongBox 生成在中途失败时别名可能已经落库，
     * 不删的话重试会命中那条不可用的坏条目，表现为「回落了但还是用不了」。
     *
     * @throws ProviderException 两次尝试都失败时向上抛。
     */
    fun generate(
        generator: SecretKeyGenerator,
        alias: String,
        deleteAlias: (String) -> Unit,
        onStrongBoxUnavailable: (Throwable) -> Unit,
    ): SecretKey {
        return try {
            generator.generate(alias, strongBoxRequired = true)
        } catch (error: ProviderException) {
            onStrongBoxUnavailable(error)
            deleteAlias(alias)
            generator.generate(alias, strongBoxRequired = false)
        }
    }
}
