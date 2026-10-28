/*
 * Transistor.kt
 * Implements the Transistor class
 * Transistor is the base Application class that sets up day and night theme
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor

import android.app.Application
import android.util.Log
import org.y20k.transistor.helpers.AppThemeHelper
import org.y20k.transistor.helpers.PreferencesHelper
import org.y20k.transistor.helpers.PreferencesHelper.initPreferences


/**
 * Transistor.class
 */
class Transistor: Application () {

    /* Define log tag */
    private val TAG: String = Transistor::class.java.simpleName


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "Transistor application started.")
        initPreferences()

        // apply dynamic colors, if user enabled it
        AppThemeHelper.setupDynamicColors(this)

        // set Dark / Light theme state
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
    }



    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
        Log.v(TAG, "Transistor application terminated.")
    }

}
