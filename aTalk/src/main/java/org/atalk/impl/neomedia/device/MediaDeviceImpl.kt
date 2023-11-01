/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaUtils
import org.atalk.impl.neomedia.NeomediaServiceUtils
import org.atalk.impl.neomedia.format.MediaFormatImpl
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice
import org.atalk.impl.neomedia.protocol.CaptureDeviceDelegatePushBufferDataSource
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.QualityPreset
import org.atalk.service.neomedia.codec.EncodingConfiguration
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.MediaType
import timber.log.Timber
import java.awt.Dimension
import java.io.IOException
import javax.media.CaptureDeviceInfo
import javax.media.Manager
import javax.media.MediaLocator
import javax.media.NoDataSourceException
import javax.media.protocol.CaptureDevice
import javax.media.protocol.DataSource
import javax.media.protocol.PushBufferDataSource

/**
 * Implements `MediaDevice` for the JMF `CaptureDevice`.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
open class MediaDeviceImpl : AbstractMediaDevice {
    /**
     * The `CaptureDeviceInfo` of the device that this instance is representing.
     */
    private val captureDeviceInfo: CaptureDeviceInfo?
    /**
     * Gets the `MediaType` that this device supports.
     *
     * @return [MediaType.AUDIO] if this is an audio device or [MediaType.VIDEO] if this is a video device
     * @see MediaDevice.MediaType
     */
    /**
     * The `MediaType` of this instance and the `CaptureDevice` that it wraps.
     */
    final override val mediaType: MediaType

    /**
     * Initializes a new `MediaDeviceImpl` instance which is to provide an implementation of
     * `MediaDevice` for a `CaptureDevice` with a specific `CaptureDeviceInfo`
     * and which is of a specific `MediaType`.
     *
     * @param captureDeviceInfo the `CaptureDeviceInfo` of the JMF `CaptureDevice` the new instance is
     * to provide an implementation of `MediaDevice` for
     * @param mediaType the `MediaType` of the new instance
     */
    constructor(captureDeviceInfo: CaptureDeviceInfo?, mediaType: MediaType?) {
        if (captureDeviceInfo == null) throw NullPointerException("captureDeviceInfo")
        if (mediaType == null) throw NullPointerException("mediaType")
        this.captureDeviceInfo = captureDeviceInfo
        this.mediaType = mediaType
    }

    /**
     * Initializes a new `MediaDeviceImpl` instance with a specific `MediaType` and
     * with `MediaDirection` which does not allow sending.
     *
     * @param mediaType the `MediaType` of the new instance
     */
    constructor(mediaType: MediaType) {
        captureDeviceInfo = null
        this.mediaType = mediaType
    }

    /**
     * Creates the JMF `CaptureDevice` this instance represents and provides an
     * implementation of `MediaDevice` for.
     *
     * @return the JMF `CaptureDevice` this instance represents and provides an
     * implementation of `MediaDevice` for; `null` if the creation fails
     */
    protected open fun createCaptureDevice(): CaptureDevice? {
        var captureDevice: CaptureDevice? = null
        if (direction.allowsSending()) {
            val captureDeviceInfo = getCaptureDeviceInfo()
            var exception: Throwable? = null
            try {
                captureDevice = Manager.createDataSource(captureDeviceInfo!!.locator) as CaptureDevice?
            } catch (ioe: IOException) {
                exception = ioe
            } catch (ioe: NoDataSourceException) {
                exception = ioe
            }

            if (exception != null) {
                Timber.e(exception, "Failed to create CaptureDevice from CaptureDeviceInfo %s",
                        captureDeviceInfo)
            } else {
                if (captureDevice is AbstractPullBufferCaptureDevice) {
                    (captureDevice as AbstractPullBufferCaptureDevice?)!!.setCaptureDeviceInfo(captureDeviceInfo)
                }
                // Try to enable tracing on captureDevice.
                captureDevice = createTracingCaptureDevice(captureDevice)
            }
        }
        return captureDevice
    }

    /**
     * Creates a `DataSource` instance for this `MediaDevice` which gives access to the captured media.
     *
     * @return a `DataSource` instance which gives access to the media captured by this `MediaDevice`
     * @see AbstractMediaDevice.createOutputDataSource
     */
    override fun createOutputDataSource(): DataSource? {
        return if (direction.allowsSending()) {
            createCaptureDevice() as DataSource
        }
        else {
            null
        }
    }

    /**
     * Gets the `CaptureDeviceInfo` of the JMF `CaptureDevice` represented by this instance.
     *
     * @return the `CaptureDeviceInfo` of the `CaptureDevice` represented by this instance
     */
    fun getCaptureDeviceInfo(): CaptureDeviceInfo? {
        return captureDeviceInfo
    }

    /**
     * Gets the protocol of the `MediaLocator` of the `CaptureDeviceInfo` represented by this instance.
     *
     * @return the protocol of the `MediaLocator` of the `CaptureDeviceInfo` represented by this instance
     */
    fun getCaptureDeviceInfoLocatorProtocol(): String? {
        val cdi = getCaptureDeviceInfo()
        if (cdi != null) {
            val locator = cdi.locator
            if (locator != null) return locator.protocol
        }
        return null
    }

    /**
     * Returns the `MediaDirection` supported by this device.
     *
     * @return [MediaDirection.SENDONLY] if this is a read-only device,
     * [MediaDirection.RECVONLY] if this is a write-only device or
     * [MediaDirection.SENDRECV] if this `MediaDevice` can both capture and render media
     * @see MediaDevice.direction
     */
    override val direction: MediaDirection
        get() = when {
            getCaptureDeviceInfo() != null -> MediaDirection.SENDRECV
            MediaType.AUDIO == mediaType -> MediaDirection.INACTIVE
            else -> MediaDirection.RECVONLY
        }

    /**
     * Gets the `MediaFormat` in which this `MediaDevice` captures media.
     *
     * @return the `MediaFormat` in which this `MediaDevice` captures media
     * @see MediaDevice.format
     */
    override val format: MediaFormat?
        get() {
            val captureDevice = createCaptureDevice()
            if (captureDevice != null) {
                val mediaType = mediaType
                for (formatControl in captureDevice.formatControls) {
                    val format = MediaFormatImpl.createInstance(formatControl.format)
                    if (format != null && format.mediaType == mediaType) return format
                }
            }
            return null
        }

    /**
     * Gets the list of `MediaFormat`s supported by this `MediaDevice` and enabled in `encodingConfiguration`.
     *
     * @param encodingConfiguration the `EncodingConfiguration` instance to use
     * @return the list of `MediaFormat`s supported by this device and enabled in `encodingConfiguration`.
     * @see MediaDevice.supportedFormats
     */
    fun getSupportedFormats(encodingConfiguration: EncodingConfiguration?): List<MediaFormat> {
        return getSupportedFormats(null, null, encodingConfiguration)
    }

    /**
     * Gets the list of `MediaFormat`s supported by this `MediaDevice`. Uses the
     * current `EncodingConfiguration` from the media service (i.e. the global configuration).
     *
     * @param localPreset the preset used to set some of the format parameters, used for video and settings.
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings.
     * @return the list of `MediaFormat`s supported by this device
     * @see MediaDevice.getSupportedFormats
     */
    override fun getSupportedFormats(localPreset: QualityPreset?, remotePreset: QualityPreset?): List<MediaFormat> {
        return getSupportedFormats(localPreset, remotePreset,
                NeomediaServiceUtils.mediaServiceImpl!!.currentEncodingConfiguration)
    }

    /**
     * Gets the list of `MediaFormat`s supported by this `MediaDevice` and enabled in
     * `encodingConfiguration`.
     *
     * @param localPreset the preset used to set some of the format parameters, used for video and settings.
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings.
     * @param encodingConfiguration the `EncodingConfiguration` instance to use
     * @return the list of `MediaFormat`s supported by this device and enabled in `encodingConfiguration`.
     * @see MediaDevice.getSupportedFormats
     */
    override fun getSupportedFormats(localPreset: QualityPreset?,
            remotePreset: QualityPreset?, encodingConfiguration: EncodingConfiguration?): List<MediaFormat> {
        val mediaServiceImpl = NeomediaServiceUtils.mediaServiceImpl!!
        val enabledEncodings = encodingConfiguration!!.getEnabledEncodings(mediaType)
        val supportedFormats = ArrayList<MediaFormat>()

        // If there is preset, check and set the format attributes where needed.
        if (enabledEncodings != null) {
            for (mediaFormat_ in enabledEncodings) {
                var mediaFormat = mediaFormat_
                if ("h264".equals(mediaFormat!!.encoding, ignoreCase = true)) {
                    val advancedAttrs = mediaFormat.advancedAttributes
                    val captureDeviceInfo = getCaptureDeviceInfo()
                    var captureDeviceInfoLocator: MediaLocator? = null
                    var sendSize: Dimension? = null

                    // change send size only for video calls
                    if (captureDeviceInfo != null && captureDeviceInfo.locator.also { captureDeviceInfoLocator = it } != null
                            && DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING != captureDeviceInfoLocator!!.protocol) {
                        if (localPreset != null) sendSize = localPreset.resolution else {
                            /*
                             * XXX We cannot default to any video size here because we do not know
                             * how this MediaDevice instance will be used. If the caller wanted to
                             * limit the video size, she would've specified an actual sendPreset.
                             */
                            // sendSize = mediaServiceImpl.getDeviceConfiguration().getVideoSize();
                        }
                    }
                    // if there is specified preset, send its settings
                    val receiveSize = if (remotePreset != null) remotePreset.resolution else {
                        // or just send the max video resolution of the PC as we do by default
                        mediaServiceImpl.defaultScreenDevice?.size
                    }
                    advancedAttrs!!["imageattr"] = MediaUtils.createImageAttr(sendSize, receiveSize)
                    mediaFormat = mediaServiceImpl.formatFactory!!.createMediaFormat(mediaFormat.encoding,
                            mediaFormat.clockRate, mediaFormat.formatParameters, advancedAttrs)
                }
                if (mediaFormat != null) supportedFormats.add(mediaFormat)
            }
        }
        return supportedFormats
    }

    /**
     * Gets a human-readable `String` representation of this instance.
     *
     * @return a `String` providing a human-readable representation of this instance
     */
    override fun toString(): String {
        val captureDeviceInfo = getCaptureDeviceInfo()
        return captureDeviceInfo?.toString() ?: super.toString()
    }

    companion object {
        /**
         * Creates a new `CaptureDevice` which traces calls to a specific `CaptureDevice`
         * for debugging purposes.
         *
         * captureDevice the `CaptureDevice` which is to have its calls traced for debugging output
         * @return a new `CaptureDevice` which traces the calls to the specified `captureDevice`
         */
        fun createTracingCaptureDevice(cDevice: CaptureDevice?): CaptureDevice? {
            var captureDevice = cDevice
            if (captureDevice is PushBufferDataSource) captureDevice = object : CaptureDeviceDelegatePushBufferDataSource(captureDevice) {
                @Throws(IOException::class)
                override fun connect() {
                    super.connect()
                    Timber.log(TimberLog.FINER, "Connected %s", toString(this.captureDevice!!))
                }

                override fun disconnect() {
                    super.disconnect()
                    Timber.log(TimberLog.FINER, "Disconnected %s", toString(this.captureDevice!!))
                }

                @Throws(IOException::class)
                override fun start() {
                    super.start()
                    Timber.log(TimberLog.FINER, "Started %s", toString(this.captureDevice!!))
                }

                @Throws(IOException::class)
                override fun stop() {
                    super.stop()
                    Timber.log(TimberLog.FINER, "Stopped %s", toString(this.captureDevice!!))
                }
            }
            return captureDevice
        }

        /**
         * Returns a human-readable representation of a specific `CaptureDevice` in the form of a `String` value.
         *
         * @param captureDevice the `CaptureDevice` to get a human-readable representation of
         * @return a `String` value which gives a human-readable representation of the specified `captureDevice`
         */
        private fun toString(captureDevice: CaptureDevice): String {
            val str = StringBuilder()
            str.append("CaptureDevice with hashCode ")
            str.append(captureDevice.hashCode())
            val append = str.append(" and captureDeviceInfo ")
            val captureDeviceInfo = captureDevice.captureDeviceInfo
            var mediaLocator: MediaLocator? = null
            if (captureDeviceInfo != null) {
                mediaLocator = captureDeviceInfo.locator
            }
            str.append(mediaLocator ?: captureDeviceInfo)
            return str.toString()
        }
    }
}