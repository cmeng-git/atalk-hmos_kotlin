/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a QuickTime/QTKit `QTSampleBuffer` object.
 *
 * @author Lyubomir Marinov
 */
open class QTSampleBuffer
/**
 * Initializes a new `QTSampleBuffer` which is to represent a specific QuickTime/QTKit
 * `QTSampleBuffer` object.
 *
 * @param ptr
 * the pointer to the QuickTime/QTKit `QTSampleBuffer` object to be represented by
 * the new instance
 */
(ptr: Long) : NSObject(ptr) {
    fun bytesForAllSamples(): ByteArray {
        return bytesForAllSamples(ptr)
    }

    fun formatDescription(): QTFormatDescription? {
        val formatDescriptionPtr = formatDescription(ptr)
        return if (formatDescriptionPtr == 0L) null else QTFormatDescription(formatDescriptionPtr)
    }

    companion object {
        private external fun bytesForAllSamples(ptr: Long): ByteArray
        private external fun formatDescription(ptr: Long): Long
    }
}