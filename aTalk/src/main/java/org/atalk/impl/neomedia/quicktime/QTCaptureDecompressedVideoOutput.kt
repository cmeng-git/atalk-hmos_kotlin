/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

/**
 * Represents a QTKit `QTCaptureDecompressedVideoOutput` object.
 *
 * @author Lyubomir Marinov
 */
class QTCaptureDecompressedVideoOutput
/**
 * Initializes a new `QTCaptureDecompressedVideoOutput` which is to represent a new
 * QTKit `QTCaptureDecompressedVideoOutput` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTCaptureDecompressedVideoOutput` object to be
 * represented by the new instance
 */
/**
 * Initializes a new `QTCaptureDecompressedVideoOutput` which represents a new QTKit
 * `QTCaptureDecompressedVideoOutput` object.
 */
@JvmOverloads constructor(ptr: Long = allocAndInit()) : QTCaptureOutput(ptr) {
    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected fun finalize() {
        release()
    }

    /**
     * Gets the minimum time interval between which this `QTCaptureDecompressedVideoOutput`
     * will output consecutive video frames.
     *
     * @return the minimum time interval between which this `QTCaptureDecompressedVideoOutput`
     * will output consecutive video frames. It is equivalent to the inverse of the maximum frame rate.
     * The value of `0` indicates an unlimited maximum frame rate.
     */
    fun minimumVideoFrameInterval(): Double {
        return minimumVideoFrameInterval(ptr)
    }

    fun pixelBufferAttributes(): NSDictionary? {
        val pixelBufferAttributesPtr = pixelBufferAttributes(ptr)
        return if (pixelBufferAttributesPtr == 0L) null else NSDictionary(pixelBufferAttributesPtr)
    }

    fun setAutomaticallyDropsLateVideoFrames(automaticallyDropsLateVideoFrames: Boolean): Boolean {
        return setAutomaticallyDropsLateVideoFrames(ptr, automaticallyDropsLateVideoFrames)
    }

    fun setDelegate(delegate: Delegate) {
        setDelegate(ptr, delegate)
    }

    /**
     * Sets the minimum time interval between which this `QTCaptureDecompressedVideoOutput`
     * is to output consecutive video frames.
     *
     * @param minimumVideoFrameInterval the minimum time interval between which this
     * `QTCaptureDecompressedVideoOutput` is to output consecutive video frames. It is equivalent
     * to the inverse of the maximum frame rate. The value of `0` indicates an unlimited frame rate.
     */
    fun setMinimumVideoFrameInterval(minimumVideoFrameInterval: Double) {
        setMinimumVideoFrameInterval(ptr, minimumVideoFrameInterval)
    }

    fun setPixelBufferAttributes(pixelBufferAttributes: NSDictionary) {
        setPixelBufferAttributes(ptr, pixelBufferAttributes.ptr)
    }

    /**
     * Represents the receiver of `CVImageBuffer` video frames and their associated
     * `QTSampleBuffer`s captured by a `QTCaptureDecompressedVideoOutput`.
     */
    abstract class Delegate {
        private var sampleBuffer: MutableQTSampleBuffer? = null
        private var videoFrame: MutableCVPixelBuffer? = null

        /**
         * Notifies this `Delegate` that the `QTCaptureOutput` to which it is set has
         * output a specific `CVImageBuffer` representing a video frame with a specific `QTSampleBuffer`.
         *
         * @param videoFrame the `CVImageBuffer` which represents the output video frame
         * @param sampleBuffer the `QTSampleBuffer` which represents additional details about the output
         * video samples
         */
        abstract fun outputVideoFrameWithSampleBuffer(
                videoFrame: CVImageBuffer?,
                sampleBuffer: QTSampleBuffer?,
        )

        fun outputVideoFrameWithSampleBuffer(videoFramePtr: Long, sampleBufferPtr: Long) {
            if (videoFrame == null) videoFrame = MutableCVPixelBuffer(videoFramePtr) else videoFrame!!.ptr = videoFramePtr
            if (sampleBuffer == null) sampleBuffer = MutableQTSampleBuffer(sampleBufferPtr) else sampleBuffer!!.ptr = sampleBufferPtr
            outputVideoFrameWithSampleBuffer(videoFrame, sampleBuffer)
        }
    }

    /**
     * Represents a `CVPixelBuffer` which allows public changing of the CoreVideo
     * `CVPixelBufferRef` it represents.
     */
    private class MutableCVPixelBuffer

    /**
     * Initializes a new `MutableCVPixelBuffer` which is to represent a specific CoreVideo `CVPixelBufferRef`.
     *
     * @param ptr the CoreVideo `CVPixelBufferRef` to be represented by the new instance
     */
    (ptr: Long) : CVPixelBuffer(ptr) {
    }

    /**
     * Represents a `QTSampleBuffer` which allows public changing of the QTKit
     * `QTSampleBuffer` object it represents.
     */
    private class MutableQTSampleBuffer
    /**
     * Initializes a new `MutableQTSampleBuffer` instance which is to represent a
     * specific QTKit `QTSampleBuffer` object.
     *
     * @param ptr the pointer to the QTKit `QTSampleBuffer` object to be represented by the new instance
     */
    (ptr: Long) : QTSampleBuffer(ptr) {
    }

    companion object {
        private external fun allocAndInit(): Long

        /**
         * Gets the minimum time interval between which a specific
         * `QTCaptureDecompressedVideoOutput` instance will output consecutive video frames.
         *
         * @param ptr a pointer to the `QTCaptureDecompressedVideoOutput` instance to get the minimum
         * time interval between consecutive video frame output of
         *
         * @return the minimum time interval between which a specific
         * `QTCaptureDecompressedVideoOutput` instance will output consecutive video
         * frames. It is equivalent to the inverse of the maximum frame rate. The value of
         * `0` indicates an unlimited maximum frame rate.
         */
        private external fun minimumVideoFrameInterval(ptr: Long): Double
        private external fun pixelBufferAttributes(ptr: Long): Long
        private external fun setAutomaticallyDropsLateVideoFrames(
                ptr: Long, automaticallyDropsLateVideoFrames: Boolean,
        ): Boolean

        private external fun setDelegate(ptr: Long, delegate: Delegate)

        /**
         * Sets the minimum time interval between which a specific
         * `QTCaptureDecompressedVideoOutput` instance is to output consecutive video frames.
         *
         * @param ptr a pointer to the `QTCaptureDecompressedVideoOutput` instance to set the minimum
         * time interval between consecutive video frame output on
         * @param minimumVideoFrameInterval the minimum time interval between which a specific
         * `QTCaptureDecompressedVideoOutput` instance is to output consecutive video
         * frames. It is equivalent to the inverse of the maximum frame rate. The value of
         * `0` indicates an unlimited frame rate.
         */
        private external fun setMinimumVideoFrameInterval(ptr: Long, minimumVideoFrameInterval: Double)

        private external fun setPixelBufferAttributes(ptr: Long, pixelBufferAttributesPtr: Long)
    }
}