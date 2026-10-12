package com.example.timetable.data.crypto

import javax.crypto.SecretKey

/**
 * 主密钥来源。抽成接口是为了让 [FieldCipher] 能在 JVM 上被构造：
 * [CryptoKeyProvider] 需要 Context，测试里没有。
 */
interface MasterKeySource {

    /** @throws SecurePrefsError.KeyMissing 主密钥既取不到也生成不出来。 */
    fun secretKey(): SecretKey
}
