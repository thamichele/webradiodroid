/*
 * UiHelper.kt
 * Implements the UiHelper object
 * A UiHelper provides helper methods for User Interface related tasks
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import org.y20k.transistor.Keys
import org.y20k.transistor.R
import java.io.ByteArrayOutputStream
import java.io.File


/*
 * UiHelper object
 */
object UiHelper {

    /* Define log tag */
    private val TAG: String = UiHelper::class.java.simpleName


    /* Get scaling factor from display density */
    fun getDensityScalingFactor(context: Context): Float {
        return context.resources.displayMetrics.density
    }


    /* Sets layout margins for given view in DP */
    fun setViewMargins(context: Context, view: View, left: Int = 0, right: Int = 0, top: Int= 0, bottom: Int = 0) {
        val l: Int = (left * getDensityScalingFactor(context)).toInt()
        val r: Int = (right * getDensityScalingFactor(context)).toInt()
        val t: Int = (top * getDensityScalingFactor(context)).toInt()
        val b: Int = (bottom * getDensityScalingFactor(context)).toInt()
        if (view.layoutParams is ViewGroup.MarginLayoutParams) {
            val p = view.layoutParams as ViewGroup.MarginLayoutParams
            p.setMargins(l, t, r, b)
            view.requestLayout()
        }
    }


    /* Sets layout margins for given view in percent */
    fun setViewMarginsPercentage(context: Context, view: View, height: Int, width: Int, left: Int = 0, right: Int = 0, top: Int= 0, bottom: Int = 0) {
        val l: Int = ((width / 100.0f) * left).toInt()
        val r: Int = ((width / 100.0f) * right).toInt()
        val t: Int = ((height / 100.0f) * top).toInt()
        val b: Int = ((height / 100.0f) * bottom).toInt()
        setViewMargins(context, view, l, r, t, b)
    }


    /* Extracts color from an image - creates the image blocking */
    fun getMainColor(context: Context, imageUri: String, size: Int = 72): Int {
        try {
            // load the station image
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(imageUri)
                .apply(
                    RequestOptions()
                        .override(size, size)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                )
                .submit()
                .get() // this blocks until the image is loaded - use only on background thread

            // extract color palette from the bitmap
            val palette: Palette = Palette.from(bitmap).generate()

            // get muted and vibrant swatches
            val vibrantSwatch = palette.vibrantSwatch
            val mutedSwatch = palette.mutedSwatch

            when {
                vibrantSwatch != null -> {
                    val rgb = vibrantSwatch.rgb
                    return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
                }
                mutedSwatch != null -> {
                    val rgb = mutedSwatch.rgb
                    return Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
                }
                else -> {
                    return "#ff7d7d7d".toColorInt() // color = system_neutral1_300
                }
            }
        } catch (e: Exception) {
            return "#ff7d7d7d".toColorInt()
        }
    }


    /* Displays a simple Snackbar message and anchors it to given view */
    fun displaySnackbar(contextView: View, anchorView: View, text: Int, requireConfirmation: Boolean) {
        if (requireConfirmation) {
            Snackbar.make(contextView, text, Snackbar.LENGTH_INDEFINITE)
                    .setAnchorView(anchorView)
                    .setAction(R.string.dialog_generic_button_okay) {
                        // snackbar ok button has clicked - just dismiss / do nothing
                    }
                    .show()
        } else {
            Snackbar.make(contextView, text, Snackbar.LENGTH_SHORT)
                    .setAnchorView(anchorView)
                    .show()
        }
    }


    /* Get the height of the system's top status bar */
    fun getStatusBarHeight(context: Context): Int {
        var result: Int = 0
        val resourceId: Int = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }


