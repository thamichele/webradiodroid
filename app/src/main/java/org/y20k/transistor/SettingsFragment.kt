/*
 * SettingsFragment.kt
 * Implements the SettingsFragment fragment
 * A SettingsFragment displays the user accessible settings of the app
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.contains
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.y20k.transistor.dialogs.ErrorDialog
import org.y20k.transistor.dialogs.YesNoDialog
import org.y20k.transistor.helpers.AppThemeHelper
import org.y20k.transistor.helpers.BackupHelper
import org.y20k.transistor.helpers.FileHelper
import org.y20k.transistor.helpers.NetworkHelper
import org.y20k.transistor.helpers.PreferencesHelper
import org.y20k.transistor.helpers.UiHelper


/*
 * SettingsFragment class
 */
class SettingsFragment: PreferenceFragmentCompat(), YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = SettingsFragment::class.java.simpleName


    /* Interfaces for list events */
    interface SettingsListDragListener {
        fun onSettingsListDragStateChanged(newState: Int)
    }


    /* Main class variables */
    private var settingsListDragListener: SettingsListDragListener? = null
    private lateinit var toolbar: MaterialToolbar


    /* Overrides onViewCreated from PreferenceFragmentCompat */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // use material shared axis transitions
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, /* forward= */ false)

        // set up toolbar
        toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val navController = findNavController()
        toolbar.setupWithNavController(navController)

        // set up list drag listener
        setUpDragListener()

        // set up edge to edge display
        setupEdgeToEdge(view)
    }


    /* Overrides onCreatePreferences from PreferenceFragmentCompat */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)


        // set up "App Theme" preference
        val preferenceThemeSelection: ListPreference = ListPreference(activity as Context)
        preferenceThemeSelection.title = getString(R.string.pref_theme_selection_title)
        preferenceThemeSelection.setIcon(R.drawable.ic_smartphone_24dp)
        preferenceThemeSelection.key = Keys.PREF_THEME_SELECTION
        preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${AppThemeHelper.getCurrentTheme(activity as Context)}"
        preferenceThemeSelection.entries = arrayOf(getString(R.string.pref_theme_selection_mode_device_default), getString(R.string.pref_theme_selection_mode_light), getString(R.string.pref_theme_selection_mode_dark))
        preferenceThemeSelection.entryValues = arrayOf(Keys.STATE_THEME_FOLLOW_SYSTEM, Keys.STATE_THEME_LIGHT_MODE, Keys.STATE_THEME_DARK_MODE)
        preferenceThemeSelection.setDefaultValue(Keys.STATE_THEME_FOLLOW_SYSTEM)
        preferenceThemeSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${preference.entries[index]}"
                listView.findNavController().navigate(R.id.player_destination)
                true
            } else {
                false
            }
        }

        // set up "Dynamic Colors" preference (Android 12+ only)
        val preferenceEnableDynamicColors: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEnableDynamicColors.title = getString(R.string.pref_dynamic_colors_title)
        preferenceEnableDynamicColors.setIcon(R.drawable.ic_color_palette_24dp)
        preferenceEnableDynamicColors.key = Keys.PREF_DYNAMIC_COLORS
        preferenceEnableDynamicColors.summaryOn = getString(R.string.pref_dynamic_colors_summary_enabled)
        preferenceEnableDynamicColors.summaryOff = getString(R.string.pref_dynamic_colors_summary_disabled)
        preferenceEnableDynamicColors.isVisible = DynamicColors.isDynamicColorAvailable()
        preferenceEnableDynamicColors.setDefaultValue(PreferencesHelper.loadDynamicColorsEnabled())
        preferenceEnableDynamicColors.setOnPreferenceChangeListener { preference, newValue ->
            val dynamicColorsEnabled = newValue as Boolean
            PreferencesHelper.saveDynamicColorsEnabled(dynamicColorsEnabled) // todo check if necessary
            AppThemeHelper.setupDynamicColors(requireActivity().application)
            requireActivity().recreate()
            listView.findNavController().navigate(R.id.player_destination)
            true
        }

        // set up "User Interface Transparency Effect" preference
        val preferenceUserInterfaceTransparencyEffect: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceUserInterfaceTransparencyEffect.title = getString(R.string.pref_user_interface_transparency_effect_title)
        preferenceUserInterfaceTransparencyEffect.setIcon(R.drawable.ic_transparency_24dp) // or another appropriate icon
        preferenceUserInterfaceTransparencyEffect.key = Keys.PREF_USER_INTERFACE_TRANSPARENCY_EFFECT
        preferenceUserInterfaceTransparencyEffect.summaryOn = getString(R.string.pref_user_interface_transparency_effect_summary_enabled)
        preferenceUserInterfaceTransparencyEffect.summaryOff = getString(R.string.pref_user_interface_transparency_effect_summary_disabled)
        preferenceUserInterfaceTransparencyEffect.setDefaultValue(PreferencesHelper.loadUserInterfaceTransparencyEffect())

        // set up "Tap Anywhere" preference
        val preferenceEnableTapAnywherePlayback: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEnableTapAnywherePlayback.title = getString(R.string.pref_tap_anywhere_playback_title)
        preferenceEnableTapAnywherePlayback.setIcon(R.drawable.ic_play_circle_outline_24dp)
        preferenceEnableTapAnywherePlayback.key = Keys.PREF_TAP_ANYWHERE_PLAYBACK
        preferenceEnableTapAnywherePlayback.summaryOn = getString(R.string.pref_tap_anywhere_playback_summary_enabled)
        preferenceEnableTapAnywherePlayback.summaryOff = getString(R.string.pref_tap_anywhere_playback_summary_disabled)
        preferenceEnableTapAnywherePlayback.setDefaultValue(PreferencesHelper.loadTapAnyWherePlayback())

        // set up "Update Station Images" preference
        val preferenceUpdateStationImages: Preference = Preference(activity as Context)
        preferenceUpdateStationImages.title = getString(R.string.pref_update_station_images_title)
        preferenceUpdateStationImages.setIcon(R.drawable.ic_image_24dp)
        preferenceUpdateStationImages.summary = getString(R.string.pref_update_station_images_summary)
        preferenceUpdateStationImages.setOnPreferenceClickListener {
            // show dialog
            YesNoDialog(this).show(context = activity as Context, type = Keys.DIALOG_UPDATE_STATION_IMAGES, message = R.string.dialog_yes_no_message_update_station_images, yesButton = R.string.dialog_yes_no_positive_button_update_covers)
            true
        }

