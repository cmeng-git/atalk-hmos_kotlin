/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a QTKit `QTCaptureOutput` object.
 *
 * @author Lyubomir Marinov
 */
open class QTCaptureOutput
/**
 * Initializes a new `QTCaptureOutput` instance which is to represent a specific QTKit
 * `QTCaptureOutput` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTCaptureOutput` object to be represented by the new
 * instance
 */
(ptr: Long) : NSObject(ptr)