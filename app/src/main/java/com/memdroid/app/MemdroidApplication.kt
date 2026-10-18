package com.memdroid.app

import android.app.Application
import com.memdroid.app.data.CrashReports
import com.memdroid.app.data.ShizukuShell

class MemdroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReports.install(this)
        ShizukuShell.init()
    }
}
