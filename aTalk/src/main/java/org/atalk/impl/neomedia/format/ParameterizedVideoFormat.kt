/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format

import java.awt.Dimension
import javax.media.Format
import javax.media.format.VideoFormat

/**
 * Implements a `VideoFormat` with format parameters (like [VideoMediaFormatImpl])
 * (some of) which (could) distinguish payload types.
 *
 * @author Lyubomir Marinov
 */
class ParameterizedVideoFormat : VideoFormat {
    /**
     * The format parameters of this `ParameterizedVideoFormat` instance.
     */
    private var fmtps: Map<String, String>

    /**
     * Constructs a new `ParameterizedVideoFormat`.
     *
     * @param encoding encoding
     * @param size video size
     * @param maxDataLength maximum data length
     * @param dataType data type
     * @param frameRate frame rate
     * @param fmtps format parameters.
     */
    constructor(
            encoding: String, size: Dimension?, maxDataLength: Int,
            dataType: Class<*>?, frameRate: Float, fmtps: Map<String, String>?,
    ) : super(encoding, size, maxDataLength, dataType, frameRate) {
        this.fmtps = when {
            fmtps == null || fmtps.isEmpty() -> MediaFormatImpl.EMPTY_FORMAT_PARAMETERS
            else -> HashMap<String, String>(fmtps)
        }
    }

    /**
     * Initializes a new `ParameterizedVideoFormat` with a specific , and a specific
     * set of format parameters.
     *
     * @param encoding the encoding of the new instance
     * @param fmtps the format parameters of the new instance
     */
    constructor(encoding: String, fmtps: Map<String, String>?) : super(encoding) {
        this.fmtps = when {
            fmtps == null || fmtps.isEmpty() -> MediaFormatImpl.EMPTY_FORMAT_PARAMETERS
            else -> HashMap<String, String>(fmtps)
        }
    }

    /**
     * Initializes a new `ParameterizedVideoFormat` with a specific encoding, and a specific
     * set of format parameters.
     *
     * @param encoding the encoding of the new instance
     * @param fmtps the format parameters of the new instance in the form of an array of `String`s
     * in which the key and the value of an association are expressed as consecutive elements.
     */
    constructor(encoding: String, vararg fmtps: String) : this(encoding, toMap<String>(*fmtps))

    /**
     * Initializes a new `ParameterizedVideoFormat` instance which has the same properties as this instance.
     *
     * @return a new `ParameterizedVideoFormat` instance which has the same properties as this instance.
     */
    override fun clone(): Any {
        val f = ParameterizedVideoFormat(
                getEncoding(),
                getSize(),
                getMaxDataLength(),
                getDataType(),
                getFrameRate(), /* The formatParameters will be copied by ParameterizedVideoFormat#copy(Format) bellow */
                null)
        f.copy(this)
        return f
    }

    /**
     * Copies the properties of the specified `Format` into this instance.
     *
     * @param f the `Format` the properties of which are to be copied into this instance.
     */
    override fun copy(f: Format) {
        super.copy(f)
        if (f is ParameterizedVideoFormat) {
            val pvfFmtps = f.formatParameters
            fmtps = if (pvfFmtps == null || pvfFmtps.isEmpty()) MediaFormatImpl.EMPTY_FORMAT_PARAMETERS else HashMap<String, String>(pvfFmtps)
        }
    }

    /**
     * Determines whether a specific `Object` represents a value that is equal to the value
     * represented by this instance.
     *
     * @param other the `Object` to be determined whether it represents a value that is equal to
     * the value represented by this instance
     * @return `true` if the specified `obj` represents a value that is equal to the
     * value represented by this instance; otherwise, `false`
     */
    override fun equals(other: Any?): Boolean {
        if (!super.equals(other)) return false
        var objFmtps: MutableMap<String, String>? = null
        if (other is ParameterizedVideoFormat) objFmtps = other.formatParameters
        return VideoMediaFormatImpl.formatParametersAreEqual(getEncoding(), formatParameters, objFmtps)
    }

    /**
     * Returns true if the format parameters matched.
     *
     * @param format format to test
     * @return true if the format parameters match.
     */
    fun formatParametersMatch(format: Format?): Boolean {
        var formatFmtps: Map<String, String>? = null
        if (format is ParameterizedVideoFormat) formatFmtps = format.formatParameters
        return VideoMediaFormatImpl.formatParametersMatch(getEncoding(), formatParameters, formatFmtps)
    }

    /**
     * Returns the format parameters value for the specified name.
     *
     * @param name format parameters name
     * @return value for the specified format parameters name.
     */
    fun getFormatParameter(name: String): String? {
        return fmtps[name]
    }

    /**
     * Returns the format parameters `Map`.
     *
     * @return the format parameters `Map`.
     */
    val formatParameters: MutableMap<String, String>
        get() = HashMap(fmtps)

    /**
     * Finds the attributes shared by two matching `Format`s. If the specified
     * `Format` does not match this one, the result is undefined.
     *
     * @param format the matching `Format` to intersect with this one
     * @return a `Format` with its attributes set to the attributes common to this instance
     * and the specified `format`
     */
    override fun intersects(format: Format): Format? {
        val intersection = super.intersects(format) ?: return null
        (intersection as ParameterizedVideoFormat).fmtps = if (fmtps.isEmpty()) MediaFormatImpl.EMPTY_FORMAT_PARAMETERS else formatParameters
        return intersection
    }

    /**
     * Determines whether a specific format matches this instance i.e. whether their attributes
     * match according to the definition of "match" given by [Format.matches].
     *
     * @param format the `Format` to compare to this instance
     * @return `true` if the specified `format` matches this one; otherwise, `false`
     */
    override fun matches(format: Format?): Boolean {
        return super.matches(format) && formatParametersMatch(format)
    }

    override fun toString(): String {
        val s = StringBuilder()
        s.append(super.toString())

        // fmtps
        run {
            s.append(", fmtps={")
            for ((key, value) in fmtps) {
                s.append(key)
                s.append('=')
                s.append(value)
                s.append(',')
            }
            val lastIndex = s.length - 1
            if (s[lastIndex] == ',') s.setCharAt(lastIndex, '}') else s.append('}')
        }
        return s.toString()
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Initializes a new `Map` from an array in which the key, and the value of an
         * association are expressed as consecutive elements.
         *
         * @param <T> the very type of the keys and the values to be associated in the new `Map`
         * @param entries the associations to be created in the new `Map` where the key and value of an
         * association are expressed as consecutive elements
         * @return a new `Map` with the associations specified by `entries`
        </T> */
        fun <T> toMap(vararg entries: T): Map<T, T>? {
            val map: MutableMap<T, T>?
            if (entries.isEmpty()) map = null else {
                map = HashMap()
                var i = 0
                while (i < entries.size) {
                    map[entries[i++]] = entries[i]
                    i++
                }
            }
            return map
        }
    }
}