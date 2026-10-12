package com.example.timetable.data.crypto

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生产实现。文件名与 SharedPreferences 名沿用骨架期取值，
 * 改名会让已存凭据读不到。
 */
@Singleton
class SharedPreferencesKeyValueStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecureKeyValueStore {

    private val preferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    override fun keys(): Set<String> = preferences.all.keys.toSet()

    internal companion object {
        const val PREFS_NAME = "timetable_secure"
    }
}
