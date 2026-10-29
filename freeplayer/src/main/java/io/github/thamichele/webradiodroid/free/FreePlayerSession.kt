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


    /* Companion object */
    companion object {
        // PlayerService of WebRadioDroid (its classes keep the package name of Transistor)
        const val PLAYER_SERVICE: String = "org.y20k.transistor.PlayerService"
    }


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
        val future: ListenableFuture<MediaController> = try {
            MediaController.Builder(carContext, SessionToken(carContext, ComponentName(BuildConfig.MAIN_APP_ID, PLAYER_SERVICE))).buildAsync()
        } catch (e: Exception) {
            // WebRadioDroid not installed (or not visible): show the hint instead of crashing
            Log.e(TAG, "Unable to find WebRadioDroid's player.", e)
            isConnectionFailed = true
            screen?.onControllerChanged()
            return
        }
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
