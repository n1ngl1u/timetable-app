package com.example.timetable.data.crypto

import java.security.ProviderException
import javax.crypto.SecretKey

/**
 * 主密钥生成钩子。抽成接口的唯一目的是覆盖 StrongBox 回落分支：
 * AndroidKeyStore 在 JVM 上不可用，没有这个缝就永远测不到 strongBoxRequired = false 那条路。
 */
fun interface SecretKeyGenerator {

    /**
     * 生成并落入 Keystore。
     *
     * @param strongBoxRequired true 表示必须在 StrongBox 内生成；设备不支持时
     *   必须抛 [ProviderException] 的子类（真机上是 android.security.keystore.StrongBoxUnavailableException，
     *   它是 ProviderException 的子类，刻意捕父类以便 JVM 单测能构造同样的失败）。
     */
    fun generate(alias: String, strongBoxRequired: Boolean): SecretKey
}
