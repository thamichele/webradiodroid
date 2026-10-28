/*
 * FreePlayerScreen.kt
 * Implements the FreePlayerScreen class
 * FreePlayerScreen is WebRadioDroid's own "now playing" view on Android Auto: big artwork, song, station
 * and animated bars, drawn directly on the car screen (NavigationTemplate surface)
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.y20k.transistor.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


/*
 * FreePlayerScreen class
 */
class FreePlayerScreen(carContext: CarContext, private val session: TransistorCarSession): Screen(carContext) {


    /* Define log tag */
    private val TAG: String = FreePlayerScreen::class.java.simpleName


    /* Main class variables */
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var density: Float = 1f
    private var visibleArea: Rect = Rect()
    private var artwork: Bitmap? = null
    private var artworkSource: ByteArray? = null
    private var songTitle: String = String()
    private var songArtist: String = String()
    private var stationName: String = String()
    private var isPlaying: Boolean = false
    private val barCount: Int = 36
    private val restingLevel: Float = 0.06f
    private val levels: FloatArray = FloatArray(barCount) { restingLevel }
    private val targets: FloatArray = FloatArray(barCount) { restingLevel }
    private var lastTargetUpdate: Long = 0L

    // "Arancio UI" colors
    private val colorBackground: Int = "#FF1C1B1A".toColorInt()
    private val colorPanel: Int = "#FF252422".toColorInt()
    private val colorText: Int = "#FFEDEAE3".toColorInt()
    private val colorTextSecondary: Int = "#FFA39E95".toColorInt()
    private val colorAccent: Int = "#FFE3845A".toColorInt()

