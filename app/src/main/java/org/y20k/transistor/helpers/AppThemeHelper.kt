/*
 * AppThemeHelper.kt
 * Implements the AppThemeHelper object
 * A AppThemeHelper can set the different app themes: Light Mode, Dark Mode, Follow System
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.y20k.transistor.Keys
import org.y20k.transistor.R


/*
 * AppThemeHelper object
 */
object AppThemeHelper {

    /* Define log tag */
    private val TAG: String = AppThemeHelper::class.java.simpleName


    /* Sets app theme */
    fun setTheme(nightModeState: String) {
        when (nightModeState) {
            Keys.STATE_THEME_DARK_MODE -> {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                    // turn on dark mode
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    Log.i(TAG, "Dark Mode activated.")
                }
            }
            Keys.STATE_THEME_LIGHT_MODE -> {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
                    // turn on light mode
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    Log.i(TAG, "Theme: Light Mode activated.")
                }
            }
            Keys.STATE_THEME_FOLLOW_SYSTEM -> {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                    // turn on mode "follow system"
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    Log.i(TAG, "Theme: Follow System Mode activated.")
                }
            }
            else -> {
                // turn on mode "follow system"
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Log.i(TAG, "Theme: Follow System Mode activated.")
            }
        }
    }


    /* Return weather Night Mode is on, or not  */
    fun isDarkModeOn(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }


    /* Returns a readable String for currently selected App Theme */
    fun getCurrentTheme(context: Context): String {
        return when (PreferencesHelper.loadThemeSelection()) {
            Keys.STATE_THEME_LIGHT_MODE -> context.getString(R.string.pref_theme_selection_mode_light)
            Keys.STATE_THEME_DARK_MODE -> context.getString(R.string.pref_theme_selection_mode_dark)
            else -> context.getString(R.string.pref_theme_selection_mode_device_default)
        }
    }


    /* All main (accent) colors, in the order shown in the settings */
    val accentColors: List<String> = listOf(Keys.ACCENT_ARANCIO, Keys.ACCENT_VIOLA, Keys.ACCENT_ROSSO, Keys.ACCENT_GIALLO, Keys.ACCENT_BLU, Keys.ACCENT_VERDE)


    /* Returns the theme overlay of a main (accent) color */
    private fun accentOverlay(accent: String): Int {
        return when (accent) {
            Keys.ACCENT_VIOLA -> R.style.ThemeOverlay_WebRadioDroid_Accent_Viola
            Keys.ACCENT_ROSSO -> R.style.ThemeOverlay_WebRadioDroid_Accent_Rosso
            Keys.ACCENT_GIALLO -> R.style.ThemeOverlay_WebRadioDroid_Accent_Giallo
            Keys.ACCENT_BLU -> R.style.ThemeOverlay_WebRadioDroid_Accent_Blu
            Keys.ACCENT_VERDE -> R.style.ThemeOverlay_WebRadioDroid_Accent_Verde
            else -> R.style.ThemeOverlay_WebRadioDroid_Accent_Arancio
        }
    }


    /* Returns the readable names of the main (accent) colors */
    fun accentColorNames(context: Context): Array<String> {
        return accentColors.map { accent ->
            when (accent) {
                Keys.ACCENT_VIOLA -> context.getString(R.string.pref_accent_color_viola)
                Keys.ACCENT_ROSSO -> context.getString(R.string.pref_accent_color_rosso)
                Keys.ACCENT_GIALLO -> context.getString(R.string.pref_accent_color_giallo)
                Keys.ACCENT_BLU -> context.getString(R.string.pref_accent_color_blu)
                Keys.ACCENT_VERDE -> context.getString(R.string.pref_accent_color_verde)
                else -> context.getString(R.string.pref_accent_color_arancio)
            }
        }.toTypedArray()
    }


    /* Applies the chosen main (accent) color - call before super.onCreate(), skipped while Dynamic Colors are on */
    fun applyAccentColor(activity: Activity) {
        if (DynamicColors.isDynamicColorAvailable() && PreferencesHelper.loadDynamicColorsEnabled()) return
        activity.theme.applyStyle(accentOverlay(PreferencesHelper.loadAccentColor()), true)
    }


    /* Sets up Dynamic Colors based on user preferences */
    fun setupDynamicColors(application: Application) {
        val dynamicColorsEnabled = PreferencesHelper.loadDynamicColorsEnabled()
        val options = DynamicColorsOptions.Builder()
            .setOnAppliedCallback { activity ->
                if (!dynamicColorsEnabled) {
                    activity.setTheme(R.style.AppTheme)
                }
            }
            .setThemeOverlay(
                if (dynamicColorsEnabled) {
                    com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_DayNight
                }
                else {
                    R.style.AppTheme
                }
            )
            .build()
        DynamicColors.applyToActivitiesIfAvailable(application, options)
    }


    /* Displays the default status bar */
    private fun displayDefaultStatusBar(activity: Activity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = 0
    }


    /* Displays the light (inverted) status bar */
    private fun displayLightStatusBar(activity: Activity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }




}
