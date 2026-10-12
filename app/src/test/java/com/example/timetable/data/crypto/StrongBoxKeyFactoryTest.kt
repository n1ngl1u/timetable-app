package com.example.timetable.data.crypto

import com.example.timetable.testing.FakeSecretKeyGenerator
import java.security.ProviderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StrongBox 回落分支。CryptoKeyProvider 本身需要 Context，JVM 里构造不出来，
 * 因此回落逻辑被抽成不依赖 Context 的纯对象，在这里覆盖。
 *
 * 真机上如果设备支持 StrongBox，strongBoxRequired = false 那条路永远走不到 ——
 * 只有这两个用例能覆盖它。
 */
class StrongBoxKeyFactoryTest {

    @Test
    fun strongBoxIsPreferredWhenAvailable() {
        val generator = FakeSecretKeyGenerator().apply { strongBoxFailure = null }
        val deleted = mutableListOf<String>()

        val key = StrongBoxKeyFactory.generate(
            generator = generator,
            alias = ALIAS,
            deleteAlias = { alias -> deleted += alias },
            onStrongBoxUnavailable = { },
        )

        assertNotNull(key)
        assertEquals(listOf(true), generator.requestedStrongBox)
        assertTrue("StrongBox 成功时不该删别名", deleted.isEmpty())
    }

    @Test
    fun fallsBackToPlainKeystoreWhenStrongBoxIsUnavailable() {
        val generator = FakeSecretKeyGenerator()
        val deleted = mutableListOf<String>()
        val logged = mutableListOf<Throwable>()

        val key = StrongBoxKeyFactory.generate(
            generator = generator,
            alias = ALIAS,
            deleteAlias = { alias -> deleted += alias },
            onStrongBoxUnavailable = { error -> logged += error },
        )

        assertNotNull(key)
        assertEquals("必须先要 StrongBox 再回落", listOf(true, false), generator.requestedStrongBox)
        assertEquals("半成品条目必须删掉，否则重试会命中坏条目", listOf(ALIAS), deleted)
        assertEquals(1, logged.size)
        assertTrue(logged.single() is ProviderException)
    }

    @Test
    fun bothAttemptsFailingPropagates() {
        val generator = FakeSecretKeyGenerator().apply {
            alwaysFailWith = ProviderException("Keystore 整体不可用")
        }
        val deleted = mutableListOf<String>()

        val thrown = runCatching {
            StrongBoxKeyFactory.generate(
                generator = generator,
                alias = ALIAS,
                deleteAlias = { alias -> deleted += alias },
                onStrongBoxUnavailable = { },
            )
        }.exceptionOrNull()

        assertTrue(thrown is ProviderException)
        assertEquals(listOf(true, false), generator.requestedStrongBox)
        assertEquals(listOf(ALIAS), deleted)
    }

    private companion object {
        const val ALIAS = "timetable_master_key"
    }
}
