package com.memdroid.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray

object Community {
    const val REPO_URL = "https://github.com/gazoscalvertos/Memdroid"

    /** Donation page for the Support button. Empty hides the button. */
    const val DONATE_URL = ""

    data class Idea(val title: String, val description: String, val issue: Int)

    /** Features not built yet. Each links to a GitHub issue people can vote on. */
    val ideas = listOf(
        Idea("App media cleanup", "See which apps, like WhatsApp or Telegram, store the most photos and videos, and clean them up.", 1),
        Idea("Quick Settings tile", "Check RAM or kill all from the pull-down shade.", 2),
        Idea("Export a report", "A shareable summary of storage, RAM and battery.", 3),
    )

    data class Release(val version: String, val date: String, val changes: List<String>)

    val changelog = listOf(
        Release("1.0.6", "17 Oct 2026", listOf(
            "Auto-kill rules: stop chosen apps when the screen turns off or RAM runs low",
            "App growth: see which apps' storage grew the most over 7 or 30 days",
            "Feature ideas you can vote on, and bug and feature reporting",
            "What's new and the heroes wall",
        )),
        Release("1.0.5", "17 Oct 2026", listOf(
            "Optional low storage and RAM alerts (off by default)",
            "Usage history charts for RAM, storage and battery",
            "Duplicate file finder that always keeps one copy",
            "Unused apps list with uninstall",
            "Home screen widget",
        )),
        Release("1.0.4", "17 Oct 2026", listOf(
            "Battery tab with per-app battery use",
            "Side navigation and two-pane layouts on tablets and foldables",
        )),
        Release("1.0.3", "17 Oct 2026", listOf(
            "Storage breakdown by category, biggest apps and caches",
            "Folder explorer showing which folders use the most space",
            "Help for Android's restricted setting block",
        )),
        Release("1.0.2", "17 Oct 2026", listOf("Sort running apps by RAM or name", "Select all")),
        Release("1.0.1", "17 Oct 2026", listOf("First release: RAM, storage and large files")),
    )

    data class Hero(val name: String, val anonymous: Boolean, val since: String)

    /** Supporters listed in assets/heroes.json. Anonymous heroes are shown without their name. */
    fun heroes(context: Context): List<Hero> = try {
        val json = JSONArray(context.assets.open("heroes.json").bufferedReader().readText())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Hero(o.optString("name"), o.optBoolean("anonymous", false), o.optString("since"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun ideaUrl(idea: Idea) = "$REPO_URL/issues/${idea.issue}"

    /** A pre-filled GitHub issue with the app version and device, so reports arrive with context. */
    fun newIssueUrl(context: Context, bug: Boolean, title: String, details: String): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        val body = buildString {
            append(details.trim()).append("\n\n")
            if (bug) append("**Steps to reproduce:**\n1. \n\n**What happened instead:**\n\n")
            append("---\nMemdroid $version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        }
        return Uri.parse("$REPO_URL/issues/new").buildUpon()
            .appendQueryParameter("labels", if (bug) "bug" else "enhancement")
            .appendQueryParameter("title", title.trim())
            .appendQueryParameter("body", body)
            .build().toString()
    }

    fun open(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
