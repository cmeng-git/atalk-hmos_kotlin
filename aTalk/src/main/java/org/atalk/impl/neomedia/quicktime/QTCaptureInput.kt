/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a QTKit `QTCaptureInput` object.
 *
 * @author Lyubomir Marinov
 */
open class QTCaptureInput
/**
 * Initializes a new `QTCaptureInput` instance which is to represent a specific QTKit
 * `QTCaptureInput` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTCaptureInput` object to be represented by the new
 * instance
 */
(ptr: Long) : NSObject(ptr)