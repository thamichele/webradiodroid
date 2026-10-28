/*
 * MainFragmentLayoutHolder.kt
 * Implements the MainFragmentLayoutHolder class
 * A MainFragmentLayoutHolder hold references to the views used in MainFragment
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
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.y20k.transistor.Keys
import org.y20k.transistor.R
import org.y20k.transistor.helpers.PreferencesHelper
import org.y20k.transistor.helpers.UiHelper


/*
 * MainFragmentLayoutHolder class
 */
data class MainFragmentLayoutHolder(var rootView: View) {

    /* Define log tag */
    private val TAG: String = MainFragmentLayoutHolder::class.java.simpleName


    /* Interfaces for list events */
    interface StationListDragListener {
        fun onStationListDragStateChanged(newState: Int)
    }


    /* Main class variables */
    private lateinit var systemBars: Insets
    var recyclerView: RecyclerView
    val layoutManager: LinearLayoutManager
    private var onboardingLayout: ConstraintLayout
    private var onboardingQuoteViews: Group
    private var onboardingImportViews: Group
    private var stationListDragListener: StationListDragListener? = null


    /* Init block */
    init {
        // find views
        recyclerView = rootView.findViewById(R.id.station_list)
        onboardingLayout = rootView.findViewById(R.id.onboarding_layout)
        onboardingQuoteViews = rootView.findViewById(R.id.onboarding_quote_views)
        onboardingImportViews = rootView.findViewById(R.id.onboarding_import_views)

        // set up RecyclerView
        layoutManager = CustomLayoutManager(rootView.context)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()

//        // set initial focus on the station list (focus for keyboard navigation) // todo: remove, this seems not to be necessary
//        recyclerView.requestFocus()

        // add scroll listener to detect when list is being dragged
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                stationListDragListener?.onStationListDragStateChanged(newState)
            }
        })

        // set up edge to edge display
        setupEdgeToEdge()
    }
    
    /* Sets the list drag listener */
    fun setListDragListener(listener: StationListDragListener) {
        stationListDragListener = listener
    }


//    /* Toggle the Import Running indicator  */
//    fun toggleImportingStationViews() {
//        if (onboardingImportViews.visibility == View.INVISIBLE) {
//            onboardingImportViews.isVisible = true
//            onboardingQuoteViews.isVisible = false
//        } else {
//            onboardingImportViews.isVisible = false
//            onboardingQuoteViews.isVisible = true
//        }
//    }


    /* Toggles visibility of the onboarding screen */
    fun toggleOnboarding(collectionSize: Int): Boolean {
        if (collectionSize == 0 && PreferencesHelper.loadCollectionSize() <= 0) {
            onboardingLayout.isVisible = true
            return true
        } else {
            onboardingLayout.isGone = true
            return false
        }
    }


    /* Sets up margins/paddings for edge to edge view */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            // get measurements for status and navigation bar
            systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // update the list padding to position first item below status bar and have enough room at the bottom to show the player
            recyclerView.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom + ((Keys.PLAYER_HEIGHT + Keys.PLAYER_BOTTOM_MARGIN) * UiHelper.getDensityScalingFactor(rootView.context)).toInt()
            )
            // update the onboarding layout margin to position it below the status bar
            onboardingLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                topMargin = systemBars.top
            }
            // return the insets
            insets
        }
    }


    /*
     * Inner class: Custom LinearLayoutManager
     */
    private inner class CustomLayoutManager(context: Context): LinearLayoutManager(context, VERTICAL, false) {
        override fun supportsPredictiveItemAnimations(): Boolean {
            return true
        }
    }
    /*
     * End of inner class
     */


}
