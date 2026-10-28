/*
 * StationsScreen.kt
 * Implements the StationsScreen class
 * StationsScreen lists the stations of the collection on Android Auto (SectionedItemTemplate)
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridSection
import androidx.car.app.model.Header
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.net.toFile
import androidx.core.net.toUri
import org.y20k.transistor.Keys
import org.y20k.transistor.R
import org.y20k.transistor.core.Collection
import org.y20k.transistor.core.Station
import org.y20k.transistor.helpers.FileHelper


/*
 * StationsScreen class
 */
class StationsScreen(carContext: CarContext, private val session: TransistorCarSession): Screen(carContext) {


    /* Define log tag */
    private val TAG: String = StationsScreen::class.java.simpleName


    /* Main class variables */
    private val stationImageCache: MutableMap<String, CarIcon> = mutableMapOf()
    private val defaultStationImage: CarIcon by lazy {
        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_default_station_image_64dp)).build()
    }


    /* Overrides onGetTemplate from Screen */
    override fun onGetTemplate(): Template {
        val collection: Collection = FileHelper.readCollection(carContext)
        val gridLimit: Int = carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        val currentMediaId: String = session.controller?.currentMediaItem?.mediaId ?: String()
        val isPlaying: Boolean = session.controller?.isPlaying == true

        // one big tile per station - position matches the play list of PlayerService
        val stationGrid = GridSection.Builder()
            .setItemSize(GridSection.ITEM_SIZE_LARGE)
            .setNoItemsMessage(carContext.getString(R.string.car_message_no_stations))
        collection.stations.take(gridLimit).forEachIndexed { position, station ->
            val gridItem = GridItem.Builder()
                .setTitle(station.name)
                .setImage(getStationImage(station))
                .setOnClickListener { session.playStation(position) }
            if (isPlaying && station.uuid == currentMediaId) {
                gridItem.setText(carContext.getString(R.string.car_text_on_air))
            }
            stationGrid.addItem(gridItem.build())
        }

        // header action that opens the full screen player
        val nowPlayingAction: Action = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_music_note_24dp)).build())
            .setOnClickListener { session.showPlaybackScreen() }
            .build()
        val header: Header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(R.string.app_name))
            .addEndHeaderAction(nowPlayingAction)
            .build()

        return SectionedItemTemplate.Builder()
            .setHeader(header)
            .addSection(stationGrid.build())
            // standard media playback entry point (floating button on Car API 8, minimized control panel on newer hosts)
            .addAction(Action.MEDIA_PLAYBACK)
            .build()
    }


    /* Returns a small, cached CarIcon of the station image */
    private fun getStationImage(station: Station): CarIcon {
        val imageUriString: String = station.smallImage.ifEmpty { station.image }
        if (!imageUriString.startsWith("file://")) return defaultStationImage
        val cacheKey: String = "${station.uuid}|${imageUriString}"
        stationImageCache[cacheKey]?.let { return it }
        val icon: CarIcon = try {
            val bitmap: Bitmap? = BitmapFactory.decodeFile(imageUriString.toUri().toFile().path)
            if (bitmap == null) {
                defaultStationImage
            } else {
                // keep bitmaps small: all images travel to the car host in one template
                val scaledBitmap: Bitmap = bitmap.scale(Keys.SIZE_STATION_IMAGE_CAR, Keys.SIZE_STATION_IMAGE_CAR)
                CarIcon.Builder(IconCompat.createWithBitmap(scaledBitmap)).build()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load station image for ${station.name}", e)
            defaultStationImage
        }
        stationImageCache[cacheKey] = icon
        return icon
    }


}
