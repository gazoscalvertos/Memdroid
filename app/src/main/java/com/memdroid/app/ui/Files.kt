package com.memdroid.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.ThumbnailUtils
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.memdroid.app.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun mimeType(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

/** Opens a file in whichever app the user picks (Photos, a video player, a PDF viewer…). */
fun openFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.b_no_app_can_open), Toast.LENGTH_SHORT).show()
    }
}

/** A thumbnail for images and videos, or a file icon for anything else. */
@Composable
fun FileThumbnail(file: File, size: Dp = 56.dp) {
    val context = LocalContext.current
    val mime = mimeType(file)
    val isVideo = mime.startsWith("video/")
    val thumb by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            try {
                val px = (size.value * context.resources.displayMetrics.density).toInt()
                when {
                    mime.startsWith("image/") -> ThumbnailUtils.createImageThumbnail(file, Size(px, px), null)
                    isVideo -> ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
                    else -> null
                }?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = thumb
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            if (isVideo) Icon(Icons.Filled.PlayCircle, contentDescription = stringResource(R.string.b_cd_video), tint = Color.White)
        } else {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
