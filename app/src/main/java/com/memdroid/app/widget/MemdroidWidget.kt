package com.memdroid.app.widget

import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.storage.StorageManager
import android.util.SizeF
import android.widget.RemoteViews
import com.memdroid.app.MainActivity
import com.memdroid.app.R
import com.memdroid.app.data.RamRepository
import com.memdroid.app.data.formatBytes

/** Home screen widget: RAM and storage at a glance, with refresh and kill all. */
class MemdroidWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) updateAll(context)
    }

    companion object {
        private const val ACTION_REFRESH = "com.memdroid.app.widget.REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MemdroidWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val ram = RamRepository(context).systemRam()
            val stats = context.getSystemService(StorageStatsManager::class.java)
            val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
            val free = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
            val ramPercent = (ram.usedFraction * 100).toInt()
            val storagePercent = if (total > 0) ((total - free) * 100 / total).toInt() else 0

            val ramLine = context.getString(R.string.c_widget_ram_line, ramPercent, formatBytes(ram.availableBytes))
            val storageLine = context.getString(R.string.c_widget_storage_line, storagePercent, formatBytes(free))

            val full = RemoteViews(context.packageName, R.layout.widget).apply {
                setTextViewText(R.id.ram_text, ramLine)
                setProgressBar(R.id.ram_bar, 100, ramPercent, false)
                setTextViewText(R.id.storage_text, storageLine)
                setProgressBar(R.id.storage_bar, 100, storagePercent, false)
                setActions(context)
            }
            val small = RemoteViews(context.packageName, R.layout.widget_small).apply {
                setTextViewText(R.id.summary_text, context.getString(R.string.c_widget_summary, ramPercent, storagePercent))
                setActions(context)
            }
            // The launcher picks the biggest layout that fits as the widget is resized.
            val views = RemoteViews(mapOf(SizeF(150f, 40f) to small, SizeF(150f, 90f) to full))
            manager.updateAppWidget(ids, views)
        }

        private fun RemoteViews.setActions(context: Context) {
            setOnClickPendingIntent(R.id.widget_root, openApp(context, killAll = false))
            setOnClickPendingIntent(R.id.kill_all, openApp(context, killAll = true))
            setOnClickPendingIntent(
                R.id.refresh,
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent(context, MemdroidWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }

        /** Kill all opens the app's confirmation dialog rather than killing straight from the widget. */
        private fun openApp(context: Context, killAll: Boolean): PendingIntent =
            PendingIntent.getActivity(
                context, if (killAll) 2 else 1,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_KILL_ALL, killAll)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
