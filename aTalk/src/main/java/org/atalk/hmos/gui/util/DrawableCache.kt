/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.collection.LruCache
import org.atalk.hmos.aTalkApp

/**
 * Implements bitmap cache using `LruCache` utility class. Single cache instance uses up to 1/8 of total runtime memory available.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class DrawableCache {
    // TODO: there is no LruCache prior API 12
    /**
     * The cache
     */
    private val cache: LruCache<String, BitmapDrawable>

    /**
     * Creates new instance of `DrawableCache`.
     */
    init {
        // Get max available VM memory, exceeding this amount will throw an OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

        // Use 1/8th of the available memory for this memory cache.
        val cacheSize = maxMemory / 8
        cache = object : LruCache<String, BitmapDrawable>(cacheSize) {
            protected fun sizeOf(value: BitmapDrawable): Int {
                val bmp = value.bitmap
                val byteSize = bmp.byteCount
                return byteSize / 1024
            }
        }
    }

    /**
     * Gets cached `BitmapDrawable` for given `resId`. If it doesn't exist in the cache it will be loaded and stored for later
     * use.
     *
     * @param resId bitmap drawable resource id(it must be bitmap resource)
     * @return `BitmapDrawable` for given `resId`
     * @throws Resources.NotFoundException if there's no bitmap for given `resId`
     */
    @Throws(Resources.NotFoundException::class)
    fun getBitmapFromMemCache(resId: Int): BitmapDrawable? {
        val key = "res:$resId"
        // Check for cached bitmap
        var img = cache[key]
        // Eventually loads the bitmap
        if (img == null) {
            // Load and store the bitmap
            val res = aTalkApp.appResources
            val bmp = BitmapFactory.decodeResource(res, resId)
            img = BitmapDrawable(res, bmp)
            img.setBounds(0, 0, img.intrinsicWidth, img.intrinsicHeight)
            cache.put(key, img)
        }
        return cache[key]
    }

    /**
     * Gets bitmap from the cache.
     *
     * @param key drawable key string.
     * @return bitmap from the cache if it exists or `null` otherwise.
     */
    fun getBitmapFromMemCache(key: String): BitmapDrawable? {
        return cache[key]
    }

    /**
     * Puts given `BitmapDrawable` to the cache.
     *
     * @param key drawable key string.
     * @param bmp the `BitmapDrawable` to be cached.
     */
    fun cacheImage(key: String, bmp: BitmapDrawable) {
        cache.put(key, bmp)
    }
}