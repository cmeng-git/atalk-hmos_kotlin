/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import javax.media.CaptureDeviceInfo
import javax.media.Format
import javax.media.MediaLocator

/**
 * Adds some important information (i.e. device type, UID.) to FMJ `CaptureDeviceInfo`.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class CaptureDeviceInfo2
/**
 * Initializes a new `CaptureDeviceInfo2` instance with the specified name, media
 * locator, and array of Format objects.
 *
 * @param name the human-readable name of the new instance
 * @param locator the `MediaLocator` which uniquely identifies the device to be described by the
 * new instance
 * @param formats an array of the `Format`s supported by the device to be described by the new
 * instance
 * uid the unique identifier of the hardware device (interface) which is to be represented by
 * the new instance
 * @param transportType the transport type (e.g. USB) of the device to be represented by the new instance
 * @param modelIdentifier the persistent identifier of the model of the hardware device to be represented
 * by the new instance
 */
(name: String?, locator: MediaLocator?, formats: Array<Format>?,
        /**
         * The device UID (unique identifier).
         */
        val uid: String?,

        /**
         * The device transport type.
         */
        private val transportType: String?,

        /**
         * The persistent identifier for the model of this device.
         */
        private val modelIdentifier: String?) : CaptureDeviceInfo(name, locator, formats) {

    /**
     * Returns the device transport type of this instance.
     *
     * @return the device transport type of this instance
     */
    /**
     * Returns the device UID (unique identifier) of this instance.
     *
     * @return the device UID (unique identifier) of this instance
     */
    /**
     * Initializes a new `CaptureDeviceInfo2` instance from a specific
     * `CaptureDeviceInfo` instance and additional information specific to the
     * `CaptureDeviceInfo2` class. Because the properties of the specified
     * `captureDeviceInfo` are copied into the new instance, the constructor is to be used
     * when a `CaptureDeviceInfo` exists for other purposes already; otherwise, it is
     * preferable to use
     * [.CaptureDeviceInfo2] .
     *
     * @param captureDeviceInfo the `CaptureDeviceInfo` whose properties are to be copied into the new instance
     * @param uid the unique identifier of the hardware device (interface) which is to be represented by
     * the new instance
     * @param transportType the transport type (e.g. USB) of the device to be represented by the new instance
     * @param modelIdentifier the persistent identifier of the model of the hardware device to be represented
     * by the new instance
     */
    constructor(captureDeviceInfo: CaptureDeviceInfo, uid: String?, transportType: String?, modelIdentifier: String?) :
            this(captureDeviceInfo.name, captureDeviceInfo.locator, captureDeviceInfo.formats, uid, transportType, modelIdentifier)

    /**
     * Determines whether a specific `Object` is equal (by value) to this instance.
     *
     * @param other the `Object` to be determined whether it is equal (by value) to this instance
     * @return `true` if the specified `obj` is equal (by value) to this instance; otherwise, `false`
     */
    override fun equals(other: Any?): Boolean {
        return if (other == null) false else if (other === this) true else if (other is CaptureDeviceInfo2) {
            // locator
            val locator = getLocator()
            val cdi2Locator = other.getLocator()
            if (locator == null) {
                if (cdi2Locator != null) return false
            } else if (cdi2Locator == null) return false else {
                // protocol
                val protocol = locator.protocol
                val cdi2Protocol = cdi2Locator.protocol
                if (protocol == null) {
                    if (cdi2Protocol != null) return false
                } else if (cdi2Protocol == null) return false else if (protocol != cdi2Protocol) return false
            }

            // identifier
            identifier == other.identifier
        } else false
    }

    /**
     * Returns the device identifier used to save and load device preferences. It is composed by the
     * system UID if not null. Otherwise returns the device name and (if not null) the transport type.
     *
     * @return The device identifier.
     */
    val identifier: String
        get() = uid ?: name

    /**
     * Returns the model identifier of this instance.
     *
     * @return the model identifier of this instance
     */
    fun getModelIdentifier(): String {
        return modelIdentifier ?: name
    }

    /**
     * Returns a hash code value for this object for the benefit of hashtables.
     *
     * @return a hash code value for this object for the benefit of hashtables
     */
    override fun hashCode(): Int {
        return identifier.hashCode()
    }

    /**
     * Determines whether a specific transport type is equal to/the same as the transport type of
     * this instance.
     *
     * @param transportType the transport type to compare to the transport type of this instance
     * @return `true` if the specified `transportType` is equal to/the same as the
     * transport type of this instance; otherwise, `false`
     */
    fun isSameTransportType(transportType: String?): Boolean {
        return this.transportType == transportType
    }
}