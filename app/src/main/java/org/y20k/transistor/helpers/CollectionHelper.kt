/*
 * CollectionHelper.kt
 * Implements the CollectionHelper object
 * A CollectionHelper provides helper methods for the collection of stations
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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.y20k.transistor.Keys
import org.y20k.transistor.R
import org.y20k.transistor.core.Collection
import org.y20k.transistor.core.Station
import org.y20k.transistor.search.RadioBrowserResult
import java.io.File
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale


/*
 * CollectionHelper object
 */
object CollectionHelper {

    /* Define log tag */
    private val TAG: String = CollectionHelper::class.java.simpleName


    /* Checks if station is already in collection */
    fun isNewStation(collection: Collection, station: Station): Boolean {
        collection.stations.forEach {
            if (it.getStreamUri() == station.getStreamUri()) return false
        }
        return true
    }


    /* Checks if station is already in collection */
    fun isNewStation(collection: Collection, remoteStationLocation: String): Boolean {
        collection.stations.forEach {
            if (it.remoteStationLocation == remoteStationLocation) return false
        }
        return true
    }


    /* Checks if enough time passed since last update */
    fun hasEnoughTimePassedSinceLastUpdate(): Boolean {
        val lastUpdate: Date = PreferencesHelper.loadLastUpdateCollection()
        val currentDate: Date = Calendar.getInstance().time
        return currentDate.time - lastUpdate.time  > Keys.MINIMUM_TIME_BETWEEN_UPDATES
    }


    /* Checks if a newer collection of radio stations is available on storage */
    fun isNewerCollectionAvailable(date: Date): Boolean {
        var newerCollectionAvailable = false
        val modificationDate: Date = PreferencesHelper.loadCollectionModificationDate()
        if (modificationDate.after(date) || date == Keys.DEFAULT_DATE) {
            newerCollectionAvailable = true
        }
        return newerCollectionAvailable
    }


    /* Creates station from previously downloaded playlist file */
    fun createStationFromPlaylistFile(context: Context, localFileUri: Uri, remoteFileLocation: String): Station {
        // read station playlist
        val station: Station = FileHelper.readStationPlaylist(context.contentResolver.openInputStream(localFileUri))
        station.modificationDate = GregorianCalendar.getInstance().time
        if (station.name.isEmpty()) {
            // construct name from file name - strips file extension
            station.name = FileHelper.getFileName(context, localFileUri).substringBeforeLast(".")
        }
        station.remoteStationLocation = remoteFileLocation
        station.remoteImageLocation = CollectionHelper.getFaviconAddress(remoteFileLocation)
        return station
    }


    /* Updates radio station in collection */
    fun updateStation(context: Context, collection: Collection, station: Station): Collection {
        var updatedCollection: Collection = collection

        // CASE: Update station retrieved from radio browser
        if (station.radioBrowserStationUuid.isNotEmpty()) {
            updatedCollection.stations.forEach { it ->
                if (it.radioBrowserStationUuid.equals(station.radioBrowserStationUuid)) {
                    it.modificationDate = GregorianCalendar.getInstance().time
                    // update station in collection with values from new station
                    it.streamUris[it.stream] = station.getStreamUri()
                    it.streamContent = station.streamContent
                    it.remoteImageLocation = station.remoteImageLocation
                    it.remoteStationLocation = station.remoteStationLocation
                    it.homepage = station.homepage
                    // update name - if not changed previously by user
                    if (!it.nameManuallySet) it.name = station.name
                    // re-download station image - if new URL and not changed previously by user
                    if (!it.imageManuallySet && it.remoteImageLocation != station.remoteImageLocation) DownloadHelper.updateStationImage(context, it)
                }
            }
            // sort and save collection
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
        }

        // CASE: Update station retrieved via playlist
        else if (station.remoteStationLocation.isNotEmpty()) {
            updatedCollection.stations.forEach { it ->
                if (it.remoteStationLocation.equals(station.remoteStationLocation)) {
                    it.modificationDate = GregorianCalendar.getInstance().time
                    // update stream uri, mime type and station image url
                    it.streamUris[it.stream] = station.getStreamUri()
                    it.streamContent = station.streamContent
                    it.remoteImageLocation = station.remoteImageLocation
                    // update name - if not changed previously by user
                    if (!it.nameManuallySet) it.name = station.name
                    // re-download station image - if not changed previously by user
                    if (!it.imageManuallySet) DownloadHelper.updateStationImage(context, it)
                }
            }
            // sort and save collection
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
        }

        return updatedCollection
    }


