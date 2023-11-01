/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime

import org.atalk.impl.neomedia.quicktime.NSErrorException
import java.util.*

/**
 * Represents a QTKit capture device which is connected or has been previously connected to the
 * user's computer during the lifetime of the application.
 *
 * @author Lyubomir Marinov
 */
class QTCaptureDevice
/**
 * Initializes a new `QTCaptureDevice` instance which is to represent a specific QTKit
 * `QTCaptureDevice` object.
 *
 * @param ptr
 * the pointer to the QTKit `QTCaptureDevice` object which is to be represented by
 * the new instance
 */
(ptr: Long) : NSObject(ptr) {
    /**
     * Releases application control over this device acquired in the [.open] method.
     */
    fun close() {
        close(ptr)
    }

    /**
     * Called by the garbage collector to release system resources and perform other cleanup.
     *
     * @see Object.finalize
     */
    protected fun finalize() {
        release()
    }

    fun formatDescriptions(): Array<QTFormatDescription?> {
        val formatDescriptionPtrs = formatDescriptions(ptr)
        val formatDescriptions: Array<QTFormatDescription?>
        if (formatDescriptionPtrs == null) formatDescriptions = NO_FORMAT_DESCRIPTIONS else {
            val formatDescriptionCount = formatDescriptionPtrs.size
            if (formatDescriptionCount == 0) formatDescriptions = NO_FORMAT_DESCRIPTIONS else {
                formatDescriptions = arrayOfNulls(formatDescriptionCount)
                for (i in 0 until formatDescriptionCount) formatDescriptions[i] = QTFormatDescription(formatDescriptionPtrs[i])
            }
        }
        return formatDescriptions
    }

    /**
     * Gets the indicator which determines whether this `QTCaptureDevice` is connected and
     * available to applications.
     *
     * @return `true` if this `QTCaptureDevice` is connected and available to
     * applications; otherwise, `false`
     */
    val isConnected: Boolean
        get() = isConnected(ptr)

    /**
     * Gets the localized human-readable name of this `QTCaptureDevice`.
     *
     * @return the localized human-readable name of this `QTCaptureDevice`
     */
    fun localizedDisplayName(): String {
        return localizedDisplayName(ptr)
    }

    /**
     * Attempts to give the application control over this `QTCaptureDevice` so that it can be
     * used for capture.
     *
     * @return `true` if this device was opened successfully; otherwise, `false`
     * @throws NSErrorException
     * if this device was not opened successfully and carries an `NSError` describing
     * why this device could not be opened
     */
    @Throws(NSErrorException::class)
    fun open(): Boolean {
        return open(ptr)
    }

    /**
     * Gets the unique identifier of this `QTCaptureDevice`.
     *
     * @return the unique identifier of this `QTCaptureDevice`
     */
    fun uniqueID(): String {
        return uniqueID(ptr)
    }

    companion object {
        /**
         * The cached `QTCaptureDevice` instances previously returned by the last call to
         * [.inputDevicesWithMediaType] .
         */
        private val inputDevices = HashMap<QTMediaType, MutableList<QTCaptureDevice>>()

        /**
         * The constant which represents an empty array with `QTFormatDescription` element type.
         * Explicitly defined in order to avoid unnecessary allocations.
         */
        private val NO_FORMAT_DESCRIPTIONS = arrayOfNulls<QTFormatDescription>(0)

        /**
         * The constant which represents an empty array with `QTCaptureDevice` element type.
         * Explicitly defined in order to avoid unnecessary allocations.
         */
        private val NO_INPUT_DEVICES = arrayOfNulls<QTCaptureDevice>(0)

        /**
         * Releases application control over a specific QTKit `QTCaptureDevice` object acquired
         * in the [.open] method.
         *
         * @param ptr
         * the pointer to the QTKit `QTCaptureDevice` object to close
         */
        private external fun close(ptr: Long)

        /**
         * Gets the `QTCaptureDevice` with a specific unique identifier.
         *
         * @param deviceUID
         * the unique identifier of the `QTCaptureDevice` to be retrieved
         * @return the `QTCaptureDevice` with the specified unique identifier if such a
         * `QTCaptureDevice` exists; otherwise, `null`
         */
        fun deviceWithUniqueID(deviceUID: String): QTCaptureDevice? {
            var inputDevices = inputDevicesWithMediaType(QTMediaType.Video)
            var deviceWithUniqueID = deviceWithUniqueID(deviceUID, inputDevices)
            if (deviceWithUniqueID == null) {
                inputDevices = inputDevicesWithMediaType(QTMediaType.Sound)
                deviceWithUniqueID = deviceWithUniqueID(deviceUID, inputDevices)
            }
            return deviceWithUniqueID
        }

        private fun deviceWithUniqueID(deviceUID: String,
                inputDevices: Array<QTCaptureDevice?>?): QTCaptureDevice? {
            if (inputDevices != null) for (inputDevice in inputDevices) if (deviceUID == inputDevice!!.uniqueID()) return inputDevice
            return null
        }

        private external fun formatDescriptions(ptr: Long): LongArray?

        fun inputDevicesWithMediaType(mediaType: QTMediaType): Array<QTCaptureDevice?> {
            val inputDevicePtrs = inputDevicesWithMediaType(mediaType.name)
            val inputDeviceCount = inputDevicePtrs?.size ?: 0
            val inputDevicesWithMediaType: Array<QTCaptureDevice?>
            if (inputDeviceCount == 0) {
                inputDevicesWithMediaType = NO_INPUT_DEVICES
                inputDevices.remove(mediaType)
            } else {
                inputDevicesWithMediaType = arrayOfNulls(inputDeviceCount)
                var cachedInputDevicesWithMediaType = inputDevices[mediaType]
                if (cachedInputDevicesWithMediaType == null) {
                    cachedInputDevicesWithMediaType = LinkedList()
                    inputDevices[mediaType] = cachedInputDevicesWithMediaType
                }

                for (i in 0 until inputDeviceCount) {
                    val inputDevicePtr = inputDevicePtrs!![i]
                    var inputDevice: QTCaptureDevice? = null
                    for (cachedInputDevice in cachedInputDevicesWithMediaType) if (inputDevicePtr == cachedInputDevice.ptr) {
                        inputDevice = cachedInputDevice
                        break
                    }
                    if (inputDevice == null) {
                        inputDevice = QTCaptureDevice(inputDevicePtr)
                        cachedInputDevicesWithMediaType.add(inputDevice)
                    } else NSObject.release(inputDevicePtr)
                    inputDevicesWithMediaType[i] = inputDevice
                }

                val cachedInputDeviceIter = cachedInputDevicesWithMediaType.iterator()
                while (cachedInputDeviceIter.hasNext()) {
                    val cachedInputDevicePtr = cachedInputDeviceIter.next().ptr
                    var remove = true
                    for (inputDevicePtr in inputDevicePtrs!!) if (cachedInputDevicePtr == inputDevicePtr) {
                        remove = false
                        break
                    }
                    if (remove) cachedInputDeviceIter.remove()
                }
            }
            return inputDevicesWithMediaType
        }

        private external fun inputDevicesWithMediaType(mediaType: String): LongArray?

        /**
         * Gets the indicator which determines whether a specific QTKit `QTCaptureDevice` object
         * is connected and available to applications.
         *
         * @param ptr
         * the pointer to the QTKit `QTCaptureDevice` object which is to get the indicator
         * for
         * @return `true` if the specified QTKit `QTCaptureDevice` object is connected and
         * available to applications; otherwise, `false`
         */
        private external fun isConnected(ptr: Long): Boolean

        /**
         * Gets the localized human-readable name of a specific QTKit `QTCaptureDevice` object.
         *
         * @param ptr
         * the pointer to the QTKit `QTCaptureDevice` object to get the localized
         * human-readable name of
         * @return the localized human-readable name of the specified QTKit `QTCaptureDevice`
         * object
         */
        private external fun localizedDisplayName(ptr: Long): String

        /**
         * Attempts to give the application control over a specific QTKit `QTCaptureDevice`
         * object so that it can be used for capture.
         *
         * @param ptr
         * the pointer to the QTKit `QTCaptureDevice` to be opened
         * @return `true` if the device was opened successfully; otherwise, `false`
         * @throws NSErrorException
         * if the device was not opened successfully and carries an `NSError` describing
         * why the device could not be opened
         */
        @Throws(NSErrorException::class)
        private external fun open(ptr: Long): Boolean

        /**
         * Gets the unique identifier of a specific QTKit `QTCaptureDevice` object.
         *
         * @param ptr
         * the pointer to the QTKit `QTCaptureDevice` object to get the unique identifier
         * of
         * @return the unique identifier of the specified QTKit `QTCaptureDevice` object
         */
        private external fun uniqueID(ptr: Long): String
    }
}