    private val artworkPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val panelPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorPanel }
    private val barPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAccent }
    private val titlePaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colorText; typeface = Typeface.DEFAULT_BOLD }
    private val artistPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colorTextSecondary }
    private val stationPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colorAccent; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.08f }


    /* Init block: draw on the car screen while this screen is alive */
    init {
        lifecycle.addObserver(object: DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
                session.controller?.addListener(playerListener)
                updateFromPlayer()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                session.controller?.removeListener(playerListener)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                handler.removeCallbacks(frameRunnable)
                artwork?.recycle()
                artwork = null
            }
        })
    }


    /* Overrides onGetTemplate from Screen */
    override fun onGetTemplate(): Template {
        val playPauseAction: Action = Action.Builder()
            .setIcon(carIcon(if (isPlaying) R.drawable.ic_notification_stop_36dp else R.drawable.ic_notification_play_36dp))
            .setOnClickListener {
                val controller = session.controller ?: return@setOnClickListener
                if (controller.isPlaying) controller.pause() else controller.play()
            }
            .build()
        val nextAction: Action = Action.Builder()
            .setIcon(carIcon(R.drawable.ic_notification_skip_to_next_36dp))
            .setOnClickListener { session.controller?.seekToNextMediaItem() }
            .build()
        val standardPlayerAction: Action = Action.Builder()
            .setIcon(carIcon(R.drawable.ic_car_standard_player_24dp))
            .setOnClickListener { session.openStandardPlayer() }
            .build()
        val stationsAction: Action = Action.Builder()
            .setIcon(carIcon(R.drawable.ic_car_list_24dp))
            .setOnClickListener { session.openStations() }
            .build()
        val actionStrip: ActionStrip = ActionStrip.Builder()
            .addAction(stationsAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(standardPlayerAction)
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }


    /* Reads metadata and playback state from the media controller */
    private fun updateFromPlayer() {
        val controller = session.controller
        val metadata: MediaMetadata = controller?.mediaMetadata ?: MediaMetadata.EMPTY
        val playing: Boolean = controller?.isPlaying == true
        if (playing != isPlaying) {
            isPlaying = playing
            invalidate() // refresh the play / stop icon
        }
        stationName = (metadata.subtitle ?: metadata.albumTitle ?: metadata.station ?: String()).toString()
        // radio streams send "Artist - Title" as title
        val title: String = metadata.title?.toString()?.trim() ?: String()
        val separator: Int = title.indexOf(" - ")
        if (title.isEmpty() || title == stationName) {
            songTitle = stationName
            songArtist = String()
        } else if (separator > 0) {
            songArtist = title.substring(0, separator).trim()
            songTitle = title.substring(separator + 3).trim()
        } else {
            songTitle = title
            songArtist = String()
        }
        // artwork is the square image created in CollectionHelper.buildMediaItem
        val data: ByteArray? = metadata.artworkData
        if (!data.contentEqualsOrBothNull(artworkSource)) {
            artworkSource = data
            artwork?.recycle()
            artwork = data?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        scheduleFrame()
    }


    /* Draws one frame: artwork, texts and bars */
    private fun drawFrame() {
        val currentSurface: Surface = surface ?: return
        if (!currentSurface.isValid) return
        val canvas: Canvas = try { currentSurface.lockCanvas(null) } catch (e: Exception) { Log.w(TAG, "Unable to lock surface: $e"); return }
        try {
            canvas.drawColor(colorBackground)
            val area = Rect(visibleArea)
            if (area.isEmpty) area.set(0, 0, surfaceWidth, surfaceHeight)
            val padding: Float = 24f * density
            val landscape: Boolean = area.width() >= area.height() * 1.2f
            if (landscape) {
                // artwork on the left, texts and bars on the right
                val artSize: Float = min(area.height() - padding * 2, area.width() * 0.45f)
                val artLeft: Float = area.left + padding
                val artTop: Float = area.top + (area.height() - artSize) / 2f
                drawArtwork(canvas, RectF(artLeft, artTop, artLeft + artSize, artTop + artSize))
                val textLeft: Float = artLeft + artSize + padding * 1.5f
                val textRight: Float = area.right - padding
                drawInfo(canvas, textLeft, textRight, area.top + padding, area.bottom - padding)
            } else {
                // artwork on top, texts and bars below
                val artSize: Float = min(area.width() - padding * 2, area.height() * 0.55f)
                val artLeft: Float = area.left + (area.width() - artSize) / 2f
                val artTop: Float = area.top + padding
                drawArtwork(canvas, RectF(artLeft, artTop, artLeft + artSize, artTop + artSize))
                drawInfo(canvas, area.left + padding, area.right - padding, artTop + artSize + padding, area.bottom - padding)
            }
        } finally {
            currentSurface.unlockCanvasAndPost(canvas)
        }
    }


    /* Draws the artwork (or a panel with the app glyph color if there is none) with rounded corners */
    private fun drawArtwork(canvas: Canvas, rect: RectF) {
        val radius: Float = 20f * density
        canvas.drawRoundRect(rect, radius, radius, panelPaint)
        val bitmap: Bitmap = artwork ?: return
        canvas.save()
        val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, null, rect, artworkPaint)
        canvas.restore()
    }


    /* Draws station name, song title, artist and the bars into the given column */
    private fun drawInfo(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        val width: Float = right - left
        if (width <= 0f || bottom <= top) return
        val height: Float = bottom - top
        // sizes follow the available height, within readable limits
        val titleSize: Float = (height * 0.12f).coerceIn(22f * density, 40f * density)
        stationPaint.textSize = titleSize * 0.5f
        titlePaint.textSize = titleSize
        artistPaint.textSize = titleSize * 0.65f
        val barsHeight: Float = (height * 0.28f).coerceAtMost(110f * density)
        val textBlock: Float = stationPaint.textSize * 1.6f + titlePaint.textSize * 2.5f + artistPaint.textSize * 1.4f
        var y: Float = top + max(0f, (height - textBlock - barsHeight - 16f * density) / 2f)

        y += stationPaint.textSize
        canvas.drawText(ellipsize(stationName.uppercase(), stationPaint, width), left, y, stationPaint)
        y += stationPaint.textSize * 0.6f

        // song title: up to two lines
        val titleLines: List<String> = wrapTwoLines(songTitle, titlePaint, width)
        titleLines.forEach { line ->
            y += titlePaint.textSize * 1.2f
            canvas.drawText(line, left, y, titlePaint)
        }
        if (songArtist.isNotEmpty()) {
            y += artistPaint.textSize * 1.4f
            canvas.drawText(ellipsize(songArtist, artistPaint, width), left, y, artistPaint)
        }

        // bars, aligned to the bottom of the info column
        val barsBottom: Float = min(bottom, y + 16f * density + barsHeight)
        drawBars(canvas, left, right, barsBottom - barsHeight, barsBottom)
    }


    /* Draws the animated bars */
    private fun drawBars(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        val height: Float = bottom - top
        val slot: Float = (right - left) / barCount
        val barWidth: Float = slot * 0.55f
        val radius: Float = barWidth / 2f
        for (i in 0 until barCount) {
            val barHeight: Float = max(barWidth, height * levels[i])
            val x: Float = left + i * slot + (slot - barWidth) / 2f
            barPaint.alpha = (140 + 115 * levels[i]).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(x, bottom - barHeight, x + barWidth, bottom, radius, radius, barPaint)
        }
    }


    /* Moves the bars towards their targets - returns true while they are still moving */
    private fun stepBars(): Boolean {
        val now: Long = System.currentTimeMillis()
        if (isPlaying && now - lastTargetUpdate > 140L) {
            lastTargetUpdate = now
            for (i in 0 until barCount) {
                // taller bars in the middle, like a spectrum
                val center: Float = 1f - abs(i - (barCount - 1) / 2f) / (barCount / 2f)
                targets[i] = (0.15f + 0.85f * Random.nextFloat()) * (0.45f + 0.55f * center)
            }
        } else if (!isPlaying) {
            targets.fill(restingLevel)
        }
        var moving = false
        for (i in 0 until barCount) {
            val delta: Float = targets[i] - levels[i]
            if (abs(delta) > 0.002f) moving = true
            levels[i] += delta * 0.25f
        }
        return moving || isPlaying
    }


    /* Animation loop: about 30 frames per second while playing, stops when the bars rest */
    private val frameRunnable: Runnable = object: Runnable {
        override fun run() {
            val keepGoing: Boolean = stepBars()
            drawFrame()
            if (keepGoing && surface != null) handler.postDelayed(this, 33L)
        }
    }


    /* Draws the next frame as soon as possible */
    private fun scheduleFrame() {
        handler.removeCallbacks(frameRunnable)
        handler.post(frameRunnable)
    }


    /* Shortens a text to the given width */
    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()


    /* Splits a text into at most two lines that fit the given width */
    private fun wrapTwoLines(text: String, paint: TextPaint, width: Float): List<String> {
        if (paint.measureText(text) <= width) return listOf(text)
        val words: List<String> = text.split(" ")
        var firstLine = String()
        var index = 0
        while (index < words.size) {
            val candidate: String = if (firstLine.isEmpty()) words[index] else "$firstLine ${words[index]}"
            if (paint.measureText(candidate) > width) break
            firstLine = candidate
            index++
        }
        if (firstLine.isEmpty()) return listOf(ellipsize(text, paint, width))
        val rest: String = words.drop(index).joinToString(" ")
        return listOf(firstLine, ellipsize(rest, paint, width))
    }


    /* Builds a CarIcon from a drawable */
    private fun carIcon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()


    /* Compares two (nullable) byte arrays by content */
    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
        if (this == null || other == null) this === other else this.contentEquals(other)


    /*
     * SurfaceCallback: the host hands over the car screen
     */
    private val surfaceCallback: SurfaceCallback = object: SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer.surface
            surfaceWidth = surfaceContainer.width
            surfaceHeight = surfaceContainer.height
            density = surfaceContainer.dpi / 160f
            scheduleFrame()
        }
        override fun onVisibleAreaChanged(area: Rect) {
            visibleArea = Rect(area)
            scheduleFrame()
        }
        override fun onStableAreaChanged(area: Rect) {
            // the visible area is what matters for drawing
        }
        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            handler.removeCallbacks(frameRunnable)
            surface = null
        }
    }
    /*
     * End of declaration
     */


    /*
     * Player.Listener: redraw on new song, station or play state
     */
    private val playerListener: Player.Listener = object: Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateFromPlayer()
        }
    }
    /*
     * End of declaration
     */


}
