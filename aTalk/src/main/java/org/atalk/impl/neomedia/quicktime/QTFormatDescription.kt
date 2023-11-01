/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

import java.awt.Dimension

/**
 * Describes the media format of media samples and of media sources, such as devices and capture
 * connections. Includes basic information about the media, such as media type and format type (or
 * codec type), as well as extended information specific to each media type.
 *
 * @author Lyubomir Marinov
 */
class QTFormatDescription
/**
 * Initializes a new `QTFormatDescription` instance which is to represent a specific
 * QTKit `QTFormatDescription` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTFormatDescription` object which is to be
 * represented by the new instance
 */
(ptr: Long) : NSObject(ptr) {
    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected fun finalize() {
        release()
    }

    fun sizeForKey(key: String): Dimension {
        return sizeForKey(ptr, key)
    }

    companion object {
        var VideoEncodedPixelsSizeAttribute: String

        init {
            VideoEncodedPixelsSizeAttribute = VideoEncodedPixelsSizeAttribute()
        }

        private external fun sizeForKey(ptr: Long, key: String): Dimension
        private external fun VideoEncodedPixelsSizeAttribute(): String
    }
}