/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video

import timber.log.Timber
import java.awt.Dimension
import javax.media.Format
import javax.media.format.VideoFormat

/**
 * Implements a `VideoFormat` for a `Buffer` carrying `AVFrame` as its `data`. While the
 * `AVFrameFormat` class is not strictly necessary and `VideoFormat` could have be directly used, it is
 * conceived as an appropriate way to avoid possible matching with other `VideoFormat`s and a very obvious one.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class AVFrameFormat @JvmOverloads constructor(size: Dimension?, frameRate: Float, pixFmt: Int, deviceSystemPixFmt: Int = NOT_SPECIFIED) : VideoFormat(AVFRAME, size, NOT_SPECIFIED, AVFrame::class.java, frameRate) {
    /**
     * Gets the `DeviceSystem`-specific format represented by this instance.
     *
     * @return the `DeviceSystem`-specific format represented by this instance
     */
    /**
     * The `DeviceSystem`-specific format represented by this instance. It is necessary in the cases of multiple
     * `DeviceSystem`-specific formats corresponding to one and the same native FFmpeg format in which JMF is
     * unable to differentiate them yet the device needs to know its `DeviceSystem`-specific format.
     */
    var deviceSystemPixFmt: Int
        private set
    /**
     * Gets the native FFmpeg format represented by this instance.
     *
     * @return the native FFmpeg format represented by this instance
     */
    /**
     * The native FFmpeg format represented by this instance.
     */
    var pixFmt: Int
        private set

    /**
     * Initializes a new `AVFrameFormat` instance with specific size, frame rate and FFmpeg colorspace.
     *
     * @param size the `Dimension` of the new instance
     * @param frameRate the frame rate of the new instance
     * @param pixFmt the FFmpeg colorspace to be represented by the new instance
     * @param deviceSystemPixFmt the `DeviceSystem`-specific colorspace to be represented by the new instance
     */
    init {
        if (pixFmt == NOT_SPECIFIED && deviceSystemPixFmt != NOT_SPECIFIED) {
            Timber.w(Throwable(), "Specifying a device system-specific pixel format %08x without a matching FFmpeg pixel format may eventually lead to a failure.",
                    deviceSystemPixFmt.toLong() and 0xffffffffL)
        }
        this.pixFmt = pixFmt
        this.deviceSystemPixFmt = deviceSystemPixFmt
    }
    /**
     * Initializes a new `AVFrameFormat` instance with a specific FFmpeg
     * colorspace and unspecified size and frame rate.
     *
     * @param pixFmt the FFmpeg colorspace to be represented by the new instance
     * @param deviceSystemPixFmt the `DeviceSystem`-specific colorspace to be represented by the new instance
     */
    /**
     * Initializes a new `AVFrameFormat` instance with unspecified size, frame rate and FFmpeg colorspace.
     */
    /**
     * Initializes a new `AVFrameFormat` instance with a specific FFmpeg
     * colorspace and unspecified size and frame rate.
     *
     * @param pixFmt the FFmpeg colorspace to be represented by the new instance
     */
    @JvmOverloads
    constructor(pixFmt: Int = NOT_SPECIFIED, deviceSystemPixFmt: Int = NOT_SPECIFIED) : this(null, NOT_SPECIFIED.toFloat(), pixFmt, deviceSystemPixFmt) {
    }

    /**
     * Initializes a new `AVFrameFormat` instance which has the same properties as this instance.
     *
     * @return a new `AVFrameFormat` instance which has the same properties as this instance
     */
    override fun clone(): Any {
        val f = AVFrameFormat(getSize(), getFrameRate(), pixFmt, deviceSystemPixFmt)
        f.copy(this)
        return f
    }

    /**
     * Copies the properties of the specified `Format` into this instance.
     *
     * @param f the `Format` the properties of which are to be copied into this instance
     */
    override fun copy(f: Format) {
        super.copy(f)
        if (f is AVFrameFormat) {
            val avFrameFormat = f
            pixFmt = avFrameFormat.pixFmt
            deviceSystemPixFmt = avFrameFormat.deviceSystemPixFmt
        }
    }

    /**
     * Determines whether a specific `Object` represents a value that is equal to the value represented by this
     * instance.
     *
     * @param obj the `Object` to be determined whether it represents a value that is equal to the value represented
     * by this instance
     * @return `true` if the specified `obj` represents a value that is equal to the value represented by
     * this instance; otherwise, `false`
     */
    override fun equals(obj: Any?): Boolean {
        return if (obj is AVFrameFormat && super.equals(obj)) {
            pixFmt == obj.pixFmt
        } else false
    }

    override fun hashCode(): Int {
        return super.hashCode() + pixFmt
    }

    /**
     * Finds the attributes shared by two matching `Format`s. If the
     * specified `Format` does not match this one, the result is undefined.
     *
     * @param format the matching `Format` to intersect with this one
     * @return a `Format` with its attributes set to the attributes
     * common to this instance and the specified `format`
     */
    override fun intersects(format: Format): Format {
        val intersection = super.intersects(format)
        if (intersection != null) {
            val avFrameFormatIntersection = intersection as AVFrameFormat
            avFrameFormatIntersection.pixFmt = if (pixFmt == NOT_SPECIFIED && format is AVFrameFormat) format.pixFmt else pixFmt
        }
        return intersection
    }

    /**
     * Determines whether a specific format matches this instance i.e. whether their attributes match according to the
     * definition of "match" given by [Format.matches].
     *
     * @param format the `Format` to compare to this instance
     * @return `true` if the specified `format` matches this one; otherwise, `false`
     */
    override fun matches(format: Format?): Boolean {
        val matches = if (super.matches(format)) {
            if (format is AVFrameFormat) {
                pixFmt == NOT_SPECIFIED || format.pixFmt == NOT_SPECIFIED || pixFmt == format.pixFmt
            } else true
        } else false

        return matches
    }

    override fun toString(): String {
        val s = StringBuilder(super.toString())
        if (pixFmt != NOT_SPECIFIED) s.append(", pixFmt ").append(pixFmt)
        if (deviceSystemPixFmt != NOT_SPECIFIED) {
            s.append(", deviceSystemPixFmt 0x")
            /*
             * The value is likely more suitably displayed as unsigned and hexadecimal.
             */
            s.append(java.lang.Long.toHexString(deviceSystemPixFmt.toLong() and 0xffffffffL))
        }
        return s.toString()
    }

    companion object {
        /**
         * The encoding of the `AVFrameFormat` instances.
         */
        const val AVFRAME = "AVFrame"

        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}