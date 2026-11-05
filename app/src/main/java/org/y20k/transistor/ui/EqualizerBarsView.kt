/*
 * EqualizerBarsView.kt
 * Implements the EqualizerBarsView class
 * An EqualizerBarsView is the real time audio visualizer of the full screen player:
 * one bar per frequency band (bass on the left, treble on the right), moving with the music
 * that is heard right now (see AudioSpectrum)
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.y20k.transistor.audio.AudioSpectrum


/*
 * EqualizerBarsView class
 */
class EqualizerBarsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): View(context, attrs, defStyleAttr) {


    /* Main class variables */
    private val barCount: Int = AudioSpectrum.BAND_COUNT
    private val restingLevel: Float = 0.06f
    private val levels: FloatArray = FloatArray(barCount) { restingLevel }
    private val peaks: FloatArray = FloatArray(barCount) { restingLevel }
    private val barPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val peakPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect: RectF = RectF()
    private var isPlaying: Boolean = false
    private var isViewer: Boolean = false

    init {
        val mainColor: Int = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        barPaint.color = mainColor
        peakPaint.color = ColorUtils.setAlphaComponent(mainColor, 0x8C)
    }


    /* Starts or stops the visualizer */
    fun setPlaying(playing: Boolean) {
        if (playing == isPlaying) return
        isPlaying = playing
        postInvalidateOnAnimation()
    }


    /* Overrides onVisibilityAggregated from View - the audio is only analysed while the visualizer can be seen */
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        setViewer(isVisible)
        if (isVisible) postInvalidateOnAnimation()
    }


    /* Overrides onDetachedFromWindow from View */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setViewer(false)
    }


    /* Overrides onDraw from View */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // bands of the audio heard right now - nothing new for a moment (paused, buffering): bars fall back
        val bands: FloatArray? = if (isPlaying) AudioSpectrum.currentBands(MAX_BAND_AGE_MILLIS) else null
        var stillMoving = false
        val slotWidth: Float = width.toFloat() / barCount
        val barWidth: Float = slotWidth * 0.6f
        val radius: Float = barWidth / 2f
        val peakHeight: Float = (barWidth / 2.5f).coerceAtLeast(2f)
        for (i in 0 until barCount) {
            val target: Float = if (bands != null) restingLevel + bands[i] * (1f - restingLevel) else restingLevel
            // rise quickly, fall a bit slower - like the needle of a VU meter
            levels[i] += (target - levels[i]) * (if (target > levels[i]) 0.6f else 0.18f)
            // small peak marker that floats above the bar and sinks slowly
            peaks[i] = if (levels[i] >= peaks[i]) levels[i] else (peaks[i] - 0.012f).coerceAtLeast(levels[i])
            if (kotlin.math.abs(target - levels[i]) > 0.003f || peaks[i] - levels[i] > 0.003f) stillMoving = true
            // bars grow from the bottom
            val left: Float = i * slotWidth + (slotWidth - barWidth) / 2f
            val usableHeight: Float = height - peakHeight * 2f
            val barHeight: Float = (usableHeight * levels[i]).coerceAtLeast(barWidth)
            barRect.set(left, height - barHeight, left + barWidth, height.toFloat())
            canvas.drawRoundRect(barRect, radius, radius, barPaint)
            if (peaks[i] > restingLevel + 0.03f) {
                val peakBottom: Float = height - usableHeight * peaks[i] - peakHeight
                barRect.set(left, peakBottom - peakHeight, left + barWidth, peakBottom)
                canvas.drawRoundRect(barRect, peakHeight / 2f, peakHeight / 2f, peakPaint)
            }
        }
        if ((isPlaying && isViewer) || stillMoving) postInvalidateOnAnimation()
    }


    /* Registers / unregisters this view as a viewer of the audio spectrum */
    private fun setViewer(viewer: Boolean) {
        if (viewer == isViewer) return
        isViewer = viewer
        if (viewer) AudioSpectrum.addViewer() else AudioSpectrum.removeViewer()
    }


    /* Companion object */
    companion object {
        private const val MAX_BAND_AGE_MILLIS: Long = 250L
    }

}