//        // set up "Update Stations" preference
//        val preferenceUpdateCollection: Preference = Preference(activity as Context)
//        preferenceUpdateCollection.title = getString(R.string.pref_update_collection_title)
//        preferenceUpdateCollection.setIcon(R.drawable.ic_refresh_24dp)
//        preferenceUpdateCollection.summary = getString(R.string.pref_update_collection_summary)
//        preferenceUpdateCollection.setOnPreferenceClickListener {
//            // show dialog
//            YesNoDialog(this).show(context = activity as Context, type = Keys.DIALOG_UPDATE_COLLECTION, message = R.string.dialog_yes_no_message_update_collection, yesButton = R.string.dialog_yes_no_positive_button_update_collection)
//            true
//        }

        // set up "M3U Export" preference
        val preferenceM3uExport: Preference = Preference(activity as Context)
        preferenceM3uExport.title = getString(R.string.pref_m3u_export_title)
        preferenceM3uExport.setIcon(R.drawable.ic_playlist_24dp)
        preferenceM3uExport.summary = getString(R.string.pref_m3u_export_summary)
        preferenceM3uExport.setOnPreferenceClickListener {
            openSaveM3uDialog()
            true
        }

        // set up "Backup Stations" preference
        val preferenceBackupCollection: Preference = Preference(activity as Context)
        preferenceBackupCollection.title = getString(R.string.pref_backup_title)
        preferenceBackupCollection.setIcon(R.drawable.ic_save_24dp)
        preferenceBackupCollection.summary = getString(R.string.pref_backup_summary)
        preferenceBackupCollection.setOnPreferenceClickListener {
            openBackupCollectionDialog()
            true
        }

        // set up "Restore Stations" preference
        val preferenceRestoreCollection: Preference = Preference(activity as Context)
        preferenceRestoreCollection.title = getString(R.string.pref_restore_title)
        preferenceRestoreCollection.setIcon(R.drawable.ic_restore_24dp)
        preferenceRestoreCollection.summary = getString(R.string.pref_restore_summary)
        preferenceRestoreCollection.setOnPreferenceClickListener {
            openRestoreCollectionDialog()
            true
        }

        // set up "Buffer Size" preference
        val preferenceBufferSize: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceBufferSize.title = getString(R.string.pref_buffer_size_title)
        preferenceBufferSize.setIcon(R.drawable.ic_network_check_24dp)
        preferenceBufferSize.key = Keys.PREF_LARGE_BUFFER_SIZE
        preferenceBufferSize.summaryOn = getString(R.string.pref_buffer_size_summary_enabled)
        preferenceBufferSize.summaryOff = getString(R.string.pref_buffer_size_summary_disabled)
        preferenceBufferSize.setDefaultValue(PreferencesHelper.loadLargeBufferSize())

        // set up "Edit Stream Address" preference
        val preferenceEnableEditingStreamUri: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEnableEditingStreamUri.title = getString(R.string.pref_edit_station_stream_title)
        preferenceEnableEditingStreamUri.setIcon(R.drawable.ic_music_note_24dp)
        preferenceEnableEditingStreamUri.key = Keys.PREF_EDIT_STREAMS_URIS
        preferenceEnableEditingStreamUri.summaryOn = getString(R.string.pref_edit_station_stream_summary_enabled)
        preferenceEnableEditingStreamUri.summaryOff = getString(R.string.pref_edit_station_stream_summary_disabled)
        preferenceEnableEditingStreamUri.setDefaultValue(PreferencesHelper.loadEditStreamUrisEnabled())

        // set up "Edit Stations" preference
        val preferenceEnableEditingGeneral: SwitchPreferenceCompat = SwitchPreferenceCompat(activity as Context)
        preferenceEnableEditingGeneral.title = getString(R.string.pref_edit_station_title)
        preferenceEnableEditingGeneral.setIcon(R.drawable.ic_edit_24dp)
        preferenceEnableEditingGeneral.key = Keys.PREF_EDIT_STATIONS
        preferenceEnableEditingGeneral.summaryOn = getString(R.string.pref_edit_station_summary_enabled)
        preferenceEnableEditingGeneral.summaryOff = getString(R.string.pref_edit_station_summary_disabled)
        preferenceEnableEditingGeneral.setDefaultValue(PreferencesHelper.loadEditStationsEnabled())
        preferenceEnableEditingGeneral.setOnPreferenceChangeListener { preference, newValue ->
            when (newValue) {
                true -> {
                    preferenceEnableEditingStreamUri.isEnabled = true
                }
                false -> {
                    preferenceEnableEditingStreamUri.isEnabled = false
                    preferenceEnableEditingStreamUri.isChecked = false
                }
            }
            true
        }

        // set up "App Version" preference
        val preferenceAppVersion: Preference = Preference(context)
        preferenceAppVersion.title = getString(R.string.pref_app_version_title)
        preferenceAppVersion.setIcon(R.drawable.ic_info_24dp)
        preferenceAppVersion.summary = "${getString(R.string.pref_app_version_summary)} ${BuildConfig.VERSION_NAME} (${getString(R.string.app_version_name)})"
        preferenceAppVersion.setOnPreferenceClickListener {
            // copy to clipboard
            val clip: ClipData = ClipData.newPlainText("simple text", preferenceAppVersion.summary)
            val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // since API 33 (TIRAMISU) the OS displays its own notification when content is copied to the clipboard
                Toast.makeText(activity as Context, R.string.toast_message_copied_to_clipboard, Toast.LENGTH_LONG).show()
            }
            true
        }


        // set preference categories
        val preferenceCategoryGeneral: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryGeneral.title = getString(R.string.pref_general_title)
        preferenceCategoryGeneral.contains(preferenceThemeSelection)
        preferenceCategoryGeneral.contains(preferenceEnableDynamicColors)
        preferenceCategoryGeneral.contains(preferenceUserInterfaceTransparencyEffect)
        preferenceCategoryGeneral.contains(preferenceEnableTapAnywherePlayback)

        val preferenceCategoryMaintenance: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryMaintenance.title = getString(R.string.pref_maintenance_title)
        preferenceCategoryMaintenance.contains(preferenceUpdateStationImages)
