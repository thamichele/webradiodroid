/*
 * RadioBrowserSearch.kt
 * Implements the RadioBrowserSearch class
 * A RadioBrowserSearch performs searches on the radio-browser.info database
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.search

import android.content.Context
import android.util.Log
import com.android.volley.AuthFailureError
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.RetryPolicy
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.y20k.transistor.Keys
import org.y20k.transistor.helpers.NetworkHelper
import org.y20k.transistor.helpers.PreferencesHelper


/*
 * RadioBrowserSearch class
 */
class RadioBrowserSearch(private var radioBrowserSearchListener: RadioBrowserSearchListener) {

    /* Interface used to send back search results */
    interface RadioBrowserSearchListener {
        fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        }
    }


    /* Define log tag */
    private val TAG: String = RadioBrowserSearch::class.java.simpleName


    /* Main class variables */
    private var radioBrowserApi: String
    private lateinit var requestQueue: RequestQueue


    /* Init constructor */
    init {
        // get address of radio-browser.info api and update it in background
        radioBrowserApi = PreferencesHelper.loadRadioBrowserApiAddress()
        updateRadioBrowserApi()
    }


    /* Searches station(s) on radio-browser.info */
    fun searchStation(context: Context, query: String, searchType: Int) {
        Log.v(TAG, "Search - Querying $radioBrowserApi for: $query")

        // create queue and request
        requestQueue = Volley.newRequestQueue(context)
        val requestUrl: String
        when (searchType) {
            // CASE: single station search - by radio browser UUID
            Keys.SEARCH_TYPE_BY_UUID -> requestUrl = "https://${radioBrowserApi}/json/stations/byuuid/${query}"
            // CASE: multiple results search by search term
            else -> requestUrl = "https://${radioBrowserApi}/json/stations/search?name=${query.replace(" ", "+")}"
        }

        // request data from request URL
        val stringRequest = object: JsonArrayRequest(Method.GET, requestUrl, null, responseListener, errorListener) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params = HashMap<String, String>()
                params["User-Agent"] = Keys.USER_AGENT
                return params
            }
        }

        // override retry policy
        stringRequest.retryPolicy = object : RetryPolicy {
            override fun getCurrentTimeout(): Int {
                return 30000
            }
            override fun getCurrentRetryCount(): Int {
                return 30000
            }
            @Throws(VolleyError::class)
            override fun retry(error: VolleyError) {
                Log.w(TAG, "Error: $error")
            }
        }

        // add to RequestQueue.
        requestQueue.add(stringRequest)
    }


    fun stopSearchRequest() {
        if (this::requestQueue.isInitialized) {
            requestQueue.stop()
        }
    }


    /* Converts search result JSON string */
    private fun createRadioBrowserResult(result: String): Array<RadioBrowserResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<RadioBrowserResult>::class.java)
    }


    /* Updates the address of the radio-browser.info api */
    private fun updateRadioBrowserApi() {
        CoroutineScope(IO).launch {
            radioBrowserApi = NetworkHelper.getRadioBrowserServer()
        }
    }


    /* Listens for (positive) server responses to search requests */
    private val responseListener: Response.Listener<JSONArray> = Response.Listener<JSONArray> { response ->
        if (response != null) {
            radioBrowserSearchListener.onRadioBrowserSearchResults(createRadioBrowserResult(response.toString()))
        }
    }

    /* Listens for error response from server */
    private val errorListener: Response.ErrorListener = Response.ErrorListener { error ->
        Log.w(TAG, "Error: $error")
    }

}