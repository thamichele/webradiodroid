/*
 * MainActivity.kt
 * Implements the MainActivity class
 * MainActivity is the phone screen of WebRadioDroid Libero: it explains where the player is and checks the connection to WebRadioDroid
 *
 * This file is part of WebRadioDroid Libero
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package io.github.thamichele.webradiodroid.free

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture


/*
 * MainActivity class
 */
class MainActivity: Activity() {


    /* Main class variables */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var statusView: TextView


    /* Overrides onCreate from Activity */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding: Int = dp(24)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.parseColor("#1C1B1A"))
        }
        layout.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.parseColor("#E3845A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = getString(R.string.main_explanation)
            setTextColor(Color.parseColor("#EDEAE3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        })
        statusView = TextView(this).apply {
            text = getString(R.string.main_status_checking)
            setTextColor(Color.parseColor("#A39E95"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        layout.addView(statusView)
        layout.addView(Button(this).apply {
            text = getString(R.string.main_open_webradiodroid)
            setOnClickListener { openWebRadioDroid() }
        })
        setContentView(layout)
    }


    /* Overrides onStart from Activity */
    override fun onStart() {
        super.onStart()
        checkConnection()
    }


    /* Overrides onStop from Activity */
    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }


    /* Tries to connect to WebRadioDroid's player and shows the result */
    private fun checkConnection() {
        val future: ListenableFuture<MediaController> = try {
            MediaController.Builder(this, SessionToken(this, ComponentName(BuildConfig.MAIN_APP_ID, FreePlayerSession.PLAYER_SERVICE))).buildAsync()
        } catch (e: Exception) {
            statusView.text = getString(R.string.main_status_failed, e.message ?: e.javaClass.simpleName)
            return
        }
        controllerFuture = future
        future.addListener({
            statusView.text = try {
                future.get()
                getString(R.string.main_status_connected)
            } catch (e: Exception) {
                getString(R.string.main_status_failed, (e.cause ?: e).message ?: e.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    /* Opens WebRadioDroid on the phone */
    private fun openWebRadioDroid() {
        val intent: Intent? = packageManager.getLaunchIntentForPackage(BuildConfig.MAIN_APP_ID)
        if (intent != null) startActivity(intent) else statusView.text = getString(R.string.free_player_not_connected)
    }


    /* Converts dp to pixels */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()


}
