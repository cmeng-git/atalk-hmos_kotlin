/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.MediaServiceImpl
import org.atalk.util.MediaType
import org.atalk.util.OSUtils
import org.atalk.util.event.PropertyChangeNotifier
import timber.log.Timber
import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.*
import javax.media.*
import javax.media.format.AudioFormat
import javax.media.format.VideoFormat

/**
 * Represents the base of a supported device system/backend such as DirectShow, PortAudio,
 * PulseAudio, QuickTime, video4linux2. A `DeviceSystem` is initialized at a certain time
 * (usually, during the initialization of the `MediaService` implementation which is going to
 * use it) and it registers with FMJ the `CaptureDevice`s it will provide. In addition to
 * providing the devices for the purposes of capture, a `DeviceSystem` also provides the
 * devices on which playback is to be performed i.e. it acts as a `Renderer` factory via its
 * [.createRenderer] method.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class DeviceSystem protected constructor(
        mediaType: MediaType, locatorProtocol: String,
        features: Int = 0,
) : PropertyChangeNotifier() {
    /**
     * Gets the flags indicating the optional features supported by this `DeviceSystem`.
     *
     * @return the flags indicating the optional features supported by this `DeviceSystem`.
     * The possible flags are among the `FEATURE_XXX` constants defined by the
     * `DeviceSystem` class and its extenders.
     */
    /**
     * The set of flags indicating which optional features are supported by this
     * `DeviceSystem`. For example, the presence of the flag [.FEATURE_REINITIALIZE]
     * indicates that this instance is able to deal with multiple consecutive invocations of its
     * [.initialize] method.
     */
    val features: Int

    /**
     * Gets the protocol of the `MediaLocator`s of the `CaptureDeviceInfo`s (to be)
     * registered (with FMJ) by this `DeviceSystem`. The protocol is a unique identifier of a
     * `DeviceSystem`.
     *
     * @return the protocol of the `MediaLocator`s of the `CaptureDeviceInfo`s (to be)
     * registered (with FMJ) by this `DeviceSystem`
     */
    /**
     * The protocol of the `MediaLocator` of the `CaptureDeviceInfo`s (to be)
     * registered (with FMJ) by this `DeviceSystem`. The protocol is a unique identifier of a
     * `DeviceSystem`.
     */
    val locatorProtocol: String

    /**
     * The `MediaType` of this `DeviceSystem` i.e. the type of the media that this
     * instance supports for capture and playback such as audio or video.
     */
    val mediaType: MediaType

    init {
        // if (mediaType == null) throw NullPointerException("mediaType")
        // if (locatorProtocol == null) throw NullPointerException("locatorProtocol")
        this.mediaType = mediaType
        this.locatorProtocol = locatorProtocol
        this.features = features
        invokeDeviceSystemInitialize(this)
    }

    /**
     * Initializes a new `Renderer` instance which is to perform playback on a device
     * contributed by this system.
     *
     * @return a new `Renderer` instance which is to perform playback on a device contributed
     * by this system or `null`
     */
    open fun createRenderer(): Renderer? {
        val className = rendererClassName
        if (className != null) {
            try {
                return Class.forName(className).newInstance() as Renderer
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t
                else {
                    Timber.e(t, "Failed to initialize a new %s instance", className)
                }
            }
        }
        return null
    }

    /**
     * Invoked by [.initialize] to perform the very logic of the initialization of this
     * `DeviceSystem`. This instance has been prepared for initialization by an earlier call
     * to [.preInitialize] and the initialization will be completed with a subsequent call
     * to [.postInitialize].
     *
     * @throws Exception if an error occurs during the initialization of this instance. The initialization of
     * this instance will be completed with a subsequent call to `postInitialize()`
     * regardless of any `Exception` thrown by `doInitialize()`.
     */
    @Throws(Exception::class)
    protected abstract fun doInitialize()

    /**
     * Returns the format depending on the media type: AudioFormat for AUDIO, VideoFormat for VIDEO.
     * Otherwise, returns null.
     *
     * @return The format depending on the media type: AudioFormat for AUDIO, VideoFormat for VIDEO.
     * Otherwise, returns null.
     */
    val format: Format?
        get() {
            val format = when (mediaType) {
                MediaType.AUDIO -> AudioFormat(null)
                MediaType.VIDEO -> VideoFormat(null)
                else -> null
            }
            return format
        }

    /**
     * Gets the name of the class which implements the `Renderer` interface to render media
     * on a playback or notification device associated with this `DeviceSystem`. Invoked by
     * [.createRenderer].
     *
     * @return the name of the class which implements the `Renderer` interface to render
     * media on a playback or notification device associated with this `DeviceSystem`
     * or `null` if no `Renderer` instance is to be created by the
     * `DeviceSystem` implementation or `createRenderer(boolean) is overridden.
    ` */
    protected open val rendererClassName: String?
        get() = null

    /**
     * Initializes this `DeviceSystem` i.e. represents the native/system devices in the terms
     * of the application so that they may be utilized. For example, the capture devices are
     * represented as `CaptureDeviceInfo` instances registered with FMJ.
     *
     *
     * **Note**: The method is synchronized on this instance in order to guarantee that the whole
     * initialization procedure (which includes [.doInitialize]) executes once at any given time.
     *
     *
     * @throws Exception if an error occurs during the initialization of this `DeviceSystem`
     */
    @Synchronized
    @Throws(Exception::class)
    protected fun initialize() {
        preInitialize()
        try {
            doInitialize()
        } finally {
            postInitialize()
        }
    }

    /**
     * Invoked as part of the execution of [.initialize] after the execution of
     * [.doInitialize] regardless of whether the latter completed successfully. The
     * implementation of `DeviceSystem` fires a new `PropertyChangeEvent` to notify
     * that the value of the property [.PROP_DEVICES] of this instance may have changed i.e.
     * that the list of devices detected by this instance may have changed.
     */
    @Throws(Exception::class)
    protected open fun postInitialize() {
        try {
            val format = format
            if (format != null) {
                /*
                 * Calculate the lists of old and new devices and report them in a
                 * PropertyChangeEvent about PROP_DEVICES.
                 */
                val cdis = CaptureDeviceManager.getDeviceList(format)
                val postInitializeDevices = ArrayList(cdis)

                if (preInitializeDevices != null) {
                    val preIter = preInitializeDevices!!.iterator()
                    while (preIter.hasNext()) {
                        if (postInitializeDevices.remove(preIter.next())) preIter.remove()
                    }
                }

                /*
                 * Fire a PropertyChangeEvent but only if there is an actual change in the value of
                 * the property.
                 */
                val preInitializeDeviceCount = if (preInitializeDevices == null) 0 else preInitializeDevices!!.size
                if (preInitializeDeviceCount != 0 || postInitializeDevices.size != 0) {
                    firePropertyChange(PROP_DEVICES, preInitializeDevices, postInitializeDevices)
                }
            }
        } finally {
            preInitializeDevices = null
        }
    }

    /**
     * Invoked as part of the execution of [.initialize] before the execution of
     * [.doInitialize]. The implementation of `DeviceSystem` removes from FMJ's
     * `CaptureDeviceManager` the `CaptureDeviceInfo`s whose `MediaLocator` has
     * the same protocol as [.getLocatorProtocol] of this instance.
     */
    @Throws(Exception::class)
    protected open fun preInitialize() {
        val format = format
        if (format != null) {
            val cdis = CaptureDeviceManager.getDeviceList(format) as MutableList<CaptureDeviceInfo>
            preInitializeDevices = ArrayList(cdis)
            if (cdis.size > 0) {
                var commit = false
                for (cdi in filterDeviceListByLocatorProtocol(cdis, locatorProtocol)!!) {
                    CaptureDeviceManager.removeDevice(cdi)
                    commit = true
                }
                if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad) {
                    try {
                        CaptureDeviceManager.commit()
                    } catch (ioe: IOException) {
                        /*
                         * We do not really need commit but we have it for historical reasons.
                         */
                        Timber.d(ioe, "Failed to commit CaptureDeviceManager")
                    }
                }
            }
        }
    }

    /**
     * Returns a human-readable representation of this `DeviceSystem`. The implementation of
     * `DeviceSystem` returns the protocol of the `MediaLocator`s of the
     * `CaptureDeviceInfo`s (to be) registered by this `DeviceSystem`.
     *
     * @return a `String` which represents this `DeviceSystem` in a human-readable form
     */
    override fun toString(): String {
        return locatorProtocol
    }

    companion object {
        /**
         * The list of `DeviceSystem`s which have been initialized.
         */
        private val deviceSystems = LinkedList<DeviceSystem?>()

        /**
         * The constant/flag (to be) returned by [.getFeatures] in order to indicate that the
         * respective `DeviceSystem` supports invoking its [.initialize] more than once.
         */
        const val FEATURE_REINITIALIZE = 1
        const val LOCATOR_PROTOCOL_ANDROIDCAMERA = "androidcamera"
        const val LOCATOR_PROTOCOL_CIVIL = "civil"
        const val LOCATOR_PROTOCOL_DIRECTSHOW = "directshow"
        const val LOCATOR_PROTOCOL_IMGSTREAMING = "imgstreaming"

        /**
         * The protocol of the `MediaLocator`s identifying `MediaRecorder` capture devices.
         */
        const val LOCATOR_PROTOCOL_MEDIARECORDER = "mediarecorder"
        const val LOCATOR_PROTOCOL_QUICKTIME = "quicktime"
        const val LOCATOR_PROTOCOL_VIDEO4LINUX2 = "video4linux2"

        /**
         * The list of `CaptureDeviceInfo`s representing the devices of this instance at the time
         * its [.preInitialize] method was last invoked.
         */
        private var preInitializeDevices: MutableList<CaptureDeviceInfo>? = null
        const val PROP_DEVICES = "devices"

        /**
         * Returns a `List` of `CaptureDeviceInfo`s which are elements of a specific
         * `List` of `CaptureDeviceInfo`s and have a specific `MediaLocator` protocol.
         *
         * @param deviceList the `List` of `CaptureDeviceInfo` which are to be filtered based on the
         * specified `MediaLocator` protocol
         * @param locatorProtocol the protocol of the `MediaLocator`s of the `CaptureDeviceInfo`s
         * which are to be returned
         *
         * @return a `List` of `CaptureDeviceInfo`s which are elements of the specified
         * `deviceList` and have the specified `locatorProtocol`
         */
        protected fun filterDeviceListByLocatorProtocol(
                deviceList: MutableList<CaptureDeviceInfo>?, locatorProtocol: String,
        ): List<CaptureDeviceInfo>? {
            if (deviceList != null && deviceList.size > 0) {
                val deviceListIter = deviceList.iterator()
                while (deviceListIter.hasNext()) {
                    val locator = deviceListIter.next().locator
                    if (locator == null || !locatorProtocol.equals(locator.protocol, ignoreCase = true)) {
                        deviceListIter.remove()
                    }
                }
            }
            return deviceList
        }

        fun getDeviceSystems(mediaType: MediaType): Array<DeviceSystem?> {
            var ret: MutableList<DeviceSystem?>

            synchronized(deviceSystems) {
                ret = ArrayList(deviceSystems.size)
                for (deviceSystem in deviceSystems) {
                    if (deviceSystem!!.mediaType == mediaType)
                        ret.add(deviceSystem)
                }
            }
            return ret.toTypedArray()
        }

        /**
         * Initializes the `DeviceSystem` instances which are to represent the supported device
         * systems/backends such as DirectShow, PortAudio, PulseAudio, QuickTime, video4linux2. The
         * method may be invoked multiple times. If a `DeviceSystem` has been initialized by a
         * previous invocation of the method, its [.initialize] method will be called again as
         * part of the subsequent invocation only if the `DeviceSystem` in question returns a set
         * of flags from its [.getFeatures] method which contains the constant/flag
         * [.FEATURE_REINITIALIZE].
         */
        fun initializeDeviceSystems() {
            /*
             * Detect the audio capture devices unless the configuration explicitly states that they are
             * to not be detected.
             */
            if (MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO)) {
                Timber.i("Initializing audio devices")
                initializeDeviceSystems(MediaType.AUDIO)
            }

            /*
             * Detect the video capture devices unless the configuration explicitly states that they are
             * to not be detected.
             */
            if (MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO)) {
                Timber.i("Initializing video devices")
                initializeDeviceSystems(MediaType.VIDEO)
            }
        }

        /**
         * Initializes the `DeviceSystem` instances which are to represent the supported device
         * systems/backends which are to capable of capturing and playing back media of a specific type
         * such as audio or video.
         *
         * @param mediaType the `MediaType` of the `DeviceSystem`s to be initialized
         */
        fun initializeDeviceSystems(mediaType: MediaType) {
            /*
             * The list of supported DeviceSystem implementations if hard-coded. The order of the
             * classes is significant and represents a decreasing preference with respect to which
             * DeviceSystem is to be picked up as the default one (for the specified mediaType, of course).
             */
            val classNames = when (mediaType) {
                MediaType.AUDIO -> arrayOf(
                    ".AudioRecordSystem",
                    ".OpenSLESSystem",
                    ".AudioSilenceSystem",
                    ".NoneAudioSystem"
                )
                MediaType.VIDEO -> arrayOf( // MediaRecorderSystem not working for API-23; so remove the support
                    // OSUtils.IS_ANDROID ? ".MediaRecorderSystem" : null,
                    ".AndroidCameraSystem",
                    ".ImgStreamingSystem"
                )
                else -> throw IllegalArgumentException("mediaType")
            }
            initializeDeviceSystems(classNames)
        }

        /**
         * Initializes the `DeviceSystem` instances specified by the names of the classes which
         * implement them. If a `DeviceSystem` instance has already been initialized for a
         * specific class name, no new instance of the class in question will be initialized and rather
         * the [.initialize] method of the existing `DeviceSystem` instance will be
         * invoked if the `DeviceSystem` instance returns a set of flags from its
         * [.getFeatures] which contains [.FEATURE_REINITIALIZE].
         *
         * @param classNames the names of the classes which extend the `DeviceSystem` class
         * and instances of which are to be initialized.
         */
        private fun initializeDeviceSystems(classNames: Array<String>) {
            synchronized(deviceSystems) {
                var packageName: String? = null
                for (className_ in classNames) {
                    var className = className_
                    if (className.startsWith(".")) {
                        if (packageName == null) packageName = DeviceSystem::class.java.getPackage()?.name
                        className = packageName + className
                    }

                    // we can explicitly disable an audio system
                    if (java.lang.Boolean.getBoolean("$className.disabled"))
                        continue

                    // Initialize a single instance per className.
                    var deviceSystem: DeviceSystem? = null
                    for (aDeviceSystem in deviceSystems) {
                        if (aDeviceSystem!!.javaClass.name == className) {
                            deviceSystem = aDeviceSystem
                            break
                        }
                    }
                    var reinitialize: Boolean
                    if (deviceSystem == null) {
                        reinitialize = false
                        var o: Any? = null
                        try {
                            o = Class.forName(className).newInstance()
                        } catch (e: ClassNotFoundException) {
                            Timber.e("Class not found: %s", e.message)
                        } catch (t: Throwable) {
                            if (t is ThreadDeath) {
                                Timber.e("Fatal error while initialize Device Systems: %s; %s", className, t.message)
                                throw t
                            }
                            else {
                                Timber.w("Initialize failed: %s; %s", className, t.message)
                            }
                        }
                        if (o is DeviceSystem) {
                            deviceSystem = o
                            if (!deviceSystems.contains(deviceSystem)) deviceSystems.add(deviceSystem)
                            Timber.d("Initialize successful: %s", className)
                        }
                    }
                    else {
                        reinitialize = true
                    }

                    // Reinitializing is an optional feature.
                    if (reinitialize && deviceSystem!!.features and FEATURE_REINITIALIZE != 0) {
                        try {
                            invokeDeviceSystemInitialize(deviceSystem)
                        } catch (t: Throwable) {
                            if (t is ThreadDeath) {
                                Timber.e("Fatal error while initialize Device Systems: %s; %s", className, t.message)
                                throw t
                            }
                            else {
                                Timber.w("Failed to initialize %s; %s", className, t.message)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Invokes [.initialize] on a specific `DeviceSystem`. The method returns after
         * the invocation returns.
         *
         * @param deviceSystem the `DeviceSystem` to invoke `initialize()` on
         *
         * @throws Exception if an error occurs during the initialization of `initialize()` on the
         * specified `deviceSystem`
         */
        @Throws(Exception::class)
        fun invokeDeviceSystemInitialize(deviceSystem: DeviceSystem?) {
            invokeDeviceSystemInitialize(deviceSystem, false)
        }

        /**
         * Invokes [.initialize] on a specific `DeviceSystem`.
         *
         * @param deviceSystem the `DeviceSystem` to invoke `initialize()` on
         * @param asynchronous `true` if the invocation is to be performed in a separate thread and the method
         * is to return immediately without waiting for the invocation to return; otherwise, `false`
         *
         * @throws Exception if an error occurs during the initialization of `initialize()` on the
         * specified `deviceSystem`
         */
        @Throws(Exception::class)
        private fun invokeDeviceSystemInitialize(deviceSystem: DeviceSystem?, asynchronous: Boolean) {
            if (OSUtils.IS_WINDOWS || asynchronous) {
                /*
                 * The use of Component Object Model (COM) technology is common on Windows. The
                 * initialization of the COM library is done per thread. However, there are multiple
                 * concurrency models which may interfere among themselves. Dedicate a new thread on
                 * which the COM library has surely not been initialized per invocation of initialize().
                 */
                val className = deviceSystem!!.javaClass.name
                val exception = arrayOfNulls<Throwable>(1)
                val thread = object : Thread("$className.initialize()") {
                    override fun run() {
                        try {
                            Timber.log(TimberLog.FINER, "Will initialize %s", className)
                            deviceSystem.initialize()
                            Timber.log(TimberLog.FINER, "Did initialize %s", className)
                        } catch (t: Throwable) {
                            exception[0] = t
                            if (t is ThreadDeath) throw t
                        }
                    }
                }
                thread.isDaemon = true
                thread.start()
                if (asynchronous) return

                /*
                 * Wait for the initialize() invocation on deviceSystem to return i.e. the thread to die.
                 */
                var interrupted = false
                while (thread.isAlive) {
                    try {
                        thread.join()
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                }
                if (interrupted) Thread.currentThread().interrupt()

                /* Re-throw any exception thrown by the thread. */
                val t = exception[0]
                if (t != null) {
                    if (t is Exception) throw (t as Exception?)!! else throw UndeclaredThrowableException(t)
                }
            }
            else {
                deviceSystem!!.initialize()
            }
        }
    }
}