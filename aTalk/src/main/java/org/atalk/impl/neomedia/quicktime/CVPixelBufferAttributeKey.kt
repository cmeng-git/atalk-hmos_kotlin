/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * @author Lyubomir Marinov
 */
object CVPixelBufferAttributeKey {
    var kCVPixelBufferHeightKey = 0L
    var kCVPixelBufferPixelFormatTypeKey = 0L
    var kCVPixelBufferWidthKey = 0L

    init {
        System.loadLibrary("jnquicktime")
        kCVPixelBufferHeightKey = kCVPixelBufferHeightKey()
        kCVPixelBufferPixelFormatTypeKey = kCVPixelBufferPixelFormatTypeKey()
        kCVPixelBufferWidthKey = kCVPixelBufferWidthKey()
    }

    private external fun kCVPixelBufferHeightKey(): Long
    private external fun kCVPixelBufferPixelFormatTypeKey(): Long
    private external fun kCVPixelBufferWidthKey(): Long
}