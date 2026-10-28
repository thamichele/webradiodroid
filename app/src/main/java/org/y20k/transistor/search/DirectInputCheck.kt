/*
 * DirectInputCheck.kt
 * Implements the DirectInputCheck class
 * A DirectInputCheck checks if a station url is valid and returns station via a listener
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.search

import android.content.Context
import android.webkit.URLUtil
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.transistor.R
import org.y20k.transistor.core.Station
import org.y20k.transistor.helpers.CollectionHelper


/*
 * DirectInputCheck class
 */
class DirectInputCheck(private var directInputCheckListener: DirectInputCheckListener) {

    /* Interface used to send back station list for checked */
    interface DirectInputCheckListener {
        fun onDirectInputCheck(stationList: MutableList<Station>) {
        }
    }


    /* Define log tag */
    private val TAG: String = DirectInputCheck::class.java.simpleName


    /* Main class variables */
    private var lastCheckedAddress: String = String()


    /* Searches station(s) on radio-browser.info */
    fun checkStationAddress(context: Context, query: String) {
        // check if valid URL
        if (URLUtil.isValidUrl(query)) {
            val stationList: MutableList<Station> = mutableListOf()
            CoroutineScope(IO).launch {
                stationList.addAll(CollectionHelper.createStationsFromUrl(query, lastCheckedAddress))
                lastCheckedAddress = query
                withContext(Main) {
                    if (stationList.isNotEmpty()) {
                        // hand over station is to listener
                        directInputCheckListener.onDirectInputCheck(stationList)
                    } else {
                        // invalid address
                        Toast.makeText(context, R.string.toast_message_station_not_valid, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

}