/*
 * WorkerHelper.kt
 * Implements the WorkerHelper object
 * A WorkerHelper provides helper methods for starting work jobs
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers

import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.y20k.transistor.Keys
import java.util.UUID
import java.util.concurrent.TimeUnit


/*
 * WorkerHelper object
 */
object WorkerHelper {

    /* Define log tag */
    private val TAG: String = WorkerHelper::class.java.simpleName


    /* Schedules a DownloadWorker that triggers background updates of the collection periodically */
    fun schedulePeriodicUpdateWorker(): UUID {
        Log.v(TAG, "Starting / Updating periodic work: update collection")
        val requestData: Data = Data.Builder()
                .putInt(Keys.KEY_DOWNLOAD_WORK_REQUEST, Keys.REQUEST_UPDATE_COLLECTION)
                .build()
        val unmeteredConstraint = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
        val updateCollectionPeriodicWork = PeriodicWorkRequestBuilder<DownloadWorker>(Keys.UPDATE_REPEAT_INTERVAL, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                //.setConstraints(unmeteredConstraint)
                .setInputData(requestData)
                .build()
        WorkManager.getInstance().enqueueUniquePeriodicWork(Keys.NAME_PERIODIC_COLLECTION_UPDATE_WORK,  ExistingPeriodicWorkPolicy.REPLACE, updateCollectionPeriodicWork)
        return updateCollectionPeriodicWork.id
    }

}
