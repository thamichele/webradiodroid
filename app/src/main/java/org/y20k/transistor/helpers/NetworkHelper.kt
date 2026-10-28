/*
 * NetworkHelper.kt
 * Implements the NetworkHelper object
 * A NetworkHelper provides helper methods for network related operations
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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.util.Log
import org.y20k.transistor.Keys
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.Random


/*
 * NetworkHelper object
 */
object NetworkHelper {

    /* Define log tag */
    private val TAG: String = NetworkHelper::class.java.simpleName


    /* Data class: holder for content type information */
    data class ContentType(var type: String = String(), var charset: String = String())


    /* Checks if the active network connection is over Wifi */
    fun isConnectedToWifi(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a Wifi connection is active
                result = capabilities.hasTransport(TRANSPORT_WIFI)
            }
        }
        return result
    }


    /* Checks if the active network connection is over Cellular */
    fun isConnectedToCellular(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a cellular connection is active
                result = capabilities.hasTransport(TRANSPORT_CELLULAR)
            }
        }
        return result
    }


    /* Checks if the active network connection is over VPN */
    fun isConnectedToVpn(context: Context): Boolean {
        var result: Boolean = false
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        if (activeNetwork != null) {
            val capabilities: NetworkCapabilities? = connMgr.getNetworkCapabilities(activeNetwork)
            if (capabilities != null) {
                // check if a VPN connection is active
                result = capabilities.hasTransport(TRANSPORT_VPN)
            }
        }
        return result
    }


    /* Checks if the active network connection is connected to any network */
    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        return activeNetwork != null
    }


    /* Detects content type (mime type) from given URL string - async using coroutine - use only on separate threat */
    fun detectContentType(urlString: String): ContentType {
        Log.v(TAG, "Determining content type - Thread: ${Thread.currentThread().name}")
        val contentType: ContentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
        val connection: HttpURLConnection? = createConnection(urlString)
        if (connection != null) {
            val contentTypeHeader: String = connection.contentType ?: String()
            Log.v(TAG, "Raw content type header: $contentTypeHeader")
            val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
            contentTypeHeaderParts.forEachIndexed { index, part ->
                if (index == 0 && part.isNotEmpty()) {
                    contentType.type = part.trim()
                } else if (part.contains("charset=")) {
                    contentType.charset = part.substringAfter("charset=").trim()
                }
            }

            // special treatment for octet-stream - try to get content type from file extension
            if (contentType.type.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
                Log.w(TAG, "Special case \"application/octet-stream\"")
                val headerFieldContentDisposition: String? = connection.getHeaderField("Content-Disposition")
                if (headerFieldContentDisposition != null) {
                    val fileName: String = headerFieldContentDisposition.split("=")[1].replace("\"", "") //getting value after '=' & stripping any "s
                    contentType.type = FileHelper.getContentTypeFromExtension(fileName)
                } else {
                    Log.i(TAG, "Unable to get file name from \"Content-Disposition\" header field.")
                }
            }

            connection.disconnect()
        }
        Log.i(TAG, "content type: ${contentType.type} | character set: ${contentType.charset}")
        return contentType
    }


    /* Download playlist - up to 100 lines, with max. 200 characters */
    fun downloadPlaylist(playlistUrlString: String): List<String> {
        val lines = mutableListOf<String>()
        val connection = URL(playlistUrlString).openConnection()
        // add user agent
        connection.setRequestProperty("User-Agent", Keys.USER_AGENT)
        val reader = connection.getInputStream().bufferedReader()
        reader.useLines { sequence ->
            sequence.take(100).forEach { line ->
                val trimmedLine = line.take(2000)
                lines.add(trimmedLine)
            }
        }
        return lines
    }


//    /* Suspend function: Detects content type (mime type) from given URL string - async using coroutine */
//    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
//        return suspendCoroutine { cont ->
//            cont.resume(detectContentType(urlString))
//        }
//    }


    /* Gets a random radio-browser.info api address - async using coroutine */
    fun getRadioBrowserServer(): String {
        var serverAddress: String
        try {
            // get all available radio browser servers
            val serverAddressList: Array<InetAddress> = InetAddress.getAllByName(Keys.RADIO_BROWSER_API_BASE)
            // select a random address
            serverAddress = serverAddressList[Random().nextInt(serverAddressList.size)].canonicalHostName
        } catch (e: UnknownHostException) {
            serverAddress = Keys.RADIO_BROWSER_API_DEFAULT
        }
        PreferencesHelper.saveRadioBrowserApiAddress(serverAddress)
        return serverAddress

    }


    /* Creates a http connection from given url string */
    private fun createConnection(urlString: String, redirectCount: Int = 0): HttpURLConnection? {
        var connection: HttpURLConnection? = null

        try {
            // try to open connection and get status
            Log.i(TAG, "Opening http connection.")
            connection = URL(urlString).openConnection() as HttpURLConnection
            // add user agent - and mask as web browser if required
            connection.setRequestProperty("User-Agent", Keys.USER_AGENT)
            for (hostname in Keys.WEB_BROWSER_USER_AGENT_REQUIRED) {
                if (urlString.contains(hostname, ignoreCase = true)) {
                    connection.setRequestProperty("User-Agent", Keys.WEB_BROWSER_USER_AGENT)
                }
            }
            val status = connection.responseCode
            Log.i(TAG, "http status: $status")
            // CHECK for non-HTTP_OK status
            if (status != HttpURLConnection.HTTP_OK) {
                // CHECK for redirect status
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
                    val redirectUrl: String = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (redirectCount < 5) {
                        Log.i(TAG, "Following redirect to $redirectUrl")
                        connection = createConnection(redirectUrl, redirectCount + 1)
                    } else {
                        connection = null
                        Log.e(TAG, "Too many redirects.")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unable to open http connection.")
            e.printStackTrace()
        }

        return connection
    }


}
