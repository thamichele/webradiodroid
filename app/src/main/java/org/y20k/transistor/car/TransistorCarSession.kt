/*
 * TransistorCarSession.kt
 * Implements the TransistorCarSession class
 * TransistorCarSession connects Android Auto to PlayerService and manages the car screens
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.car

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.media.MediaPlaybackManager
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.y20k.transistor.Keys
import org.y20k.transistor.PlayerService
import org.y20k.transistor.R
import org.y20k.transistor.extensions.play


/*
 * TransistorCarSession class
 */
class TransistorCarSession: Session() {


    /* Define log tag */
    private val TAG: String = TransistorCarSession::class.java.simpleName


    /* Main class variables */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    val controller: MediaController? get() {
        val future: ListenableFuture<MediaController> = controllerFuture ?: return null
        return if (future.isDone && !future.isCancelled) runCatching { future.get() }.getOrNull() else null
    }
    var isMediaPlaybackTokenRegistered: Boolean = false
        private set


    /* Init block: connect to PlayerService while the session is alive */
    init {
        lifecycle.addObserver(object: DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                initializeController()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                releaseController()
            }
        })
    }


    /* Overrides onCreateScreen from Session */
    override fun onCreateScreen(intent: Intent): Screen {
        val stationsScreen = StationsScreen(carContext, this)
        if (intent.action == Keys.ACTION_SHOW_MEDIA_PLAYBACK) {
            // launched from a system media surface (e.g. the media card): open the playback screen on top of the station list
            getScreenManager().push(stationsScreen)
            return PlaybackScreen(carContext, this)
        }
        return stationsScreen
    }


    /* Overrides onNewIntent from Session */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Keys.ACTION_SHOW_MEDIA_PLAYBACK) {
            showPlaybackScreen()
        }
    }


    /* Pushes the playback screen - unless it is already on top */
    fun showPlaybackScreen() {
        val screenManager: ScreenManager = getScreenManager()
        if (screenManager.top is PlaybackScreen) return
        screenManager.push(PlaybackScreen(carContext, this))
    }


    /* Starts playback of the station at the given position and shows the playback screen */
    fun playStation(stationPosition: Int) {
        val mediaController: MediaController? = controller
        if (mediaController == null) {
            CarToast.makeText(carContext, R.string.car_toast_player_not_ready, CarToast.LENGTH_SHORT).show()
            return
        }
        mediaController.play(carContext, stationPosition)
        showPlaybackScreen()
    }


    /* Returns the ScreenManager of this session */
    private fun getScreenManager(): ScreenManager {
        return carContext.getCarService(ScreenManager::class.java)
    }


    /* Initializes the MediaController - connects to PlayerService and requests the media session token */
    private fun initializeController() {
        val future: ListenableFuture<MediaController> = MediaController.Builder(carContext, SessionToken(carContext, ComponentName(carContext, PlayerService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({
            val mediaController: MediaController = controller ?: return@addListener
            mediaController.addListener(playerListener)
            requestAndRegisterMediaPlaybackToken(mediaController)
        }, ContextCompat.getMainExecutor(carContext))
    }


    /* Releases the MediaController */
    private fun releaseController() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }


    /* Asks PlayerService for its platform session token and hands it to the Car App Library */
    private fun requestAndRegisterMediaPlaybackToken(controller: MediaController) {
        val resultFuture: ListenableFuture<SessionResult> = controller.sendCustomCommand(SessionCommand(Keys.CMD_REQUEST_PLATFORM_TOKEN, Bundle.EMPTY), Bundle.EMPTY)
        resultFuture.addListener({
            try {
                val result: SessionResult = resultFuture.get()
                if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                    Log.e(TAG, "PlayerService refused to hand out the session token. Result code: ${result.resultCode}")
                    return@addListener
                }
                val platformToken: android.media.session.MediaSession.Token? = BundleCompat.getParcelable(result.extras, Keys.EXTRA_PLATFORM_TOKEN, android.media.session.MediaSession.Token::class.java)
                if (platformToken == null) {
                    Log.e(TAG, "Session token missing in result bundle.")
                    return@addListener
                }
                val compatToken: MediaSessionCompat.Token = MediaSessionCompat.Token.fromToken(platformToken)
                val mediaPlaybackManager = carContext.getCarService(CarContext.MEDIA_PLAYBACK_SERVICE) as MediaPlaybackManager
                mediaPlaybackManager.registerMediaPlaybackToken(compatToken)
                isMediaPlaybackTokenRegistered = true
                // a waiting playback screen can now show the MediaPlaybackTemplate
                getScreenManager().top.invalidate()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to register media playback token.", e)
            }
        }, ContextCompat.getMainExecutor(carContext))
    }


    /*
     * Player.Listener: refreshes the visible screen (station list highlights the station on air)
     */
    private val playerListener: Player.Listener = object: Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            getScreenManager().top.invalidate()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            getScreenManager().top.invalidate()
        }
    }
    /*
     * End of declaration
     */


}
