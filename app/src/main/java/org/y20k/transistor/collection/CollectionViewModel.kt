/*
 * CollectionViewModel.kt
 * Implements the CollectionViewModel class
 * A CollectionViewModel stores the collection of stations as live data
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.collection

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.transistor.Keys
import org.y20k.transistor.core.Collection
import org.y20k.transistor.helpers.FileHelper
import java.util.Date


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Define log tag */
    private val TAG: String = CollectionViewModel::class.java.simpleName


    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    private var modificationDateViewModel: Date = Date()
    private var collectionChangedReceiver: BroadcastReceiver


    /* Init constructor */
    init {
        // load collection
        loadCollection()
        // create and register collection changed receiver
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
    }


    /* Overrides onCleared */
    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(collectionChangedReceiver)
    }


    /* Creates the collectionChangedReceiver - handles Keys.ACTION_COLLECTION_CHANGED */
    private fun createCollectionChangedReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                    val date: Date = Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))
                    // check if reload is necessary
                    if (date.after(modificationDateViewModel)) {
                        Log.v(TAG, "CollectionViewModel - reload collection after broadcast received.")
                        loadCollection()
                    }
                }
            }
        }
    }


    /* Reads collection of radio stations from storage using GSON */
    private fun loadCollection() {
        Log.v(TAG, "Loading collection of stations from storage")
        CoroutineScope(IO).launch {
            // load collection on background thread
            val collection: Collection = FileHelper.readCollection(getApplication())
            // get updated modification date
            modificationDateViewModel = collection.modificationDate
            withContext(Main) {
                // update collection view model
                collectionLiveData.value = collection
            }
        }
    }

}
