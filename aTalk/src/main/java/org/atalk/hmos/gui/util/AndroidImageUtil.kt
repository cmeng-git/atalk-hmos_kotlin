/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.content.*
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import org.atalk.hmos.aTalkApp
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Class containing utility methods for Android's Displayable and Bitmap
 *
 * @author Eng Chong Meng
 */
object AndroidImageUtil {
    /**
     * Converts given array of bytes to [Bitmap]
     *
     * @param imageBlob array of bytes with raw image data
     * @return [Bitmap] created from `imageBlob`
     */
    fun bitmapFromBytes(imageBlob: ByteArray?): Bitmap? {
        return if (imageBlob != null) {
            BitmapFactory.decodeByteArray(imageBlob, 0, imageBlob.size)
        } else null
    }

    /**
     * Creates the [Drawable] from raw image data
     *
     * @param imageBlob the array of bytes containing raw image data
     * @return the [Drawable] created from given `imageBlob`
     */
    fun drawableFromBytes(imageBlob: ByteArray?): Drawable? {
        val bmp = bitmapFromBytes(imageBlob) ?: return null
        return BitmapDrawable(aTalkApp.appResources, bmp)
    }

    /**
     * Creates a `Drawable` from the given image byte array and scales it to the given
     * `width` and `height`.
     *
     * @param imageBytes the raw image data
     * @param width the width to which to scale the image
     * @param height the height to which to scale the image
     * @return the newly created `Drawable`
     */
    fun scaledDrawableFromBytes(imageBytes: ByteArray, width: Int, height: Int): Drawable? {
        val bmp = scaledBitmapFromBytes(imageBytes, width, height)
                ?: return null
        return BitmapDrawable(aTalkApp.appResources, bmp)
    }

    /**
     * Creates a `Bitmap` from the given image byte array and scales it to the given
     * `width` and `height`.
     *
     * @param imageBytes the raw image data
     * @param reqWidth the width to which to scale the image
     * @param reqHeight the height to which to scale the image
     * @return the newly created `Bitmap`
     */
    fun scaledBitmapFromBytes(imageBytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    }

    /**
     * Calculates `options.inSampleSize` for requested width and height.
     *
     * @param options the `Options` object that contains image `outWidth` and `outHeight`.
     * @param reqWidth requested width.
     * @param reqHeight requested height.
     * @return `options.inSampleSize` for requested width and height.
     */
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2
            // and keeps both height and width larger than the requested height
            // and width.
            while (halfHeight / inSampleSize > reqHeight
                    && halfWidth / inSampleSize > reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Decodes `Bitmap` identified by given `resId` scaled to requested width and height.
     *
     * @param res the `Resources` object.
     * @param resId bitmap resource id.
     * @param reqWidth requested width.
     * @param reqHeight requested height.
     * @return `Bitmap` identified by given `resId` scaled to requested width and
     * height.
     */
    fun scaledBitmapFromResource(res: Resources?, resId: Int, reqWidth: Int, reqHeight: Int): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(res, resId, options)
    }

    /**
     * Reads `Bitmap` from given `uri` using `ContentResolver`. Output image is scaled
     * to given `reqWidth` and `reqHeight`. Output size is not guaranteed to match exact
     * given values, because only powers of 2 are used as scale factor. Algorithm tries to scale image down
     * as long as the output size stays larger than requested value.
     *
     * @param ctx the context used to create `ContentResolver`.
     * @param uri the `Uri` that points to the image.
     * @param reqWidth requested width.
     * @param reqHeight requested height.
     * @return `Bitmap` from given `uri` retrieved using `ContentResolver`
     * and down sampled as close as possible to match requested width and height.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun scaledBitmapFromContentUri(ctx: Context, uri: Uri?, reqWidth: Int, reqHeight: Int): Bitmap? {
        var imageStream: InputStream? = null
        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            imageStream = ctx.contentResolver.openInputStream(uri!!)
            if (imageStream == null) return null
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(imageStream, null, options)
            imageStream.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            imageStream = ctx.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(imageStream, null, options)
        } finally {
            imageStream?.close()
        }
    }

    /**
     * Encodes given `Bitmap` to array of bytes using given compression `quality` in PNG format.
     *
     * @param bmp the bitmap to encode.
     * @param quality encoding quality in range 0-100.
     * @return raw bitmap data PNG encoded using given `quality`.
     */
    fun convertToBytes(bmp: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Loads an image from a given image identifier and return bytes of the image.
     *
     * @param imageID The identifier of the image i.e. R.drawable.
     * @return The image bytes for the given identifier.
     */
    fun getImageBytes(ctx: Context, imageID: Int): ByteArray {
        val bitmap = BitmapFactory.decodeResource(ctx.resources, imageID)
        return convertToBytes(bitmap, 100)
    }

    /**
     * Creates a `Bitmap` with rounded corners.
     *
     * @param bitmap the bitmap that will have it's corners rounded.
     * @param factor factor used to calculate corners radius based on width and height of the image.
     * @return a `Bitmap` with rounded corners created from given `bitmap`.
     */
    fun getRoundedCornerBitmap(bitmap: Bitmap, factor: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        val rX = bitmap.width.toFloat() / 2 // * factor;
        val rY = bitmap.height.toFloat() / 2 // * factor ;
        // float r = (rX+rY)/2;

        //canvas.drawRoundRect(rectF, rX, rY, paint);
        canvas.drawCircle(rX, rY, rX, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    /**
     * Creates `BitmapDrawable` with rounded corners from raw image data.
     *
     * @param rawData raw bitmap data
     * @return `BitmapDrawable` with rounded corners from raw image data.
     */
    fun roundedDrawableFromBytes(rawData: ByteArray?): BitmapDrawable? {
        var bmp = bitmapFromBytes(rawData) ?: return null
        bmp = getRoundedCornerBitmap(bmp, 0.10f)
        return BitmapDrawable(aTalkApp.appResources, bmp)
    }

    /**
     * Creates a rounded corner scaled image.
     *
     * @param imageBytes The bytes of the image to be scaled.
     * @param width The maximum width of the scaled image.
     * @param height The maximum height of the scaled image.
     * @return The rounded corner scaled image.
     */
    fun getScaledRoundedIcon(imageBytes: ByteArray, width: Int, height: Int): Drawable {
        val bmp = getRoundedCornerBitmap(scaledBitmapFromBytes(imageBytes, width, height), 0.1f)
        return BitmapDrawable(aTalkApp.appResources, bmp)
    }
}