    /* Hide keyboard */
    fun hideSoftKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }


    /* Get the default station image as a ByteArray */
    fun getDefaultStationImageAsByteArray(context: Context, size: Int = 512): ByteArray {
        val stationImageBitmap: Bitmap = ContextCompat.getDrawable(context, R.drawable.ic_default_station_image_64dp)!!.toBitmap(size, size)
        val stream = ByteArrayOutputStream()
        stationImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val coverByteArray: ByteArray = stream.toByteArray()
        stationImageBitmap.recycle()
        return coverByteArray
    }


    /* Creates the artwork shown by Android Auto and the media notification: the station image filling a square,
     * or - for stations without image - the WebRadioDroid glyph on the dark "Arancio UI" background */
    fun createArtworkAsByteArray(context: Context, stationImageFile: File?, imageColor: Int, size: Int = 512): ByteArray {
        val stationImage: Bitmap? = stationImageFile?.let { BitmapFactory.decodeFile(it.path) }
        val artwork: Bitmap = if (stationImage != null) {
            createSquareImage(stationImage, imageColor, size, adaptivePadding = false).also { stationImage.recycle() }
        } else {
            createBitmap(size, size).also { placeholder ->
                val canvas = Canvas(placeholder)
                canvas.drawColor("#FF252422".toColorInt())
                ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.apply {
                    setBounds(0, 0, size, size)
                    draw(canvas)
                }
            }
        }
        val stream = ByteArrayOutputStream()
        artwork.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        artwork.recycle()
        return stream.toByteArray()
    }


    /* Creates station image on a square background with the main station image color and option padding for adaptive icons */
    fun createSquareImage(bitmap: Bitmap, backgroundColor: Int, size: Int, adaptivePadding: Boolean): Bitmap {
        // create background
        val background = Paint()
        background.style = Paint.Style.FILL
        if (backgroundColor != -1) {
            background.color = backgroundColor
        } else {
            background.color = "#ff595959".toColorInt() // color = system_neutral1_600
        }
        // create empty bitmap and canvas
        val outputImage: Bitmap = createBitmap(size, size)
        val imageCanvas: Canvas = Canvas(outputImage)
        // draw square background
        val right = size.toFloat()
        val bottom = size.toFloat()
        imageCanvas.drawRect(0f, 0f, right, bottom, background)
        // draw input image onto canvas using transformation matrix
        val paint = Paint()
        paint.isFilterBitmap = true
        imageCanvas.drawBitmap(bitmap, createTransformationMatrix(size, 0, bitmap.height.toFloat(), bitmap.width.toFloat(), adaptivePadding), paint)
        return outputImage
    }


    /* Creates a transformation matrix with the given size and optional padding  */
    private fun createTransformationMatrix(size: Int, yOffset: Int, inputImageHeight: Float, inputImageWidth: Float, scaled: Boolean): Matrix {
        val matrix = Matrix()
        // calculate padding
        var padding = 0f
        if (scaled) {
            padding = size.toFloat() / 4f
        }
        // define variables needed for transformation matrix
        var aspectRatio = 0.0f
        var xTranslation = 0.0f
        var yTranslation = 0.0f
        // landscape format and square
        if (inputImageWidth >= inputImageHeight) {
            aspectRatio = (size - padding * 2) / inputImageWidth
            xTranslation = 0.0f + padding
            yTranslation = (size - inputImageHeight * aspectRatio) / 2.0f + yOffset
        } else if (inputImageHeight > inputImageWidth) {
            aspectRatio = (size - padding * 2) / inputImageHeight
            yTranslation = 0.0f + padding + yOffset
            xTranslation = (size - inputImageWidth * aspectRatio) / 2.0f
        }
        // construct transformation matrix
        matrix.postTranslate(xTranslation, yTranslation)
        matrix.preScale(aspectRatio, aspectRatio)
        return matrix
    }



    /*
     * Inner class: Callback that detects a swipe to left
     * Credit: https://github.com/kitek/android-rv-swipe-delete/blob/master/app/src/main/java/pl/kitek/rvswipetodelete/SwipeToDeleteCallback.kt
     */
    abstract class SwipeToDeleteCallback(context: Context): ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_remove_circle_24dp)
        private val intrinsicWidth: Int = deleteIcon?.intrinsicWidth ?: 0
        private val intrinsicHeight: Int = deleteIcon?.intrinsicHeight ?: 0
        private val background: ColorDrawable = ColorDrawable()
        // colorErrorContainer is a Material attribute - the starred callback below deliberately uses appcompat
        private val backgroundColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorErrorContainer, null)
        private val clearPaint: Paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            // disable swipe for the add new card
            if (viewHolder.itemViewType == Keys.VIEW_TYPE_ADD_NEW) {
                return 0
            }
            return super.getMovementFlags(recyclerView, viewHolder)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            // do nothing
            return false
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val itemView = viewHolder.itemView
            val itemHeight = itemView.bottom - itemView.top
            val isCanceled = dX == 0f && !isCurrentlyActive

            if (isCanceled) {
                clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                return
            }

            // draw red delete background
            background.color = backgroundColor
            background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
            ) // left - top - right - bottom
            background.draw(c)

            // calculate position of delete icon
            val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
            val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
            val deleteIconLeft = itemView.right - deleteIconMargin - intrinsicWidth
            val deleteIconRight = itemView.right - deleteIconMargin
            val deleteIconBottom = deleteIconTop + intrinsicHeight

            // draw delete icon
            deleteIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
            deleteIcon?.draw(c)

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
            c?.drawRect(left, top, right, bottom, clearPaint)
        }
    }
    /*
     * End of inner class
     */


    /*
     * Inner class: Callback that detects a swipe to left
     * Credit: https://github.com/kitek/android-rv-swipe-delete/blob/master/app/src/main/java/pl/kitek/rvswipetodelete/SwipeToDeleteCallback.kt
     */
    abstract class SwipeToMarkStarredCallback(context: Context): ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

        private val starIcon = ContextCompat.getDrawable(context, R.drawable.ic_marked_starred_star_24dp)
        private val intrinsicWidth: Int = starIcon?.intrinsicWidth ?: 0
        private val intrinsicHeight: Int = starIcon?.intrinsicHeight ?: 0
        private val background: ColorDrawable = ColorDrawable()
        // do not switch to Material to match the delete callback above: colorPrimary is an appcompat attribute, Material only consumes it
        private val backgroundColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, null)
        private val clearPaint: Paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            // disable swipe for the add new card
            if (viewHolder.itemViewType == Keys.VIEW_TYPE_ADD_NEW) {
                return 0
            }
            return super.getMovementFlags(recyclerView, viewHolder)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            // do nothing
            return false
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val itemView = viewHolder.itemView
            val itemHeight = itemView.bottom - itemView.top
            val isCanceled = dX == 0f && !isCurrentlyActive

            if (isCanceled) {
                clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                return
            }

            // draw red background
            background.color = backgroundColor
            background.setBounds(
                    itemView.left,
                    itemView.top,
                    itemView.left + dX.toInt(),
                    itemView.bottom
            ) // left - top - right - bottom
            background.draw(c)

            // calculate position of delete icon
            val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
            val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
            val deleteIconLeft = itemView.left + deleteIconMargin
            val deleteIconRight = itemView.left + deleteIconMargin + intrinsicWidth
            val deleteIconBottom = deleteIconTop + intrinsicHeight

            // draw delete icon
            starIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
            starIcon?.draw(c)

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
            c?.drawRect(left, top, right, bottom, clearPaint)
        }
    }
    /*
     * End of inner class
     */

}
