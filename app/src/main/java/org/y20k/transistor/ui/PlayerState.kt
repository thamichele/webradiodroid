/*
 * PlayerState.kt
 * Implements the PlayerState class
 * A PlayerState holds parameters describing the state of the player part of the UI
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.ui

import android.os.Parcelable
import com.google.gson.annotations.Expose
import kotlinx.parcelize.Parcelize


/*
 * PlayerState class
 */
@Parcelize
data class PlayerState (@Expose var stationPosition: Int = -1,
                        @Expose var stationUuid: String = String(),
                        @Expose var isPlaying: Boolean = false,
                        @Expose var sleepTimerRunning: Boolean = false): Parcelable