/*
 * FreePlayerSession.kt
 * Implements the FreePlayerSession class
 * FreePlayerSession connects the car screen to WebRadioDroid's player (media session of its PlayerService)
 *
 * This file is part of WebRadioDroid Libero
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package io.github.thamichele.webradiodroid.free

import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture


/*
 * FreePlayerSession class
 */
class FreePlayerSession: Session() {


    /* Define log tag */
    private val TAG: String = FreePlayerSession::class.java.simpleName


    /* Main class variables */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var screen: FreePlayerScreen? = null
    val controller: MediaController? get() {
        val future: ListenableFuture<MediaController> = controllerFuture ?: return null
        return if (future.isDone && !future.isCancelled) runCatching { future.get() }.getOrNull() else null
    }
    var isConnectionFailed: Boolean = false
        private set


    /* Init block: connect to WebRadioDroid's player while the session is alive */
    init {
        lifecycle.addObserver(object: DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                initializeController()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                controllerFuture?.let { MediaController.releaseFuture(it) }
                controllerFuture = null
                screen = null
            }
        })
    }


    /* Overrides onCreateScreen from Session */
    override fun onCreateScreen(intent: Intent): Screen {
        return FreePlayerScreen(carContext, this).also { screen = it }
    }


    /* Connects to the PlayerService of WebRadioDroid - binding to it also starts the player if needed */
    private fun initializeController() {
        val token = SessionToken(carContext, ComponentName(BuildConfig.MAIN_APP_ID, "org.y20k.transistor.PlayerService"))
        val future: ListenableFuture<MediaController> = MediaController.Builder(carContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controller == null) {
                Log.e(TAG, "Unable to connect to WebRadioDroid (${BuildConfig.MAIN_APP_ID}).")
                isConnectionFailed = true
            }
            screen?.onControllerChanged()
        }, ContextCompat.getMainExecutor(carContext))
    }


}
