package com.example.timetable.data.crypto

import android.content.Context
import com.example.timetable.domain.importer.SchoolCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 敏感字段专用存储：只存教务账号密码，非敏感项一律走 DataStore。
 */
@Singleton
class SecurePrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fieldCipher: FieldCipher,
) {

    private val preferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun putCredentials(accountKey: String, credentials: SchoolCredentials) {
        val payload = fieldCipher.encrypt(credentials.username + FIELD_SEPARATOR + credentials.password)
        preferences.edit().putString(keyFor(accountKey), payload).apply()
    }

    fun readCredentials(accountKey: String): SchoolCredentials? {
        val payload = preferences.getString(keyFor(accountKey), null) ?: return null
        val plainText = fieldCipher.decrypt(payload) ?: return null
        val parts = plainText.split(FIELD_SEPARATOR)
        if (parts.size != 2) return null
        return SchoolCredentials(username = parts[0], password = parts[1])
    }

    fun removeCredentials(accountKey: String) {
        preferences.edit().remove(keyFor(accountKey)).apply()
    }

    private fun keyFor(accountKey: String): String = KEY_PREFIX + accountKey

    private companion object {
        const val PREFS_NAME = "timetable_secure"
        const val KEY_PREFIX = "credentials_"
        const val FIELD_SEPARATOR = "\u0000"
    }
}
