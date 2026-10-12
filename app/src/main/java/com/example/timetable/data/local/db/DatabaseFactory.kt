package com.example.timetable.data.local.db

import android.content.Context
import androidx.room.Room
import com.example.timetable.data.crypto.CryptoKeyProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 整库加密（SQLCipher）的唯一装配入口，口令来自 Android Keystore 保护的随机密钥。
 */
@Singleton
class DatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyProvider: CryptoKeyProvider,
) {

    fun create(): AppDatabase {
        loadNativeLibrary()
        val openHelperFactory = SupportOpenHelperFactory(keyProvider.databasePassphrase())
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(openHelperFactory)
            .addMigrations(*Migrations.ALL)
            .build()
    }

    private fun loadNativeLibrary() {
        if (nativeLibraryLoaded) return
        synchronized(LOCK) {
            if (nativeLibraryLoaded) return
            System.loadLibrary(SQLCIPHER_LIBRARY)
            nativeLibraryLoaded = true
        }
    }

    private companion object {
        const val SQLCIPHER_LIBRARY = "sqlcipher"
        val LOCK = Any()

        @Volatile
        var nativeLibraryLoaded = false
    }
}
