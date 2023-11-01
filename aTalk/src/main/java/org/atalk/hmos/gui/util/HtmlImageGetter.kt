/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.text.Html.ImageGetter
import android.text.TextUtils
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * Utility class that implements `Html.ImageGetter` interface and can be used to display images in
 * `TextView` through the HTML syntax.<br></br>
 * Source image URI should be formatted as follows:<br></br>
 * <br></br>
 * atalk.resource://{Integer drawable id}, example: atalk.resource://2130837599 <br></br>
 * <br></br>
 * This format is used by Android `ResourceManagementService` to return image URLs.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class HtmlImageGetter : ImageGetter {
    /**
     * {@inheritDoc}
     */
    override fun getDrawable(source: String): Drawable? {
        try {
            // Image resource id is returned here in form:
            // atalk.resource://{Integer drawable id} e.g.: atalk.resource://2130837599
            val resIdStr = source.replace(".*?//(\\d+)".toRegex(), "$1")
            if (source != resIdStr && !TextUtils.isEmpty(resIdStr)) {
                val resId = resIdStr.toInt()
                // Gets application global bitmap cache
                val cache = aTalkApp.imageCache
                return cache.getBitmapFromMemCache(resId)!!
            }
        } catch (e: IndexOutOfBoundsException) {
            // Invalid string format for source.substring(17); Error parsing Integer.parseInt(source.substring(17));
            // Resource for given id is not found
            Timber.e(e, "Error parsing: %s", source)
        } catch (e: NumberFormatException) {
            Timber.e(e, "Error parsing: %s", source)
        } catch (e: Resources.NotFoundException) {
            Timber.e(e, "Error parsing: %s", source)
        }
        return null
    }
}