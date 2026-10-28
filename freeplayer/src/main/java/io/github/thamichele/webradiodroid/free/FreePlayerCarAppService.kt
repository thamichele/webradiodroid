/*
 * FreePlayerCarAppService.kt
 * Implements the FreePlayerCarAppService class
 * FreePlayerCarAppService is the entry point of WebRadioDroid Libero on Android Auto (runs as navigation app)
 *
 * This file is part of WebRadioDroid Libero
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package io.github.thamichele.webradiodroid.free

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator


/*
 * FreePlayerCarAppService class
 */
class FreePlayerCarAppService: CarAppService() {


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
        return FreePlayerSession()
    }


}
