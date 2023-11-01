/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import okhttp3.internal.notifyAll
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.*
import java.util.regex.Pattern

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AudioSystem2 protected constructor(locatorProtocol: String, features: Int) : AudioSystem(locatorProtocol, features) {
    /**
     * / **
     * The number of times that [.willOpenStream] has been invoked without an intervening
     * [.didOpenStream] i.e. the number of API clients who are currently executing a
     * `Pa_OpenStream`-like function and which are thus inhibiting
     * `updateAvailableDeviceList()`.
     */
    private var openStream = 0

    /**
     * The `Object` which synchronizes that access to [.openStream] and
     * [.updateAvailableDeviceList].
     */
    private val openStreamSyncRoot = Any()

    /**
     * The number of times that [.willUpdateAvailableDeviceList] has been invoked without an
     * intervening [.didUpdateAvailableDeviceList] i.e. the number of API clients who are
     * currently executing `updateAvailableDeviceList()` and who are thus inhibiting
     * `openStream`.
     */
    private var updateAvailableDeviceList = 0

    /**
     * The list of `UpdateAvailableDeviceListListener`s which are to be notified before and
     * after this `AudioSystem`'s method `updateAvailableDeviceList()` is invoked.
     */
    private val updateAvailableDeviceListListeners = LinkedList<WeakReference<UpdateAvailableDeviceListListener>>()

    /**
     * The `Object` which ensures that this `AudioSystem`'s function to update the
     * list of available devices will not be invoked concurrently. The condition should hold true on
     * the native side but, anyway, it should not hurt (much) to enforce it on the Java side as
     * well.
     */
    private val updateAvailableDeviceListSyncRoot = Any()

    /**
     * Adds a listener which is to be notified before and after this `AudioSystem`'s method
     * `updateAvailableDeviceList()` is invoked.
     *
     *
     * **Note**: The `AudioSystem2` class keeps a `WeakReference` to the specified
     * `listener` in order to avoid memory leaks.
     *
     *
     * @param listener the `UpdateAvailableDeviceListListener` to be notified before and after this
     * `AudioSystem`'s method `updateAvailableDeviceList()` is invoked
     */
    fun addUpdateAvailableDeviceListListener(listener: UpdateAvailableDeviceListListener?) {
        if (listener == null) throw NullPointerException("listener")
        synchronized(updateAvailableDeviceListListeners) {
            val i = updateAvailableDeviceListListeners
                    .iterator()
            var add = true
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null) i.remove() else if (l == listener) add = false
            }
            if (add) {
                updateAvailableDeviceListListeners.add(WeakReference(listener))
            }
        }
    }

    /**
     * Notifies this `AudioSystem` that an API client finished executing a
     * `Pa_OpenStream`-like function.
     */
    fun didOpenStream() {
        synchronized(openStreamSyncRoot) {
            openStream--
            if (openStream < 0) openStream = 0
            (openStreamSyncRoot as Object).notifyAll()
        }
    }

    /**
     * Notifies this `AudioSystem` that a it has finished executing
     * `updateAvailableDeviceList()`.
     */
    private fun didUpdateAvailableDeviceList() {
        synchronized(openStreamSyncRoot) {
            updateAvailableDeviceList--
            if (updateAvailableDeviceList < 0) updateAvailableDeviceList = 0
            openStreamSyncRoot.notifyAll()
        }
        fireUpdateAvailableDeviceListEvent(false)
    }

    /**
     * Notifies the registered `UpdateAvailableDeviceListListener`s that this
     * `AudioSystem`'s method `updateAvailableDeviceList()` will be or was invoked.
     *
     * @param will `true` if this `AudioSystem`'s method
     * `updateAvailableDeviceList()` will be invoked or `false` if it was invoked
     */
    private fun fireUpdateAvailableDeviceListEvent(will: Boolean) {
        var ls: List<WeakReference<UpdateAvailableDeviceListListener>>
        synchronized(updateAvailableDeviceListListeners) { ls = ArrayList(updateAvailableDeviceListListeners) }
        for (wr in ls) {
            val l = wr.get()
            if (l != null) {
                try {
                    if (will) l.willUpdateAvailableDeviceList() else l.didUpdateAvailableDeviceList()
                } catch (t: Throwable) {
                    if (t is ThreadDeath) {
                        throw t
                    } else {
                        Timber.e("UpdateAvailableDeviceListListener %s failed. %s", if (will) "will" else "did", t.message)
                    }
                }
            }
        }
    }

    /**
     * Reinitializes this `AudioSystem` in order to bring it up to date with possible changes
     * in the list of available devices. Invokes `updateAvailableDeviceList()` to update the
     * devices on the native side and then [.initialize] to reflect any changes on the Java
     * side. Invoked by the native side of this `AudioSystem` when it detects that the list
     * of available devices has changed.
     *
     * @throws Exception if there was an error during the invocation of `updateAvailableDeviceList()`
     * and `DeviceSystem.initialize()`
     */
    @Throws(Exception::class)
    protected fun reinitialize() {
        synchronized(updateAvailableDeviceListSyncRoot) {
            willUpdateAvailableDeviceList()
            try {
                updateAvailableDeviceList()
            } finally {
                didUpdateAvailableDeviceList()
            }
        }

        /*
         * XXX We will likely minimize the risk of crashes on the native side even further by
         * invoking initialize() with updateAvailableDeviceList() locked. Unfortunately, that will
         * likely increase the risks of deadlocks on the Java side.
         */
        invokeDeviceSystemInitialize(this)
    }

    fun removeUpdateAvailableDeviceListListener(listener: UpdateAvailableDeviceListListener?) {
        if (listener == null) return
        synchronized(updateAvailableDeviceListListeners) {
            val i = updateAvailableDeviceListListeners
                    .iterator()
            while (i.hasNext()) {
                val l = i.next().get()
                if (l == null || l == listener) i.remove()
            }
        }
    }

    protected abstract fun updateAvailableDeviceList()

    /**
     * Waits for all API clients to finish executing a `Pa_OpenStream` -like function.
     */
    private fun waitForOpenStream() {
        var interrupted = false
        while (openStream > 0) {
            try {
                (openStreamSyncRoot as Object).wait()
            } catch (ie: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    /**
     * Waits for all API clients to finish executing `updateAvailableDeviceList()`.
     */
    private fun waitForUpdateAvailableDeviceList() {
        var interrupted = false
        while (updateAvailableDeviceList > 0) {
            try {
                (openStreamSyncRoot as Object).wait()
            } catch (ie: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }

    /**
     * Notifies this `AudioSystem` that an API client will start executing a
     * `Pa_OpenStream`-like function.
     */
    fun willOpenStream() {
        synchronized(openStreamSyncRoot) {
            waitForUpdateAvailableDeviceList()
            openStream++
            (openStreamSyncRoot as Object).notifyAll()
        }
    }

    /**
     * Notifies this `AudioSystem` that it will start executing `updateAvailableDeviceList()`.
     */
    private fun willUpdateAvailableDeviceList() {
        synchronized(openStreamSyncRoot) {
            waitForOpenStream()
            updateAvailableDeviceList++
            (openStreamSyncRoot as Object).notifyAll()
        }
        fireUpdateAvailableDeviceListEvent(true)
    }

    companion object {
        /**
         * Sorts a specific list of `CaptureDeviceInfo2`s so that the ones representing USB
         * devices appear at the beginning/top of the specified list.
         *
         * @param devices the list of `CaptureDeviceInfo2`s to be sorted so that the ones representing
         * USB devices appear at the beginning/top of the list
         */
        @JvmStatic
        protected fun bubbleUpUsbDevices(devices: MutableList<CaptureDeviceInfo2>) {
            if (devices.isNotEmpty()) {
                val nonUsbDevices = ArrayList<CaptureDeviceInfo2>(devices.size)
                val i = devices.iterator()
                while (i.hasNext()) {
                    val d = i.next()
                    if (!d.isSameTransportType("USB")) {
                        nonUsbDevices.add(d)
                        i.remove()
                    }
                }
                if (nonUsbDevices.isNotEmpty()) {
                    devices.addAll(nonUsbDevices)
                }
            }
        }

        /**
         * Attempts to reorder specific lists of capture and playback/notify `CaptureDeviceInfo2`
         * s so that devices from the same hardware appear at the same indices in the respective lists.
         * The judgment with respect to the belonging to the same hardware is based on the names of the
         * specified `CaptureDeviceInfo2`s. The implementation is provided as a fallback to stand
         * in for scenarios in which more accurate relevant information is not available.
         *
         * @param captureDevices
         * @param playbackDevices
         */
        @JvmStatic
        protected fun matchDevicesByName(captureDevices: MutableList<CaptureDeviceInfo2>,
                playbackDevices: MutableList<CaptureDeviceInfo2>) {
            val captureIter = captureDevices.iterator()
            val pattern = Pattern.compile(
                    "array|headphones|microphone|speakers|\\s|\\(|\\)", Pattern.CASE_INSENSITIVE)
            val captureDevicesWithPlayback = LinkedList<CaptureDeviceInfo2>()
            val playbackDevicesWithCapture = LinkedList<CaptureDeviceInfo2>()
            var count = 0
            while (captureIter.hasNext()) {
                val captureDevice = captureIter.next()
                var captureName = captureDevice.name
                if (captureName != null) {
                    captureName = pattern.matcher(captureName).replaceAll("")
                    if (captureName.isNotEmpty()) {
                        val playbackIter = playbackDevices.iterator()
                        var matchingPlaybackDevice: CaptureDeviceInfo2? = null
                        while (playbackIter.hasNext()) {
                            val playbackDevice = playbackIter.next()
                            var playbackName = playbackDevice.name
                            if (playbackName != null) {
                                playbackName = pattern.matcher(playbackName).replaceAll("")
                                if (captureName == playbackName) {
                                    playbackIter.remove()
                                    matchingPlaybackDevice = playbackDevice
                                    break
                                }
                            }
                        }
                        if (matchingPlaybackDevice != null) {
                            captureIter.remove()
                            captureDevicesWithPlayback.add(captureDevice)
                            playbackDevicesWithCapture.add(matchingPlaybackDevice)
                            count++
                        }
                    }
                }
            }
            for (i in count - 1 downTo 0) {
                captureDevices.add(0, captureDevicesWithPlayback[i])
                playbackDevices.add(0, playbackDevicesWithCapture[i])
            }
        }
    }
}