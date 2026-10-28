/*
 * MediaControllerExt.kt
 * Implements the MediaControllerExt extension methods
 * Useful extension methods for MediaController
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.extensions

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import org.y20k.transistor.Keys
import org.y20k.transistor.R


private val TAG: String = "MediaControllerExt"

/* Starts the sleep timer */
fun MediaController.startSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_START_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Cancels the sleep timer */
fun MediaController.cancelSleepTimer() {
    sendCustomCommand(SessionCommand(Keys.CMD_CANCEL_SLEEP_TIMER, Bundle.EMPTY), Bundle.EMPTY)
}


/* Request sleep timer state */
fun MediaController.requestSleepTimerRunning(): ListenableFuture<SessionResult> {
    return sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_RUNNING, Bundle.EMPTY), Bundle.EMPTY)
}


/* Request sleep timer remaining */
fun MediaController.requestSleepTimerRemaining(): ListenableFuture<SessionResult> {
    return sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_SLEEP_TIMER_REMAINING, Bundle.EMPTY), Bundle.EMPTY)
}


/* Request sleep timer remaining */
fun MediaController.requestMetadataHistory(): ListenableFuture<SessionResult> {
    return sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_METADATA_HISTORY, Bundle.EMPTY), Bundle.EMPTY)
}


/* Starts playback with a new media item */
fun MediaController.play(context: Context, stationPosition: Int) {
    // go to media item at given position in the internal play list
    if (stationPosition > -1) {
        seekTo(stationPosition, 0L)
        play()
    } else {
        Toast.makeText(context, R.string.toast_message_error_station_not_found, Toast.LENGTH_SHORT).show()
    }
}


/* Starts playback with of a stream url */
fun MediaController.playStreamDirectly(streamUri: String) {
    sendCustomCommand(SessionCommand(Keys.CMD_PLAY_STREAM, Bundle.EMPTY), bundleOf(Pair(Keys.KEY_STREAM_URI, streamUri)))
}



/* Returns mediaId of currently active media item */
fun MediaController.getCurrentMediaId(): String {
    if (mediaItemCount > 0) {
        return getMediaItemAt(0).mediaId
    } else {
        return String()
    }
}


/* Returns if controller/player has one or more media items  */
fun MediaController.hasMediaItems(): Boolean {
    return mediaItemCount > 0
}
