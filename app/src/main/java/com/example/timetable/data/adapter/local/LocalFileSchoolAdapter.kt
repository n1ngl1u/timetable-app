package com.example.timetable.data.adapter.local

import android.content.Context
import android.net.Uri
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.importer.ImportIoException
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.importer.TimetableParser
import com.example.timetable.domain.model.Timetable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * 首个适配器：本地课表导入，不触网。
 *
 * 两条来源路径：
 * - 文件：sourceUri 为 content://（SAF / 文件选择器）或 file:// / 绝对路径；
 * - 剪贴板：由 UI 层经 ClipboardPayloadSource 取文本，作为 ImportRequest.payload 直接进入 parse。
 */
@Singleton
class LocalFileSchoolAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: TimetableParser,
    private val dispatchers: DispatcherProvider,
) : SchoolAdapter {

    override val schoolId: String = SCHOOL_ID

    override val displayName: String = "本地课表文件"

    override val requiresNetwork: Boolean = false

    override suspend fun fetchLogin(credentials: SchoolCredentials): LoginState = LoginState.Success

    override suspend fun fetchTimetable(
        session: SchoolSession,
        request: TimetableFetchRequest,
    ): String = withContext(dispatchers.io) {
        val source = request.sourceUri?.takeIf { it.isNotBlank() }
            ?: throw ImportIoException("本地导入缺少文件 URI")
        val uri = Uri.parse(source)
        when (val scheme = uri.scheme?.lowercase()) {
            null, "", "file" -> readFromFile(uri.path ?: source)
            "content", "android.resource" -> readFromStream(uri)
            else -> throw ImportIoException("不支持的本地来源协议: " + scheme)
        }
    }

    override fun parse(payload: String): Timetable = parser.parse(payload)

    private fun readFromStream(uri: Uri): String {
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (error: IOException) {
            throw ImportIoException("打开课表文件失败: " + uri, error)
        } ?: throw ImportIoException("无法打开课表文件: " + uri)
        return try {
            stream.use { input -> input.readBytes().toString(Charsets.UTF_8) }
        } catch (error: IOException) {
            throw ImportIoException("读取课表文件失败: " + uri, error)
        }
    }

    private fun readFromFile(path: String): String {
        val file = File(path)
        if (!file.exists()) {
            throw ImportIoException("课表文件不存在: " + path)
        }
        return try {
            file.readBytes().toString(Charsets.UTF_8)
        } catch (error: IOException) {
            throw ImportIoException("读取课表文件失败: " + path, error)
        }
    }

    companion object {
        const val SCHOOL_ID = "local-file"
    }
}
