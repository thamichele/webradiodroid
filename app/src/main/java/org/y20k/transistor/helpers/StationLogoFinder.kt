/*
 * StationLogoFinder.kt
 * Implements the StationLogoFinder object
 * StationLogoFinder looks for a logo on the web for stations that have no image
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.y20k.transistor.Keys
import org.y20k.transistor.core.Collection
import org.y20k.transistor.core.Station
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder


/*
 * StationLogoFinder object
 */
object StationLogoFinder {


    /* Define log tag */
    private val TAG: String = StationLogoFinder::class.java.simpleName


    /* Main class variables */
    private const val MIN_LOGO_SIZE: Int = 64
    private const val MAX_LOGO_BYTES: Int = 2 * 1024 * 1024
    private var isRunning: Boolean = false


    /* Searches logos for all stations without an image - each station is searched only once */
    fun findMissingLogos(context: Context, collection: Collection) {
        if (isRunning) return
        val searched: Set<String> = PreferencesHelper.loadLogoSearchedStations()
        val stations: List<Station> = collection.stations.filter { it.image.isEmpty() && !it.imageManuallySet && it.uuid !in searched }
        if (stations.isEmpty()) return
        isRunning = true
        CoroutineScope(IO).launch {
            try {
                stations.forEach { station -> findLogo(context.applicationContext, station) }
            } finally {
                isRunning = false
            }
        }
    }


    /* Finds, downloads and sets the logo of one station */
    private fun findLogo(context: Context, station: Station) {
        val candidates: List<String> = try {
            findCandidates(station)
        } catch (e: Exception) {
            // probably offline: try again next time
            Log.w(TAG, "Logo search failed for ${station.name}: $e")
            return
        }
        PreferencesHelper.addLogoSearchedStation(station.uuid)
        candidates.forEach { candidate ->
            val logoFile: File = download(context, candidate, station.uuid) ?: return@forEach
            Log.i(TAG, "Logo found for ${station.name}: $candidate")
            // the collection may have changed meanwhile: work on a fresh copy
            val collection: Collection = FileHelper.readCollection(context)
            val target: Station = collection.stations.find { it.uuid == station.uuid } ?: return
            if (target.image.isNotEmpty() || target.imageManuallySet) return
            target.remoteImageLocation = candidate
            CollectionHelper.setStationImageWithStationUuid(context, collection, Uri.fromFile(logoFile).toString(), station.uuid)
            return
        }
        Log.i(TAG, "No logo found for ${station.name}")
    }


    /* Lists image addresses that may show the station's logo - best first */
    private fun findCandidates(station: Station): List<String> {
        val candidates: MutableList<String> = mutableListOf()
        // 1. radio-browser.info: stations with the same name and an icon
        val name: String = station.name.trim()
        candidates += searchRadioBrowser(name)
        // 2. shorter name - e.g. "Virgin Radio Metal" -> "Virgin Radio" (the brand usually comes first)
        val words: List<String> = name.split(Regex("\\s+"))
        if (words.size > 2) candidates += searchRadioBrowser(words.dropLast(1).joinToString(" "))
        // 3. the icon of the station's website
        val host: String? = runCatching { Uri.parse(station.homepage).host }.getOrNull()
        if (!host.isNullOrEmpty()) {
            candidates += "https://$host/apple-touch-icon.png"
        }
        return candidates.distinct()
    }


    /* Returns the icons of radio-browser.info stations whose name matches the query */
    private fun searchRadioBrowser(query: String): List<String> {
        if (query.length < 3) return emptyList()
        val server: String = PreferencesHelper.loadRadioBrowserApiAddress()
        val url = "https://$server/json/stations/search?name=${URLEncoder.encode(query, "UTF-8")}&hidebroken=true&order=clickcount&reverse=true&limit=30"
        val response: String = readText(url) ?: throw java.io.IOException("No response from $server")
        val results = JSONArray(response)
        val wanted: String = normalize(query)
        val exact: MutableList<String> = mutableListOf()
        val similar: MutableList<String> = mutableListOf()
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val favicon: String = result.optString("favicon").trim()
            if (!favicon.startsWith("http")) continue
            val resultName: String = normalize(result.optString("name"))
            when {
                resultName == wanted -> exact += favicon
                resultName.startsWith(wanted) -> similar += favicon
            }
        }
        return exact + similar
    }


    /* Downloads an image and keeps it only if it is a real picture of a useful size */
    private fun download(context: Context, address: String, stationUuid: String): File? {
        val connection: HttpURLConnection = openConnection(address) ?: return null
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes: ByteArray = connection.inputStream.use { it.readNBytesCompat(MAX_LOGO_BYTES + 1) }
            if (bytes.isEmpty() || bytes.size > MAX_LOGO_BYTES) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth < MIN_LOGO_SIZE || options.outHeight < MIN_LOGO_SIZE) return null
            val file = File(context.cacheDir, "logo-$stationUuid")
            file.writeBytes(bytes)
            return file
        } catch (e: Exception) {
            Log.w(TAG, "Unable to download $address: $e")
            return null
        } finally {
            connection.disconnect()
        }
    }


    /* Reads a web address as text */
    private fun readText(address: String): String? {
        val connection: HttpURLConnection = openConnection(address) ?: return null
        return try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            connection.disconnect()
        }
    }


    /* Opens a connection with WebRadioDroid's user agent */
    private fun openConnection(address: String): HttpURLConnection? {
        return try {
            (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Keys.USER_AGENT)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open $address: $e")
            null
        }
    }


    /* Lower case letters and digits only - "Virgin Radio-Metal!" -> "virginradiometal" */
    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }


    /* Reads at most the given number of bytes (InputStream.readNBytes needs API 33) */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buffer.size() < limit) {
            val read: Int = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }


}
