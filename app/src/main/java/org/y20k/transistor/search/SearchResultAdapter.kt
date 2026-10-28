/*
 * SearchResultAdapter.kt
 * Implements the SearchResultAdapter class
 * A SearchResultAdapter is a custom adapter providing search result views for a RecyclerView
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import org.y20k.transistor.R
import org.y20k.transistor.core.Station


/*
 * SearchResultAdapter class
 */
class SearchResultAdapter(private val listener: SearchResultAdapterListener, var searchResults: List<Station>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /* Define log tag */
    private val TAG: String = SearchResultAdapter::class.java.simpleName


    /* Main class variables */
    private var selectedPosition: Int = RecyclerView.NO_POSITION


    /* Listener Interface */
    interface SearchResultAdapterListener {
        fun onSearchResultTapped(result: Station)
    }


    init {
        setHasStableIds(true)
    }


    /* Overrides onCreateViewHolder from RecyclerView.Adapter */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.element_search_result, parent, false)
        return SearchResultViewHolder(v)
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemCount(): Int {
        return searchResults.size
    }


    /* Overrides getItemCount from RecyclerView.Adapter */
    override fun getItemId(position: Int): Long = position.toLong()


    /* Overrides onBindViewHolder from RecyclerView.Adapter */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // get reference to ViewHolder
        val searchResultViewHolder: SearchResultViewHolder = holder as SearchResultViewHolder
        val searchResult: Station = searchResults[position]
        // update text
        searchResultViewHolder.nameView.text = "${searchResult.name} (${searchResult.codec} - ${searchResult.bitrate} kbps)"
        searchResultViewHolder.streamView.text = searchResult.getStreamUri()
        // mark selected if necessary
        searchResultViewHolder.searchResultLayout.isSelected = selectedPosition == position
        // toggle text scrolling (marquee) if necessary
        searchResultViewHolder.nameView.isSelected = selectedPosition == position
        searchResultViewHolder.streamView.isSelected = selectedPosition == position
        // attach touch listener
        searchResultViewHolder.searchResultLayout.setOnClickListener {
            // move marked position
            notifyItemChanged(selectedPosition)
            selectedPosition = position
            notifyItemChanged(selectedPosition)
            // hand over station
            listener.onSearchResultTapped(searchResult)
        }
    }


    /* Resets the selected position */
    fun resetSelection(clearAdapter: Boolean) {
        val currentlySelected: Int = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (clearAdapter) {
            searchResults = listOf()
            notifyDataSetChanged()
        } else {
            notifyItemChanged(currentlySelected)
        }
    }



    /*
     * Inner class: ViewHolder for a radio station search result
     */
    private inner class SearchResultViewHolder (var searchResultLayout: View): RecyclerView.ViewHolder(searchResultLayout) {
        val nameView: MaterialTextView = searchResultLayout.findViewById(R.id.station_name)
        val streamView: MaterialTextView = searchResultLayout.findViewById(R.id.station_url)
    }
    /*
     * End of inner class
     */

}
