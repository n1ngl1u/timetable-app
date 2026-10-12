package com.example.timetable

import android.app.Application
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TimetableApplication : Application() {

    @Inject
    lateinit var perfTracer: PerfTracer

    override fun onCreate() {
        super.onCreate()
        perfTracer.mark(TraceTags.APP_ON_CREATE)
    }
}
