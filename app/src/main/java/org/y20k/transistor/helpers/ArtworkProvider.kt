/*
 * ArtworkProvider.kt
 * Implements the ArtworkProvider class
 * An ArtworkProvider serves the station artwork as content:// URIs - Android Auto loads artwork only from URIs
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor.helpers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException


/*
 * ArtworkProvider class - read-only, serves only the files in the artwork cache folder
 */
class ArtworkProvider: ContentProvider() {


    /* Main class variables */
    companion object {
        private const val FOLDER_ARTWORK: String = "artwork"

        /* Returns the content:// URI of an artwork - writes the artwork into the cache folder if it is not there yet */
        fun getArtworkUri(context: Context, name: String, createArtwork: () -> ByteArray): Uri {
            val folder = File(context.cacheDir, FOLDER_ARTWORK)
            val file = File(folder, name)
            if (!file.exists()) {
                folder.mkdirs()
                val temp = File(folder, "$name.tmp")
                temp.writeBytes(createArtwork())
                temp.renameTo(file)
            }
            return Uri.Builder().scheme("content").authority("${context.packageName}.artwork").appendPath(name).build()
        }
    }


    /* Overrides onCreate from ContentProvider */
    override fun onCreate(): Boolean = true


    /* Overrides openFile from ContentProvider - only plain file names inside the artwork folder are served */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context: Context = context ?: throw FileNotFoundException()
        val name: String = uri.lastPathSegment ?: throw FileNotFoundException()
        if (mode != "r" || name.contains("/") || name.startsWith(".")) throw FileNotFoundException()
        val file = File(File(context.cacheDir, FOLDER_ARTWORK), name)
        if (!file.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }


    /* Overrides getType from ContentProvider */
    override fun getType(uri: Uri): String = "image/jpeg"


    /* Unused ContentProvider methods - the provider is read-only */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0


}
