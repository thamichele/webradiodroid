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
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import org.y20k.transistor.R


/*
 * PlaybackScreen class
 */
class PlaybackScreen(carContext: CarContext, private val session: TransistorCarSession): Screen(carContext) {


    /* Overrides onGetTemplate from Screen */
    override fun onGetTemplate(): Template {
        val header: Header = Header.Builder()
            .setStartHeaderAction(Action.BACK)
            .setTitle(carContext.getString(R.string.car_title_now_playing))
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
