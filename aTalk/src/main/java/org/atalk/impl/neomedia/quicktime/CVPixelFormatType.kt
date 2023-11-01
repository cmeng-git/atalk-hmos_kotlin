/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Defines the types of `CVPixelBuffer`s to be output by
 * `QTCaptureDecompressedVideoOutput`.
 *
 * @author Lyubomir Marinov
 */
object CVPixelFormatType {
    /** 24 bit RGB  */
    const val kCVPixelFormatType_24RGB = 0x00000018

    /** 32 bit ARGB  */
    const val kCVPixelFormatType_32ARGB = 0x00000020

    /** Planar Component Y'CbCr 8-bit 4:2:0.  */
    const val kCVPixelFormatType_420YpCbCr8Planar = 0x79343230
}