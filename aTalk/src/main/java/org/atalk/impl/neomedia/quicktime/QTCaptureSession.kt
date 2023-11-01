/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

import org.atalk.impl.neomedia.quicktime.NSErrorException

/**
 * Represents a QTKit `QTCaptureSession` object.
 *
 * @author Lyubomir Marinov
 */
class QTCaptureSession
/**
 * Initializes a new `QTCaptureSession` instance which is to represent a specific QTKit
 * `QTCaptureSession` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTCaptureSession` object to be represented by the new
 * instance
 */
/**
 * Initializes a new `QTCaptureSession` instance which represents a new QTKit
 * `QTCaptureSession` object.
 */
@JvmOverloads constructor(ptr: Long = allocAndInit()) : NSObject(ptr) {
    private var closed = false
    @Throws(NSErrorException::class)
    fun addInput(input: QTCaptureInput): Boolean {
        return addInput(ptr, input.ptr)
    }

    @Throws(NSErrorException::class)
    fun addOutput(output: QTCaptureOutput): Boolean {
        return addOutput(ptr, output.ptr)
    }

    /**
     * Releases the resources used by this instance throughout its existence and makes it available
     * for garbage collection. This instance is considered unusable after closing.
     */
    @Synchronized
    fun close() {
        if (!closed) {
            stopRunning()
            release()
            closed = true
        }
    }

    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected fun finalize() {
        close()
    }

    fun startRunning() {
        startRunning(ptr)
    }

    fun stopRunning() {
        stopRunning(ptr)
    }

    companion object {
        @Throws(NSErrorException::class)
        private external fun addInput(ptr: Long, inputPtr: Long): Boolean

        @Throws(NSErrorException::class)
        private external fun addOutput(ptr: Long, outputPtr: Long): Boolean

        private external fun allocAndInit(): Long
        private external fun startRunning(ptr: Long)
        private external fun stopRunning(ptr: Long)
    }
}