//        preferenceCategoryMaintenance.contains(preferenceUpdateCollection)
        preferenceCategoryMaintenance.contains(preferenceM3uExport)
        preferenceCategoryMaintenance.contains(preferenceBackupCollection)
        preferenceCategoryMaintenance.contains(preferenceRestoreCollection)

        val preferenceCategoryAdvanced: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryAdvanced.title = getString(R.string.pref_advanced_title)
        preferenceCategoryAdvanced.contains(preferenceBufferSize)
        preferenceCategoryAdvanced.contains(preferenceEnableEditingGeneral)
        preferenceCategoryAdvanced.contains(preferenceEnableEditingStreamUri)

        val preferenceCategoryAbout: PreferenceCategory = PreferenceCategory(context)
        preferenceCategoryAbout.title = getString(R.string.pref_about_title)
        preferenceCategoryAbout.contains(preferenceAppVersion)


        // setup preference screen
        screen.addPreference(preferenceCategoryGeneral)
        screen.addPreference(preferenceThemeSelection)
        screen.addPreference(preferenceEnableDynamicColors)
        screen.addPreference(preferenceUserInterfaceTransparencyEffect)
        screen.addPreference(preferenceEnableTapAnywherePlayback)
        screen.addPreference(preferenceCategoryMaintenance)
        screen.addPreference(preferenceUpdateStationImages)
