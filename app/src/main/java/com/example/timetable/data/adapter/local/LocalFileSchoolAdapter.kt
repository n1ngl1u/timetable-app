package com.example.timetable.data.adapter.local

import android.content.Context
import android.net.Uri
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.data.adapter.json.CourseParseException
import com.example.timetable.domain.importer.LoginState
import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolCredentials
import com.example.timetable.domain.importer.SchoolSession
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.importer.TimetableParser
import com.example.timetable.domain.model.Timetable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * 首个适配器：本地文件 / 剪贴板导入，不触网。
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
        val uri = request.sourceUri ?: throw CourseParseException(
            message = "本地导入缺少文件 URI",
            field = "sourceUri",
        )
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("无法打开课表文件: " + uri)
        stream.use { input -> input.readBytes().toString(Charsets.UTF_8) }
    }

    override fun parse(payload: String): Timetable = parser.parse(payload)

    companion object {
        const val SCHOOL_ID = "local-file"
    }
}