    /* Adds new radio station to collection */
    fun addStation(context: Context, collection: Collection, newStation: Station): Collection {
        // check validity
        if (!newStation.isValid()) {
            Toast.makeText(context, R.string.toast_message_station_not_valid, Toast.LENGTH_LONG).show()
            return collection
        }
        // duplicate check
        else if (!isNewStation(collection, newStation)) {
            // update station
            Toast.makeText(context, R.string.toast_message_station_duplicate, Toast.LENGTH_LONG).show()
            return collection
        }
        // all clear -> add station
        else {
            var updatedCollection: Collection = collection
            val updatedStationList: MutableList<Station> = collection.stations.toMutableList()
            // add station
            updatedStationList.add(newStation)
            updatedCollection.stations = updatedStationList
            // sort and save collection
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
            // download station image
            DownloadHelper.updateStationImage(context, newStation)
            // return updated collection
            return updatedCollection
        }
    }


    /* Sets station image - determines station by remote image file location */
    fun setStationImageWithStationUuid(context: Context, collection: Collection, tempImageFileUri: String, stationUuid: String, imageManuallySet: Boolean = false) {
        CoroutineScope(IO).launch {
           collection.stations.forEach { station ->
                // find station by uuid
                if (station.uuid == stationUuid) {
                    station.modificationDate = GregorianCalendar.getInstance().time
                    station.smallImage = FileHelper.saveStationImage(context, station, tempImageFileUri, Keys.SIZE_STATION_IMAGE_SMALL, Keys.STATION_SMALL_IMAGE_FILE).toString()
                    station.image = FileHelper.saveStationImage(context, station, tempImageFileUri, Keys.SIZE_STATION_IMAGE, Keys.STATION_IMAGE_FILE).toString()
                    station.imageColor = UiHelper.getMainColor(context, tempImageFileUri)
                    station.imageManuallySet = imageManuallySet
                }
            }
            // save collection
            withContext(Main) {
                saveCollection(context, collection)
            }
        }
    }


