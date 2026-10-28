/*
 * PlaybackScreen.kt
 * Implements the PlaybackScreen class
 * PlaybackScreen shows the full screen "now playing" view on Android Auto (MediaPlaybackTemplate)
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import org.y20k.transistor.R


/*
 * PlaybackScreen class
 */
class PlaybackScreen(carContext: CarContext, private val session: TransistorCarSession): Screen(carContext) {


    /* Overrides onGetTemplate from Screen */
    override fun onGetTemplate(): Template {
        // header action that switches to WebRadioDroid's free player
        val freePlayerAction: Action = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_free_player_24dp)).build())
            .setOnClickListener { session.openFreePlayer() }
            .build()
        val header: Header = Header.Builder()
            .setStartHeaderAction(Action.BACK)
            .setTitle(carContext.getString(R.string.car_title_now_playing))
            .addEndHeaderAction(freePlayerAction)
            .build()
        // the host shows an error if a MediaPlaybackTemplate is sent before the session token is registered
        if (!session.isMediaPlaybackTokenRegistered) {
            return MessageTemplate.Builder(carContext.getString(R.string.car_message_connecting_to_player))
                .setHeader(header)
                .setLoading(true)
                .build()
        }
        // metadata (station, track title), artwork and controls are filled in by the host from PlayerService's media session
        return MediaPlaybackTemplate.Builder()
            .setHeader(header)
            .build()
    }


}