//        screen.addPreference(preferenceUpdateCollection)
        screen.addPreference(preferenceM3uExport)
        screen.addPreference(preferenceBackupCollection)
        screen.addPreference(preferenceRestoreCollection)
        screen.addPreference(preferenceCategoryAdvanced)
        screen.addPreference(preferenceBufferSize)
        screen.addPreference(preferenceEnableEditingGeneral)
        screen.addPreference(preferenceEnableEditingStreamUri)

        screen.addPreference(preferenceCategoryAbout)
        screen.addPreference(preferenceAppVersion)
        preferenceScreen = screen
    }



    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString)

        when (type) {

            Keys.DIALOG_UPDATE_STATION_IMAGES -> {
                if (dialogResult) {
                    // user tapped: refresh station images
                    updateStationImages()
                }
            }

            Keys.DIALOG_UPDATE_COLLECTION -> {
                if (dialogResult) {
                    // user tapped update collection
                    updateCollection()
                }
            }

        }

    }

    /* Sets the list drag listener */
    fun setListDragListener(listener: SettingsListDragListener) {
        settingsListDragListener = listener
    }


    /* Register the ActivityResultLauncher for the save m3u dialog */
    private val requestSaveM3uLauncher = registerForActivityResult(StartActivityForResult(), this::requestSaveM3uResult)


    /* Register the ActivityResultLauncher for the backup dialog */
    private val requestBackupCollectionLauncher = registerForActivityResult(StartActivityForResult(), this::requestBackupCollectionResult)


    /* Register the ActivityResultLauncher for the restore dialog */
    private val requestRestoreCollectionLauncher = registerForActivityResult(StartActivityForResult(), this::requestRestoreCollectionResult)


    /* Pass the activity result for the save m3u dialog */
    private fun requestSaveM3uResult(result: ActivityResult) {
        // save M3U file to result file location
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val sourceUri: Uri? = FileHelper.getM3ulUri(activity as Activity)
            val targetUri: Uri? = result.data?.data
            if (targetUri != null && sourceUri != null) {
                // copy file async (= fire & forget - no return value needed)
                CoroutineScope(IO).launch {
                    FileHelper.copyFile(activity as Context, sourceUri, targetUri, true)
                }
                Toast.makeText(activity as Context, R.string.toast_message_save_m3u, Toast.LENGTH_LONG).show()
            } else {
                Log.w(TAG, "M3U export failed.")
            }
        }
    }


    /* Pass the activity result for the backup collection dialog */
    private fun requestBackupCollectionResult(result: ActivityResult) {
        // save station backup file to result file location
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val targetUri: Uri? = result.data?.data
            if (targetUri != null) {
                BackupHelper.backup(activity as Context, targetUri)
            } else {
                Log.w(TAG, "Station backup failed.")
            }
        }
    }


    /* Pass the activity result for the restore collection dialog */
    private fun requestRestoreCollectionResult(result: ActivityResult) {
        // save station backup file to result file location
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val sourceUri: Uri? = result.data?.data
            if (sourceUri != null) {
                // open and import OPML in player fragment
                val bundle: Bundle = bundleOf(
                    Keys.ARG_RESTORE_COLLECTION to "$sourceUri"
                )
                this.findNavController().navigate(R.id.player_destination, bundle)
            }
        }
    }


    /* Updates collection */
    private fun updateCollection() {
        if (NetworkHelper.isConnectedToNetwork(activity as Context)) {
            Toast.makeText(activity as Context, R.string.toast_message_updating_collection, Toast.LENGTH_LONG).show()
            // update collection in player screen
            val bundle: Bundle = bundleOf(Keys.ARG_UPDATE_COLLECTION to true)
            this.findNavController().navigate(R.id.player_destination, bundle)
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Updates station images */
    private fun updateStationImages() {
        if (NetworkHelper.isConnectedToNetwork(activity as Context)) {
            Toast.makeText(activity as Context, R.string.toast_message_updating_station_images, Toast.LENGTH_LONG).show()
            // update collection in player screen
            val bundle: Bundle = bundleOf(Keys.ARG_UPDATE_IMAGES to true)
            this.findNavController().navigate(R.id.player_destination, bundle)
        } else {
            ErrorDialog().show(activity as Context, R.string.dialog_error_title_no_network, R.string.dialog_error_message_no_network)
        }
    }


    /* Opens up a file picker to select the save location */
    private fun openSaveM3uDialog() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Keys.MIME_TYPE_M3U
            putExtra(Intent.EXTRA_TITLE, Keys.COLLECTION_M3U_FILE)
        }
        // file gets saved in the ActivityResult
        try {
            requestSaveM3uLauncher.launch(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to save M3U.\n$exception")
            Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
    }


    /* Opens up a file picker to select the backup location */
    private fun openBackupCollectionDialog() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Keys.MIME_TYPE_ZIP
            putExtra(Intent.EXTRA_TITLE, Keys.COLLECTION_BACKUP_FILE)
        }
        // file gets saved in the ActivityResult
        try {
            requestBackupCollectionLauncher.launch(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to save M3U.\n$exception")
            Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
    }


    /* Opens up a file picker to select the file containing the collection to be restored */
    private fun openRestoreCollectionDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, Keys.MIME_TYPES_ZIP)
        }
        // file gets saved in the ActivityResult
        try {
            requestRestoreCollectionLauncher.launch(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to open file picker for ZIP.\n$exception")
            // Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
    }


    /* Creates a listener that detects when the list of preferences is being dragged */
    private fun setUpDragListener() {
        // add scroll listener to detect when list is being dragged
        val preferencesList: RecyclerView = listView
        preferencesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                settingsListDragListener?.onSettingsListDragStateChanged(newState)
            }
        })
        // set up list drag listener
        val mainActivity = activity as? BaseMainActivity
        if (mainActivity != null) {
            setListDragListener(mainActivity.layout as SettingsListDragListener)
        }
    }


    /* Sets up margins/paddings for edge to edge view */
    private fun setupEdgeToEdge(view: View) {
        val preferencesList: RecyclerView = listView
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            // get measurements for status and navigation bar
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // apply measurements
            toolbar.updateLayoutParams<LinearLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            preferencesList.updatePadding(
                bottom = systemBars.bottom + ((Keys.PLAYER_HEIGHT + Keys.PLAYER_BOTTOM_MARGIN) * UiHelper.getDensityScalingFactor(requireContext())).toInt()
            )
            // return the insets
            insets
        }
    }

}
