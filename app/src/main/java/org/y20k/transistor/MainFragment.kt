/*
 * MainFragment.kt
 * Implements the MainFragment class
 * MainFragment is the fragment that hosts Transistor's list of stations
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.transistor.collection.CollectionAdapter
import org.y20k.transistor.collection.CollectionViewModel
import org.y20k.transistor.core.Collection
import org.y20k.transistor.core.Station
import org.y20k.transistor.dialogs.AddStationDialog
import org.y20k.transistor.dialogs.FindStationDialog
import org.y20k.transistor.dialogs.YesNoDialog
import org.y20k.transistor.helpers.BackupHelper
import org.y20k.transistor.helpers.CollectionHelper
import org.y20k.transistor.helpers.DownloadHelper
import org.y20k.transistor.helpers.FileHelper
import org.y20k.transistor.helpers.NetworkHelper
import org.y20k.transistor.helpers.PreferencesHelper
import org.y20k.transistor.helpers.UiHelper
import org.y20k.transistor.helpers.UpdateHelper
import org.y20k.transistor.ui.MainFragmentLayoutHolder


/*
 * MainFragment class
 */
class MainFragment: Fragment(),
    FindStationDialog.FindStationDialogListener,
    AddStationDialog.AddStationDialogListener,
    CollectionAdapter.CollectionAdapterListener,
    YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = MainFragment::class.java.simpleName


    /* Main class variables */
    lateinit var layout: MainFragmentLayoutHolder
    private lateinit var collectionViewModel: CollectionViewModel
    private lateinit var collectionAdapter: CollectionAdapter
    private var collection: Collection = Collection()
    private var listLayoutState: Parcelable? = null
    private var tempStationUuid: String = String()


    /* Overrides onCreate from Fragment */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // handle back tap/gesture
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isEnabled) {
                        isEnabled = false
                        activity?.onBackPressed()
                    }
                }
            })

        // Set exit transition for MainFragment
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)

        // create view model and observe changes in collection view model
        collectionViewModel = ViewModelProvider(this)[CollectionViewModel::class.java]

        // create collection adapter
        collectionAdapter = CollectionAdapter(requireContext(), this as CollectionAdapter.CollectionAdapterListener, this as YesNoDialog.YesNoDialogListener)
    }


    /* Overrides onCreate from Fragment*/
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // find views and set them up
        val rootView: View = inflater.inflate(R.layout.fragment_main, container, false);
        layout = MainFragmentLayoutHolder(rootView)
        initializeViews()
        // hide action bar
        (activity as AppCompatActivity).supportActionBar?.hide()
        return rootView
    }


    /* Overrides onSaveInstanceState from Fragment */
    override fun onSaveInstanceState(outState: Bundle) {
        if (this::layout.isInitialized) {
            // save current state of station list
            listLayoutState = layout.layoutManager.onSaveInstanceState()
            outState.putParcelable(Keys.KEY_SAVE_INSTANCE_STATE_STATION_LIST, listLayoutState)
        }
        // always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(outState)
    }


    /* Overrides onRestoreInstanceState from Activity */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // always call the superclass so it can restore the view hierarchy
        super.onActivityCreated(savedInstanceState)
        // restore state of station list
        listLayoutState = savedInstanceState?.getParcelable(Keys.KEY_SAVE_INSTANCE_STATE_STATION_LIST)
    }


    /* Overrides onResume from Fragment */
    override fun onResume() {
        super.onResume()
        // update station list state
        updateStationListState()
        // begin looking for changes in collection
        observeCollectionViewModel()
        // handle navigation arguments
        handleNavigationArguments()
        // handle start intent
        handleStartIntent()
        // set up list drag listener
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            layout.setListDragListener(mainActivity.layout as MainFragmentLayoutHolder.StationListDragListener)
            // layout.setListDragListener(mainActivity as SettingsFragment.SettingsListDragListener)
        }
    }


    /* Register the ActivityResultLauncher */
    private val requestLoadImageLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestLoadImageResult)


    /* Pass the activity result */
    private fun requestLoadImageResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                CollectionHelper.setStationImageWithStationUuid(requireContext(), collection, imageUri.toString(), tempStationUuid, imageManuallySet = true)
                tempStationUuid = String()
            }
        }
    }


    /* Register permission launcher */
    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // permission granted
                pickImage()
            } else {
                // permission denied
                Toast.makeText(requireContext(), R.string.toast_message_error_missing_storage_permission, Toast.LENGTH_LONG).show()
            }
        }


    /* Overrides onFindStationDialog from FindStationDialog */
    override fun onFindStationDialog(station: Station) {
        if (station.streamContent.isNotEmpty() && station.streamContent != Keys.MIME_TYPE_UNSUPPORTED) {
            // add station and save collection
            collection = CollectionHelper.addStation(requireContext(), collection, station)
        } else {
            // detect content type on background thread
            CoroutineScope(IO).launch {
                val contentType: NetworkHelper.ContentType = NetworkHelper.detectContentType(station.getStreamUri())
                // set content type
                station.streamContent = contentType.type
                // add station and save collection
                withContext(Main) {
                    CollectionHelper.addStation(requireContext(), collection, station) // todo check if should be moved to adapter (like removeStation)
                }
            }
        }
    }


    /* Overrides onAddStationDialog from AddDialog */
    override fun onAddStationDialog(station: Station) {
        if (station.streamContent.isNotEmpty() && station.streamContent != Keys.MIME_TYPE_UNSUPPORTED) {
            // add station and save collection
            CollectionHelper.addStation(requireContext(), collection, station) // todo check if should be moved to adapter (like removeStation)
        }
    }


    /* Overrides onPlayButtonTapped from CollectionAdapterListener */
    override fun onPlayButtonTapped(stationPosition: Int) {
        // Get the BaseMainActivity instance
        val mainActivity = activity as? BaseMainActivity
        // Forward the play request to the activity
        mainActivity?.onPlayButtonTapped(stationPosition)
    }


    /* Overrides onAddNewButtonTapped from CollectionAdapterListener */
    override fun onAddNewButtonTapped() {
        FindStationDialog(activity as Activity, this as FindStationDialog.FindStationDialogListener).show()
    }


    /* Overrides onChangeImageButtonTapped from CollectionAdapterListener */
    override fun onChangeImageButtonTapped(stationUuid: String) {
        tempStationUuid = stationUuid
        pickImage()
    }

    /* Overrides onStationCardTapped from CollectionAdapterListener */
    override fun onStationCardTapped() {
        val mainActivity = activity as? BaseMainActivity
        mainActivity?.layout?.hidePlayerExtendedViewsIfVisible()
    }


    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString)
        when (type) {
            // handle result of remove dialog
            Keys.DIALOG_REMOVE_STATION -> {
                when (dialogResult) {
                    // user tapped remove station
                    true -> {
                        collectionAdapter.removeStation(requireContext(), payload)
                        // Notify the activity that a station was removed
                        val mainActivity = activity as? BaseMainActivity
                        if (mainActivity != null) {
                            val position = PreferencesHelper.loadLastPlayedStationPosition()
                            if (position == payload) {
                                // The currently playing station was removed
                                mainActivity.onPlayButtonTapped(position)
                            }
                        }
                    }
                    // user tapped cancel
                    false -> collectionAdapter.notifyItemChanged(payload)
                }
            }
            // handle result from the restore collection dialog
            Keys.DIALOG_RESTORE_COLLECTION -> {
                when (dialogResult) {
                    // user tapped restore
                    true -> BackupHelper.restore(requireContext(), payloadString.toUri())
                    // user tapped cancel
                    false -> { /* do nothing */
                    }
                }
            }
        }
    }


    /* Sets up views and connects tap listeners - first run */
    private fun initializeViews() {
        // set adapter data source
        layout.recyclerView.adapter = collectionAdapter

        // enable swipe to delete
        val swipeToDeleteHandler = object : UiHelper.SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // ask user and delete station if confirmed
                collectionAdapter.showDeleteStationDialog(this@MainFragment as YesNoDialog.YesNoDialogListener, viewHolder.adapterPosition)
            }
        }
        val swipeToDeleteItemTouchHelper = ItemTouchHelper(swipeToDeleteHandler)
        swipeToDeleteItemTouchHelper.attachToRecyclerView(layout.recyclerView)

        // enable swipe to mark starred
        val swipeToMarkStarredHandler =
            object : UiHelper.SwipeToMarkStarredCallback(requireContext()) {
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // mark card starred
                    val adapterPosition: Int = viewHolder.adapterPosition
                    collectionAdapter.toggleStarredStation(requireContext(), adapterPosition)
                }
            }
        val swipeToMarkStarredItemTouchHelper = ItemTouchHelper(swipeToMarkStarredHandler)
        swipeToMarkStarredItemTouchHelper.attachToRecyclerView(layout.recyclerView)
    }


    /* Sets up state of list station list */
    private fun updateStationListState() {
        if (listLayoutState != null) {
            layout.layoutManager.onRestoreInstanceState(listLayoutState)
        }
    }


    /* Check permissions and start image picker */
    private fun pickImage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // permission READ_EXTERNAL_STORAGE not granted - request permission
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // permission READ_EXTERNAL_STORAGE granted - get system picker for images
            val pickImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            try {
                requestLoadImageLauncher.launch(pickImageIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to select image. Probably no image picker available.")
                Toast.makeText(context, R.string.toast_message_no_image_picker, Toast.LENGTH_LONG).show()
            }
        }
    }


    /* Handles this activity's start intent */
    private fun handleStartIntent() {
        if ((activity as Activity).intent.action != null) {
            when ((activity as Activity).intent.action) {
                Intent.ACTION_VIEW -> handleViewIntent()
                Intent.ACTION_SEND -> handleSendIntent()
                // Keys.ACTION_START is handled in BaseMainActivity
            }
        }
    }


    /* Handles ACTION_SEND request to add Station */
    private fun handleSendIntent() {
        // get intent data
        val intentUriString: String? = (activity as Activity).intent.getStringExtra(Intent.EXTRA_TEXT)
        if (intentUriString.isNullOrEmpty()) {
            return
        }
        // attempt to parse given string into a Uri
        val intentUri: Uri? = try {
            intentUriString.toUri()
        } catch (e: Exception) {
            Log.e(TAG, "Could not parse the intent string as a URI: $intentUriString")
            null
        }
        // clear intent action to prevent double calls
        (activity as Activity).intent.action = ""
        // add station from intent, if it ends with a supported file extension
        if (intentUri != null) {
            val lowercaseIntentUriString: String = intentUri.toString().lowercase()
            val isPlaylistUri: Boolean = Keys.FILE_EXTENSIONS_PLAYLIST.any { extension ->
                lowercaseIntentUriString.endsWith(extension) ||
                lowercaseIntentUriString.contains("$extension?") ||
                lowercaseIntentUriString.contains("$extension#") }
            if (isPlaylistUri) {
                addStationFromIntent(intentUri)
            }
        }
    }


    /* Handles ACTION_VIEW request to add Station */
    private fun handleViewIntent() {
        // get intent data
        val intentUri: Uri? = (activity as Activity).intent.data
        // clear intent action to prevent double calls
        (activity as Activity).intent.action = ""
        if (intentUri != null) {
            addStationFromIntent(intentUri)
        }
    }


    /* Handles add station requests that come in via intent */
    private fun addStationFromIntent(intentUri: Uri) {
        CoroutineScope(IO).launch {
            // get station list from intent source
            val stationList: MutableList<Station> = mutableListOf()
            val scheme: String = intentUri.scheme ?: String()
            // CASE: intent is a web link
            if (scheme.startsWith("http")) {
                Log.i(TAG, "Transistor was started to handle a web link.")
                stationList.addAll(CollectionHelper.createStationsFromUrl(intentUri.toString()))
            }
            // CASE: intent is a local file
            else if (scheme.startsWith("content")) {
                Log.i(TAG, "Transistor was started to handle a local audio playlist.")
                stationList.addAll(CollectionHelper.createStationListFromContentUri(requireContext(), intentUri))
            }
            withContext(Main) {
                if (stationList.isNotEmpty()) {
                    AddStationDialog(activity as Activity, stationList, this@MainFragment as AddStationDialog.AddStationDialogListener).show()
                } else {
                    // invalid address
                    Toast.makeText(context, R.string.toast_message_station_not_valid, Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    /* Observe view model of collection of stations */
    private fun observeCollectionViewModel() {
        collectionViewModel.collectionLiveData.observe(this, Observer<Collection> { updatedCollection ->
                // get BaseMainActivity instance
                val mainActivity = activity as? BaseMainActivity

                // update collection
                collection = updatedCollection
                
                // toggle the onboarding view if necessary and export the collection
                layout.toggleOnboarding(collection.stations.size)
                if (collection.stations.size > 0) {
                    CoroutineScope(IO).launch {
                        FileHelper.backupCollectionAsM3u(requireContext(), collection)
                    }
                }
                
                // notify the activity about the updated collection
                if (mainActivity != null && collection.stations.isNotEmpty()) {
                    val currentStation: Station = collection.stations[PreferencesHelper.loadLastPlayedStationPosition().coerceIn(0, collection.stations.size - 1)]
                    mainActivity.updatePlayerViews(currentStation)
                    mainActivity.layout.showPlayer()
                } else if (mainActivity != null && collection.stations.isEmpty()) {
                    mainActivity.layout.hidePlayer()
                }
            })
    }


    /* Handles arguments handed over by navigation (from SettingsFragment) */
    private fun handleNavigationArguments() {
        // get arguments
        val updateCollection: Boolean = arguments?.getBoolean(Keys.ARG_UPDATE_COLLECTION, false) ?: false
        val updateStationImages: Boolean = arguments?.getBoolean(Keys.ARG_UPDATE_IMAGES, false) ?: false
        val restoreCollectionFileString: String? = arguments?.getString(Keys.ARG_RESTORE_COLLECTION)

        if (updateCollection) {
            arguments?.putBoolean(Keys.ARG_UPDATE_COLLECTION, false)
            val updateHelper: UpdateHelper = UpdateHelper(requireContext(), collectionAdapter, collection)
            updateHelper.updateCollection()
        }
        if (updateStationImages) {
            arguments?.putBoolean(Keys.ARG_UPDATE_IMAGES, false)
            DownloadHelper.updateStationImages(requireContext())
        }
        if (!restoreCollectionFileString.isNullOrEmpty()) {
            arguments?.putString(Keys.ARG_RESTORE_COLLECTION, null)
            when (collection.stations.isNotEmpty()) {
                true -> {
                    YesNoDialog(this as YesNoDialog.YesNoDialogListener).show(context = requireContext(), type = Keys.DIALOG_RESTORE_COLLECTION, messageString = getString(R.string.dialog_restore_collection_replace_existing), payloadString = restoreCollectionFileString)
                }

                false -> {
                    BackupHelper.restore(requireContext(), restoreCollectionFileString.toUri())
                }
            }
        }
    }

}