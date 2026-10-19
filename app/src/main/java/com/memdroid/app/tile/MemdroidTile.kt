package com.memdroid.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.memdroid.app.MainActivity
import com.memdroid.app.data.RamRepository

/** Quick Settings tile: shows RAM use, and a tap opens Memdroid's Kill all confirmation. */
class MemdroidTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val percent = (RamRepository(this).systemRam().usedFraction * 100).toInt()
        tile.label = "Kill all"
        tile.subtitle = "RAM $percent% used"
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Never kills straight from the tile: the app asks for confirmation first, like the widget.
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_KILL_ALL, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
