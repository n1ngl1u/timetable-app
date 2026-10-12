package com.example.timetable.data.adapter.local

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 剪贴板文本来源。Android 10+ 只允许前台应用读取剪贴板，因此由 UI 层调用本类取值，
 * 再把文本作为 ImportRequest.payload 交给导入链路，不放进 SchoolAdapter。
 */
@Singleton
class ClipboardPayloadSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readText(): String? {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(context)?.toString()
        return text?.takeIf { it.isNotBlank() }
    }
}
