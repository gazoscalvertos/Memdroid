package com.memdroid.app.data

import android.os.Environment
import androidx.annotation.StringRes
import com.memdroid.app.R
import java.io.File

/** Folders the old-files cleaner looks in. */
enum class OldFileSource(@StringRes val labelRes: Int) {
    DOWNLOADS(R.string.b_source_downloads),
    SCREENSHOTS(R.string.b_source_screenshots);

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