    /* Clears an image folder for a given station */
    fun clearImagesFolder(context: Context, station: Station) {
        // clear image folder
        val imagesFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, station.uuid))
        FileHelper.clearFolder(imagesFolder, 0)
    }


    /* Deletes Images of a given station */
    fun deleteStationImages(context: Context, station: Station) {
        val imagesFolder: File = File(context.getExternalFilesDir(""), FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, station.uuid))
        FileHelper.clearFolder(imagesFolder, 0, true)
    }


    /* Get station from collection for given UUID */
    fun getStation(collection: Collection, stationUuid: String): Station {
        collection.stations.forEach { station ->
                if (station.uuid == stationUuid) {
                    return station
                }
        }
        // fallback: return first station
        if (collection.stations.isNotEmpty()) {
            return collection.stations.first()
        } else {
            return Station()
        }
    }


    /* Get station from collection for given Stream Uri */
    fun getStationWithStreamUri(collection: Collection, streamUri: String): Station {
        collection.stations.forEach { station ->
            if (station.getStreamUri() == streamUri) {
                return station
            }
        }
        // fallback: return first station
        if (collection.stations.isNotEmpty()) {
            return collection.stations.first()
        } else {
            return Station()
        }
    }


    /* Converts a collection to a list of media items */
    fun getStationsAsMediaItems(context: Context, collection: Collection): MutableList<MediaItem> {
        val stations: MutableList<MediaItem> = mutableListOf()
        collection.stations.forEach {station ->
            stations.add(buildMediaItem(context, station))
        }
        return stations
    }


    /* Gets MediaIem for next station within collection */
    fun getNextMediaItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        val currentStationPosition: Int = getStationPosition(collection, stationUuid)
        if (collection.stations.isEmpty() || currentStationPosition == -1) {
            return buildMediaItem(context, Station())
        } else if (currentStationPosition < collection.stations.size -1) {
            return buildMediaItem(context, collection.stations[currentStationPosition + 1])
        } else {
            return buildMediaItem(context, collection.stations.first())
        }
    }


    /* Gets MediaIem for previous station within collection */
    fun getPreviousMediaItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        val currentStationPosition: Int = getStationPosition(collection, stationUuid)
        if (collection.stations.isEmpty() || currentStationPosition == -1) {
            return buildMediaItem(context, Station())
        } else if (currentStationPosition > 0) {
            return buildMediaItem(context, collection.stations[currentStationPosition - 1])
        } else {
            return buildMediaItem(context, collection.stations.last())
        }
    }


    /* Get the position from collection for given UUID */
    fun getStationPosition(collection: Collection, stationUuid: String): Int {
        collection.stations.forEachIndexed { stationId, station ->
            if (station.uuid == stationUuid) {
                return stationId
            }
        }
        return -1
    }


    /* Get the position from collection for given radioBrowserStationUuid */
    fun getStationPositionFromRadioBrowserStationUuid(collection: Collection, radioBrowserStationUuid: String): Int {
        collection.stations.forEachIndexed { stationId, station ->
            if (station.radioBrowserStationUuid == radioBrowserStationUuid) {
                return stationId
            }
        }
        return -1
    }


    /* Get name of station from collection for given UUID */
    fun getStationName(collection: Collection, stationUuid: String): String {
        collection.stations.forEach { station ->
            if (station.uuid == stationUuid) {
                return station.name
            }
        }
        return String()
    }


    /* Creates a MediaItem with MediaMetadata for a single radio station - used to prepare player */
    fun buildMediaItem(context: Context, station: Station): MediaItem {
        // todo implement HLS MediaItems
        // put uri in RequestMetadata - credit: https://stackoverflow.com/a/70103460
        val requestMetadata = MediaItem.RequestMetadata.Builder().apply {
            setMediaUri(station.getStreamUri().toUri())
        }.build()
        // build MediaMetadata
        val mediaMetadata = MediaMetadata.Builder().apply {
            // setTitle(station.name) // now playing metadata will be inserted as title by the androidx/media library
            setSubtitle(station.name)
            setAlbumTitle(station.name)
            setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            setIsBrowsable(false)
            setIsPlayable(true)
            /* check for "file://" prevents a crash when an old backup was restored */
            if (station.image.isNotEmpty() && station.image.startsWith("file://") && station.image.toUri().toFile().exists()) {
                setArtworkData(station.image.toUri().toFile().readBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else {
                setArtworkData(UiHelper.getDefaultStationImageAsByteArray(context), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            // keep original artwork URI for being included in Cast metadata
            val extras = Bundle()
            extras.putString(Keys.KEY_ORIGINAL_ARTWORK_URI, station.remoteImageLocation)
            setExtras(extras)
        }.build()
        // build MediaItem and return it
        return MediaItem.Builder().apply {
            setMediaId(station.uuid)
            setRequestMetadata(requestMetadata)
            setMediaMetadata(mediaMetadata)
            setMimeType(station.getMediaType())
            setUri(station.getStreamUri().toUri())
        }.build()
    }


    /* Creates the Root media item for the browser */
    fun getRootItem(): MediaItem {
        return MediaItem.Builder().apply {
            setMediaId(Keys.MEDIA_BROWSER_ROOT)
            setMediaMetadata(
                MediaMetadata.Builder(). apply {
                    setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    setIsBrowsable(true)
                    setIsPlayable(false)
                }.build())
        }.build()
    }


    /* Returns the children stations under under root (simple media library structure: root > stations) */
    fun getChildren(context: Context, collection: Collection): List<MediaItem> {
        val mediaItems: MutableList<MediaItem> = mutableListOf()
        collection.stations.forEach { station ->
            mediaItems.add(buildMediaItem(context, station))
        }
        return mediaItems
    }


    /* Returns media item for given station id */
    fun getStationItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        return buildMediaItem(context, getStation(collection, stationUuid))
    }


    /* Returns media item for last played station */
    fun getRecent(context: Context, collection: Collection): MediaItem? {
        val lastPlayedPosition: Int = PreferencesHelper.loadLastPlayedStationPosition()
        val station: Station? = collection.stations.getOrNull(lastPlayedPosition)
        if (station == null) {
            Log.w(TAG, "Last played station not found.")
            return null
        } else {
            return buildMediaItem(context, station)
        }
    }


    /* Saves the playback state of a given station */
    fun savePlaybackState(context: Context, collection: Collection, stationUuid: String, isPlaying: Boolean): Collection {
        collection.stations.forEach { it ->
            // reset playback state everywhere
            it.isPlaying = false
            // set given playback state at this station
            if (it.uuid == stationUuid) {
                it.isPlaying = isPlaying
            }
        }
        // save collection and store modification date
        collection.modificationDate = saveCollection(context, collection)
        return collection
    }


    /* Saves collection of radio stations */
    fun saveCollection(context: Context, collection: Collection, async: Boolean = true): Date {
        Log.v(TAG, "Saving collection of radio stations to storage. Async = ${async}. Size = ${collection.stations.size}")
        // get modification date
        val date: Date = Calendar.getInstance().time
        collection.modificationDate = date
        // store current station position
        val position: Int = getStationPosition(collection, PreferencesHelper.loadLastPlayedStationUuid())
        PreferencesHelper.saveCurrentStationPosition(position)
        // save collection to storage
        when (async) {
            true -> {
                CoroutineScope(IO).launch {
                    // save collection in background
                    FileHelper.saveCollection(context, collection, date)
                    // broadcast collection update
                    sendCollectionBroadcast(context, date)
                }
            }
            false -> {
                // save collection
                FileHelper.saveCollection(context, collection, date)
                // broadcast collection update
                sendCollectionBroadcast(context, date)
            }
        }
        // return modification date
        return date
    }


    /* Creates station from playlist URLs and stream address URLs */
    fun createStationsFromUrl(query: String, lastCheckedAddress: String = String()): List<Station> {
        val stationList: MutableList<Station> = mutableListOf()
        val contentType: String = NetworkHelper.detectContentType(query).type.lowercase(Locale.getDefault())
        // CASE: M3U playlist detected
        if (Keys.MIME_TYPES_M3U.contains(contentType)) {
            val lines: List<String> = NetworkHelper.downloadPlaylist(query)
            stationList.addAll(readM3uPlaylistContent(lines))
        }
        // CASE: PLS playlist detected
        else if (Keys.MIME_TYPES_PLS.contains(contentType)) {
            val lines: List<String> = NetworkHelper.downloadPlaylist(query)
            stationList.addAll(readPlsPlaylistContent(lines))
        }
        // CASE: stream address detected
        else if (Keys.MIME_TYPES_MPEG.contains(contentType) or
            Keys.MIME_TYPES_OGG.contains(contentType) or
            Keys.MIME_TYPES_AAC.contains(contentType) or
            Keys.MIME_TYPES_HLS.contains(contentType)) {
            // create station and add to collection
            val station: Station = Station(name = query, streamUris = mutableListOf(query), streamContent = contentType, modificationDate = GregorianCalendar.getInstance().time)
            if (lastCheckedAddress != query) {
                stationList.add(station)
            }
        }
        return stationList
    }


    /* Creates station from URI pointing to a local file */
    fun createStationListFromContentUri(context: Context, contentUri: Uri): List<Station> {
        val stationList: MutableList<Station> = mutableListOf()
        val fileType: String = FileHelper.getContentType(context, contentUri)
        if (Keys.MIME_TYPES_M3U.contains(fileType)) {
            val playlist = FileHelper.readTextFileFromContentUri(context, contentUri)
            stationList.addAll(readM3uPlaylistContent(playlist))
        }
        // CASE: PLS playlist detected
        else if (Keys.MIME_TYPES_PLS.contains(fileType)) {
            val playlist = FileHelper.readTextFileFromContentUri(context, contentUri)
            stationList.addAll(readPlsPlaylistContent(playlist))
        }
        return stationList
    }


    /* Reads a m3u playlist and returns a list of stations */
    private fun readM3uPlaylistContent(playlist: List<String>): List<Station> {
        val stations: MutableList<Station> = mutableListOf()
        var name: String = String()
        var streamUri: String
        var contentType: String

        playlist.forEach { line ->
            // get name of station
            if (line.startsWith("#EXTINF:")) {
                name = line.substringAfter(",").trim()
            }
            // get stream uri and check mime type
            else if (line.isNotBlank() && !line.startsWith("#")) {
                streamUri = line.trim()
                // use the stream address as the name if no name is specified
                if (name.isEmpty()) {
                    name = streamUri
                }
                contentType = NetworkHelper.detectContentType(streamUri).type.lowercase(Locale.getDefault())
                // store station in list if mime type is supported
                if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
                    val station = Station(name = name, streamUris = mutableListOf(streamUri), streamContent = contentType, modificationDate = GregorianCalendar.getInstance().time)
                    stations.add(station)
                }
                // reset name for the next station - useful if playlist does not provide name(s)
                name = String()
            }
        }
        return stations
    }


    /* Reads a pls playlist and returns a list of stations */
    private fun readPlsPlaylistContent(playlist: List<String>): List<Station> {
        val stations: MutableList<Station> = mutableListOf()
        var name: String = String()
        var streamUri: String
        var contentType: String

        playlist.forEachIndexed { index, line ->
            // get stream uri and check mime type
            if (line.startsWith("File")) {
                streamUri = line.substringAfter("=").trim()
                contentType = NetworkHelper.detectContentType(streamUri).type.lowercase(Locale.getDefault())
                if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
                    // look for the matching station name
                    val number: String = line.substring(4 /* File */, line.indexOf("="))
                    val lineBeforeIndex: Int = index - 1
                    val lineAfterIndex: Int = index + 1
                    // first: check the line before
                    if (lineBeforeIndex >= 0) {
                        val lineBefore: String = playlist[lineBeforeIndex]
                        if (lineBefore.startsWith("Title$number")) {
                            name = lineBefore.substringAfter("=").trim()
                        }
                    }
                    // then: check the line after
                    if (name.isEmpty() && lineAfterIndex < playlist.size) {
                        val lineAfter: String = playlist[lineAfterIndex]
                        if (lineAfter.startsWith("Title$number")) {
                            name = lineAfter.substringAfter("=").trim()
                        }
                    }
                    // fallback: use stream uri as name
                    if (name.isEmpty()) {
                        name = streamUri
                    }
                    // add station
                    val station = Station(name = name, streamUris = mutableListOf(streamUri), streamContent = contentType, modificationDate = GregorianCalendar.getInstance().time)
                    stations.add(station)
                }
            }
        }
        return stations
    }


    /* Create M3U string from collection of stations */
    fun createM3uString(collection: Collection): String {
        val m3uString = StringBuilder()
        /* Extended M3U Format
        #EXTM3U
        #EXTINF:-1,My Cool Stream
        http://www.site.com:8000/listen.pls
         */

        // add opening tag
        m3uString.append("#EXTM3U")
        m3uString.append("\n")

        // add name and stream address
        collection.stations.forEach { station ->
            m3uString.append("\n")
            m3uString.append("#EXTINF:-1,")
            m3uString.append(station.name)
            m3uString.append("\n")
            m3uString.append(station.getStreamUri())
            m3uString.append("\n")
        }

        return m3uString.toString()
    }


    /* Sends a broadcast that the radio station collection has changed */
    fun sendCollectionBroadcast(context: Context, modificationDate: Date) {
        Log.v(TAG, "Broadcasting that collection has changed.")
        val collectionChangedIntent = Intent()
        collectionChangedIntent.action = Keys.ACTION_COLLECTION_CHANGED
        collectionChangedIntent.putExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, modificationDate.time)
        LocalBroadcastManager.getInstance(context).sendBroadcast(collectionChangedIntent)
    }


