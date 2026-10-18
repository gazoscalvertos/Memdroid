package com.memdroid.app.data

import android.os.Environment
import java.io.File

/** Folders the old-files cleaner looks in. */
enum class OldFileSource(val label: String) {
    DOWNLOADS("Downloads"),
    SCREENSHOTS("Screenshots");

    val dirs: List<File>
        get() {
            val root = Environment.getExternalStorageDirectory()
            return when (this) {
                DOWNLOADS -> listOf(File(root, Environment.DIRECTORY_DOWNLOADS))
                SCREENSHOTS -> listOf(
                    File(root, "${Environment.DIRECTORY_PICTURES}/Screenshots"),
                    File(root, "${Environment.DIRECTORY_DCIM}/Screenshots"),
                )
            }
        }
}
