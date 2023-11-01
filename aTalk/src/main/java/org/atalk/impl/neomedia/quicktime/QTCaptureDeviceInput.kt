/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a QTKit `QTCaptureDeviceInput` object.
 *
 * @author Lyubomir Marinov
 */
class QTCaptureDeviceInput
/**
 * Initializes a new `QTCaptureDeviceInput` which is to represent a specific QTKit
 * `QTCaptureDeviceInput` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTCaptureDeviceInput` object to be represented by the
 * new instance
 */
(ptr: Long) : QTCaptureInput(ptr) {
    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected fun finalize() {
        release()
    }

    companion object {
        @Throws(IllegalArgumentException::class)
        fun deviceInputWithDevice(device: QTCaptureDevice): QTCaptureDeviceInput {
            return QTCaptureDeviceInput(deviceInputWithDevice(device.ptr))
        }

        @Throws(IllegalArgumentException::class)
        private external fun deviceInputWithDevice(devicePtr: Long): Long
    }
}