//    /* Creates MediaMetadata for a single station - used in media session*/
//    fun buildStationMediaMetadata(context: Context, station: Station, metadata: String): MediaMetadataCompat {
//        return MediaMetadataCompat.Builder().apply {
//            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.name)
//            putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata)
//            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, context.getString(R.string.app_name))
//            putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, station.getStreamUri())
//            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN))
//            //putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, station.image)
//        }.build()
//    }
//
//
//    /* Creates MediaItem for a station - used by collection provider */
//    fun buildStationMediaMetaItem(context: Context, station: Station): MediaBrowserCompat.MediaItem {
//        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
//        mediaDescriptionBuilder.setMediaId(station.uuid)
//        mediaDescriptionBuilder.setTitle(station.name)
//        mediaDescriptionBuilder.setIconBitmap(ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN))
//        // mediaDescriptionBuilder.setIconUri(station.image.toUri())
//        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
//    }
//
//
//    /* Creates description for a station - used in MediaSessionConnector */
//    fun buildStationMediaDescription(context: Context, station: Station, metadata: String): MediaDescriptionCompat {
//        val coverBitmap: Bitmap = ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN)
//        val extras: Bundle = Bundle()
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverBitmap)
//        return MediaDescriptionCompat.Builder().apply {
//            setMediaId(station.uuid)
//            setIconBitmap(coverBitmap)
//            setIconUri(station.image.toUri())
//            setTitle(metadata)
//            setSubtitle(station.name)
//            setExtras(extras)
//        }.build()
//    }


    /* Creates a fallback station - stupid hack for Android Auto compatibility :-/ */
    fun createFallbackStation(): Station {
        return Station(name = "KCSB", streamUris = mutableListOf("http://live.kcsb.org:80/KCSB_128"), streamContent = Keys.MIME_TYPE_MPEG)
    }


    /* Sorts radio stations by name */
    fun sortCollection(collection: Collection): Collection {
        collection.stations = collection.stations.sortedWith(compareByDescending<Station> { it.starred }.thenBy { it.name.lowercase(Locale.getDefault()) }) as MutableList<Station>
        return collection
    }


    /* Get favicon address */
    fun getFaviconAddress(urlString: String): String {
        var faviconAddress: String = String()
        try {
            var host: String = URL(urlString).host
            if (!host.startsWith("www")) {
                val index = host.indexOf(".")
                host = "www" + host.substring(index)
            }
            faviconAddress = "http://$host/favicon.ico"
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get base URL from $urlString.\n$e ")
        }
        return faviconAddress
    }


    /* Converts search result JSON string */
    fun createRadioBrowserResult(result: String): Array<RadioBrowserResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<RadioBrowserResult>::class.java)
    }

}
