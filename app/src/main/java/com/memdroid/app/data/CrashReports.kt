package com.memdroid.app.data

import android.content.Context
import java.io.File

/**
 * Saves the stack trace when Memdroid crashes, so the next launch can offer to report it as a
 * pre-filled GitHub issue. Nothing is sent anywhere unless the user taps Report.
 */
object CrashReports {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file(appContext).writeText("Thread: ${thread.name}\n${error.stackTraceToString()}")
            } catch (_: Exception) {
                // Never let crash saving cause a second crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    /** A GitHub issue for a crash or error. Trimmed so the link stays under URL length limits. */
    fun issueUrl(context: Context, title: String, details: String): String =
        Community.newIssueUrl(context, bug = true, title = title, details = "```\n${details.take(5000)}\n```")
}
