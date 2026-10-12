package com.example.timetable.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用 checkSelfPermission 判定，不用 try-catch 捕获 SecurityException ——
 * 后者会把「没权限」和「provider 内部异常」混成一条路径。
 */
@Singleton
class CalendarPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasReadPermission(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun hasWritePermission(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    fun hasAllPermissions(): Boolean = hasReadPermission() && hasWritePermission()

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        val REQUIRED_PERMISSIONS: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        }
    }
}
