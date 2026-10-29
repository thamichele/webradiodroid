/*
 * RadioForwardingPlayer.kt
 * Implements the RadioForwardingPlayer class
 * RadioForwardingPlayer adapts the radio player for media controllers: next/previous switch stations,
 * and each song gets a fixed 5 minute progress (radio streams have no duration) so that the system
 * media notification shows its animated progress bar - which can not be dragged
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor

import android.os.SystemClock
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.y20k.transistor.helpers.AudioHelper
import java.util.concurrent.CopyOnWriteArraySet


/*
 * RadioForwardingPlayer class
 */
@UnstableApi
class RadioForwardingPlayer(player: Player): ForwardingPlayer(player) {


    /* Main class variables */
    private val songDuration: Long = 5 * 60 * 1000L
    private val listeners: CopyOnWriteArraySet<Player.Listener> = CopyOnWriteArraySet()
    private var songTitle: String = String()
    private var songStart: Long = SystemClock.elapsedRealtime()
    private var pausedPosition: Long = 0L


    /* Init block: follow song changes and play state of the wrapped player */
    init {
        player.addListener(object: Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title: String = AudioHelper.getMetadataString(mediaMetadata)
                if (title != songTitle) {
                    songTitle = title
                    restartSongProgress()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                restartSongProgress()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // freeze the progress while paused, continue from there afterwards
                if (isPlaying) {
                    songStart = SystemClock.elapsedRealtime() - pausedPosition
                } else {
                    pausedPosition = (SystemClock.elapsedRealtime() - songStart).coerceIn(0L, songDuration)
                }
            }
        })
    }


    /* Next / previous (e.g. from headphones) switch stations - seeking within a song is not possible */
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .remove(COMMAND_SEEK_BACK)
            .remove(COMMAND_SEEK_FORWARD)
            .build()
    }
    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
    override fun seekToNext() = seekToNextMediaItem()
    override fun seekToPrevious() = seekToPreviousMediaItem()


    /* The fixed song duration and the time since the song started */
    override fun getDuration(): Long = songDuration
    override fun getContentDuration(): Long = songDuration
    override fun getCurrentPosition(): Long = songPosition()
    override fun getContentPosition(): Long = songPosition()
    override fun getBufferedPosition(): Long = songPosition()
    override fun getContentBufferedPosition(): Long = songPosition()
    override fun getTotalBufferedDuration(): Long = 0L
    override fun isCurrentMediaItemSeekable(): Boolean = false
    override fun isCurrentMediaItemLive(): Boolean = false
    override fun isCurrentMediaItemDynamic(): Boolean = false


    /* Keeps track of the listeners (media session), to tell them when the progress starts over */
    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners.add(listener)
    }
    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners.remove(listener)
    }


    /* Time since the current song started - stops at the end of the fixed duration */
    private fun songPosition(): Long {
        val position: Long = if (isPlaying) SystemClock.elapsedRealtime() - songStart else pausedPosition
        return position.coerceIn(0L, songDuration)
    }


    /* Starts the progress of a new song from zero and lets the listeners know */
    private fun restartSongProgress() {
        val oldPosition: Player.PositionInfo = positionInfo()
        songStart = SystemClock.elapsedRealtime()
        pausedPosition = 0L
        val newPosition: Player.PositionInfo = positionInfo()
        val events = Player.Events(FlagSet.Builder().add(EVENT_POSITION_DISCONTINUITY).build())
        listeners.forEach { listener ->
            listener.onPositionDiscontinuity(oldPosition, newPosition, DISCONTINUITY_REASON_INTERNAL)
            listener.onEvents(this, events)
        }
    }


    /* Describes the current position for listeners */
    private fun positionInfo(): Player.PositionInfo {
        val position: Long = songPosition()
        return Player.PositionInfo(null, currentMediaItemIndex, currentMediaItem, null, currentPeriodIndex, position, position, -1, -1)
    }


}
