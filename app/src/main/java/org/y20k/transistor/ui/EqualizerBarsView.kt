/*
 * EqualizerBarsView.kt
 * Implements the EqualizerBarsView class
 * An EqualizerBarsView draws animated "equalizer" bars while a station is playing (decorative, not a real spectrum)
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
import com.google.android.material.color.MaterialColors
import kotlin.random.Random


/*
 * EqualizerBarsView class
 */
class EqualizerBarsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0): View(context, attrs, defStyleAttr) {


    /* Main class variables */
    private val barCount: Int = 24
    private val restingLevel: Float = 0.08f
    private val levels: FloatArray = FloatArray(barCount) { restingLevel }
    private val targets: FloatArray = FloatArray(barCount) { restingLevel }
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@EqualizerBarsView, com.google.android.material.R.attr.colorPrimary)
    }
    private val barRect: RectF = RectF()
    private var isAnimating: Boolean = false
    private var lastTargetUpdate: Long = 0L


    /* Starts or stops the bar animation */
    fun setPlaying(playing: Boolean) {
        if (playing == isAnimating) return
        isAnimating = playing
        if (!playing) targets.fill(restingLevel)
        postInvalidateOnAnimation()
    }


    /* Overrides onDetachedFromWindow from View */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAnimating = false
    }


    /* Overrides onDraw from View */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now: Long = System.currentTimeMillis()
        // pick new random heights a few times per second - the middle bars a bit taller
        if (isAnimating && now - lastTargetUpdate > 140L) {
            lastTargetUpdate = now
            for (i in 0 until barCount) {
                val distanceFromCenter: Float = kotlin.math.abs(i - (barCount - 1) / 2f) / (barCount / 2f)
                val ceiling: Float = 1f - 0.45f * distanceFromCenter
                targets[i] = restingLevel + Random.nextFloat() * (ceiling - restingLevel)
            }
        }
        // ease every bar towards its target and draw it
        var stillMoving = false
        val slotWidth: Float = width.toFloat() / barCount
        val barWidth: Float = slotWidth * 0.55f
        val radius: Float = barWidth / 2f
        for (i in 0 until barCount) {
            levels[i] += (targets[i] - levels[i]) * 0.25f
            if (kotlin.math.abs(targets[i] - levels[i]) > 0.005f) stillMoving = true
            val barHeight: Float = (height * levels[i]).coerceAtLeast(barWidth)
            val left: Float = i * slotWidth + (slotWidth - barWidth) / 2f
            val top: Float = (height - barHeight) / 2f
            barRect.set(left, top, left + barWidth, top + barHeight)
            canvas.drawRoundRect(barRect, radius, radius, paint)
        }
        if (isAnimating || stillMoving) postInvalidateOnAnimation()
    }


}
