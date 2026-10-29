/*
 * SleepTimerDialog.kt
 * Implements the SleepTimerDialog class
 * A SleepTimerDialog lets the user pick the duration of the sleep timer (bottom sheet)
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import org.y20k.transistor.R
import org.y20k.transistor.helpers.PreferencesHelper


/*
 * SleepTimerDialog class
 */
class SleepTimerDialog(private val listener: SleepTimerDialogListener) {

    /* Interface used to communicate back to activity */
    interface SleepTimerDialogListener {
        fun onSleepTimerDurationSelected(durationMinutes: Int)
        fun onSleepTimerStopSelected()
    }


    /* Main class variables */
    private val presets: IntArray = intArrayOf(15, 30, 45, 60, 90, 120)


    /* Shows the sleep timer bottom sheet */
    fun show(context: Context, isRunning: Boolean) {
        val dialog = BottomSheetDialog(context)
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_sleep_timer, null)
        val valueView: TextView = view.findViewById(R.id.sleep_timer_dialog_value)
        val slider: Slider = view.findViewById(R.id.sleep_timer_dialog_slider)
        val presetGroup: ChipGroup = view.findViewById(R.id.sleep_timer_dialog_presets)
        val startButton: MaterialButton = view.findViewById(R.id.sleep_timer_dialog_start_button)
        val stopButton: MaterialButton = view.findViewById(R.id.sleep_timer_dialog_stop_button)

        // quick choices
        presets.forEach { minutes ->
            val chip = Chip(context).apply {
                text = context.getString(R.string.sleep_timer_dialog_minutes, minutes)
                isCheckable = false
                setOnClickListener { slider.value = minutes.toFloat() }
            }
            presetGroup.addView(chip)
        }

        // last used duration
        val lastDuration: Int = PreferencesHelper.loadSleepTimerDuration().coerceIn(slider.valueFrom.toInt(), slider.valueTo.toInt())
        slider.value = (lastDuration / 5 * 5).toFloat().coerceAtLeast(slider.valueFrom)
        valueView.text = context.getString(R.string.sleep_timer_dialog_minutes, slider.value.toInt())
        slider.addOnChangeListener { _, value, _ ->
            valueView.text = context.getString(R.string.sleep_timer_dialog_minutes, value.toInt())
        }

        // buttons
        startButton.setText(if (isRunning) R.string.sleep_timer_dialog_update else R.string.sleep_timer_dialog_start)
        startButton.setOnClickListener {
            val minutes: Int = slider.value.toInt()
            PreferencesHelper.saveSleepTimerDuration(minutes)
            listener.onSleepTimerDurationSelected(minutes)
            dialog.dismiss()
        }
        stopButton.isVisible = isRunning
        stopButton.setOnClickListener {
            listener.onSleepTimerStopSelected()
            dialog.dismiss()
        }

        dialog.setContentView(view)
        // show the whole sheet right away, also in landscape
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

}
