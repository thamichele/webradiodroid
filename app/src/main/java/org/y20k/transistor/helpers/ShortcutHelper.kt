/*
 * ShortcutHelper.kt
 * Implements the ShortcutHelper object
 * A ShortcutHelper creates and handles station shortcuts on the Home screen
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.transistor.Keys
import org.y20k.transistor.MainActivity
import org.y20k.transistor.R
import org.y20k.transistor.core.Station
import java.io.File


/*
 * ShortcutHelper object
 */
object ShortcutHelper {

    /* Define log tag */
    private val TAG: String = ShortcutHelper::class.java.simpleName


    /* Places shortcut on Home screen */
    fun placeShortcut(context: Context, station: Station) {
        // credit: https://medium.com/@BladeCoder/using-support-library-26-0-0-you-can-do-bb75911e01e8
        CoroutineScope(IO).launch {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                val shortcut: ShortcutInfoCompat = ShortcutInfoCompat.Builder(context, station.name)
                    .setShortLabel(station.name)
                    .setLongLabel(station.name)
                    .setIcon(createShortcutIcon(context, station))
                    .setIntent(createShortcutIntent(context, station.uuid))
                    .build()
                withContext(Main) {
                    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
                    Toast.makeText(context, R.string.toast_message_shortcut_created, Toast.LENGTH_LONG).show()
                }
            } else {
                withContext(Main) {
                    Toast.makeText(context, R.string.toast_message_shortcut_not_created, Toast.LENGTH_LONG).show()
                }
            }

        }
    }


//    /* Removes shortcut for given station from Home screen */
//    fun removeShortcut(context: Context, station: Station) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // from API level 26 ("Android O") on shortcuts are handled by ShortcutManager, which cannot remove shortcuts. The user must remove them manually.
//        } else {
//            // the pre 26 way: create and launch intent put shortcut on Home screen
//            val stationImageBitmap: Bitmap = ImageHelper.getScaledStationImage(context, station.image,192)
//            val removeIntent = Intent()
//            removeIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, station.name)
//            removeIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, ImageHelper.createSquareImage(context, stationImageBitmap, station.imageColor, 192, false))
//            removeIntent.putExtra("duplicate", false)
//            removeIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, createShortcutIntent(context, station.uuid))
//            removeIntent.action = "com.android.launcher.action.UNINSTALL_SHORTCUT"
//            context.applicationContext.sendBroadcast(removeIntent)
//        }
//    }


    /* Creates Intent for a station shortcut */
    private fun createShortcutIntent(context: Context, stationUuid: String): Intent {
        val shortcutIntent = Intent(context, MainActivity::class.java)
        shortcutIntent.action = Keys.ACTION_START
        shortcutIntent.putExtra(Keys.EXTRA_STATION_UUID, stationUuid)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return shortcutIntent
    }


    /* Create shortcut icon */
    private fun createShortcutIcon(context: Context, station: Station): IconCompat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // shortcut icon for Android 8+
            val iconSize: Int = (108 * UiHelper.getDensityScalingFactor(context)).toInt()
            val stationImageFile = File(station.image.toUri().path ?: "")
            val stationImageBitmap: Bitmap = Glide.with(context)
                .asBitmap()
                .load(station.image)
                .signature(ObjectKey(stationImageFile.lastModified()))
                .error(R.drawable.ic_default_station_image_64dp)
                .override(iconSize, iconSize)
                .fitCenter()
                .submit()
                .get() // this blocks until the image is loaded - use only on background thread
            IconCompat.createWithAdaptiveBitmap(UiHelper.createSquareImage(stationImageBitmap, station.imageColor, iconSize, true))
        } else {
            // legacy shortcut icon
            val iconSize: Int = (48 * UiHelper.getDensityScalingFactor(context)).toInt()
            val stationImageFile = File(station.image.toUri().path ?: "")
            val stationImageBitmap: Bitmap = Glide.with(context)
                .asBitmap()
                .load(station.image)
                .signature(ObjectKey(stationImageFile.lastModified()))
                .error(R.drawable.ic_default_station_image_64dp)
                .override(iconSize, iconSize)
                .fitCenter()
                .submit()
                .get() // this blocks until the image is loaded - use only on background thread
            IconCompat.createWithAdaptiveBitmap(UiHelper.createSquareImage(stationImageBitmap, station.imageColor, iconSize, true))
        }
    }

}
