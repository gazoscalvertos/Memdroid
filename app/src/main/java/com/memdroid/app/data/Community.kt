package com.memdroid.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray

object Community {
    const val REPO_URL = "https://github.com/gazoscalvertos/Memdroid"

    const val PRIVACY_URL = "https://gazoscalvertos.github.io/Memdroid/privacy.html"

    /** A donation page for copies not installed from Play, where Play tips can't work. Empty hides the button. */
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
        Release("1.0.14", "19 Oct 2026", listOf(
            "A new welcome: privacy first, offline, no ads and free for everyone",
            "Illustrations for each setup step",
            "A beer button at the top right opens Buy me a beer",
        )),
        Release("1.0.13", "19 Oct 2026", listOf(
            "Widget buttons sit beside the bars, and a compact layout fits a one-row widget",
            "RAM and Battery summaries sit above the list on tablets",
            "Wording fits tablets and foldables as well as phones",
        )),
        Release("1.0.12", "18 Oct 2026", listOf("Fixed the privacy policy link")),
        Release("1.0.11", "18 Oct 2026", listOf(
            "Buy me a beer: tip through Google Play and join the heroes wall by name or anonymously",
            "Privacy policy, in Settings › About",
            "Updated for Android 16",
        )),
        Release("1.0.10", "18 Oct 2026", listOf(
            "Smart cleanup: see everything you could free in one card",
            "Optional recycle bin: restore deleted files for 30 days",
            "Old downloads and screenshots cleaner",
            "Background battery drain alerts (optional)",
            "Setup wizard for permissions and Shizuku",
            "Crash reports you can send with one tap",
            "Fixed battery stats on phones with a Work profile or Private Space",
        )),
        Release("1.0.7", "17 Oct 2026", listOf(
            "Fixed a crash on the Battery tab",
            "Thumbnails and an Open button for duplicates and large files",
            "Ignore apps forever in the unused apps list",
            "A brand new look for the heroes wall",
        )),
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

    data class Hero(val name: String, val anonymous: Boolean, val since: String, val badge: String)

    /** Supporters listed in assets/heroes.json. Anonymous heroes are shown without their name. */
    fun heroes(context: Context): List<Hero> = try {
        val json = JSONArray(context.assets.open("heroes.json").bufferedReader().readText())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Hero(o.optString("name"), o.optBoolean("anonymous", false), o.optString("since"), o.optString("badge"))
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

    /**
     * A pre-filled GitHub issue asking to be added to the heroes wall. New heroes are checked
     * against the Play order ID and added to assets/heroes.json in the next release.
     */
    fun heroSignupUrl(name: String, anonymous: Boolean, thanks: Tips.Thanks): String {
        val shown = if (anonymous) "Anonymous hero" else name.trim()
        val body = buildString {
            append("Please add me to the heroes wall.\n\n")
            append("**Show as:** ").append(shown).append("\n")
            append("**Tip:** ").append(Tips.PRODUCTS.find { it.id == thanks.productId }?.title ?: thanks.productId).append("\n")
            append("**Order:** ").append(thanks.orderId ?: "unknown").append("\n")
        }
        return Uri.parse("$REPO_URL/issues/new").buildUpon()
            .appendQueryParameter("labels", "hero")
            .appendQueryParameter("title", "Hero: $shown")
            .appendQueryParameter("body", body)
            .build().toString()
    }

    fun open(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
