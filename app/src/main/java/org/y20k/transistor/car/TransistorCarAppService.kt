/*
 * TransistorCarAppService.kt
 * Implements the TransistorCarAppService class
 * TransistorCarAppService is the entry point for the templated Android Auto experience (Car App Library)
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator


/*
 * TransistorCarAppService class
 */
class TransistorCarAppService: CarAppService() {


    /* Overrides createHostValidator from CarAppService */
    override fun createHostValidator(): HostValidator {
        // debug builds accept every host (needed for the Desktop Head Unit), release builds only the known Android Auto hosts
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }


    /* Overrides onCreateSession from CarAppService */
    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return TransistorCarSession()
    }


}
