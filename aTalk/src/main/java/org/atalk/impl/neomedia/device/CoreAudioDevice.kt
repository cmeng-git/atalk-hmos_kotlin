/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.util.OSUtils
import org.atalk.util.StringUtils.newString
import timber.log.Timber

/**
 * JNI link to the MacOsX / Windows CoreAudio library.
 *
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
object CoreAudioDevice {
    /**
     * Tells if the CoreAudio library used by this CoreAudioDevice is correctly loaded: if we are
     * under a supported operating system.
     */
    @JvmField
    var isLoaded = false

    /**
     * Loads CoreAudioDevice if we are using MacOsX or Windows Vista/7/8.
     */
    init {
        var isLoaded = false
        try {
            if (OSUtils.IS_MAC) {
                System.loadLibrary("jnmaccoreaudio")
                isLoaded = true
            } else if (OSUtils.IS_WINDOWS) {
                System.loadLibrary("jnwincoreaudio")
                isLoaded = true
            }
        } catch (npe: NullPointerException) {
            /*
             * Swallow whatever exceptions are known to be thrown by System.loadLibrary() because
             * the class has to be loaded in order to not prevent the loading of its users and
             * isLoaded will remain false eventually.
             */
            Timber.i(npe, "Failed to load CoreAudioDevice library.")
        } catch (npe: UnsatisfiedLinkError) {
            Timber.i(npe, "Failed to load CoreAudioDevice library.")
        } catch (npe: SecurityException) {
            Timber.i(npe, "Failed to load CoreAudioDevice library.")
        }
        CoreAudioDevice.isLoaded = isLoaded
    }

    @JvmStatic
    external fun freeDevices()
    @JvmStatic
    fun getDeviceModelIdentifier(deviceUID: String?): String? {
        // Prevent an access violation in getDeviceModelIdentifierBytes.
        if (deviceUID == null) throw NullPointerException("deviceUID")
        val deviceModelIdentifierBytes = getDeviceModelIdentifierBytes(deviceUID)
        return newString(deviceModelIdentifierBytes)
    }

    external fun getDeviceModelIdentifierBytes(deviceUID: String?): ByteArray
    fun getDeviceName(deviceUID: String?): String? {
        val deviceNameBytes = getDeviceNameBytes(deviceUID)
        return newString(deviceNameBytes)
    }

    external fun getDeviceNameBytes(deviceUID: String?): ByteArray
    external fun getInputDeviceVolume(deviceUID: String?): Float
    external fun getOutputDeviceVolume(deviceUID: String?): Float
    @JvmStatic
    external fun initDevices(): Int
    external fun setInputDeviceVolume(deviceUID: String?, volume: Float): Int
    external fun setOutputDeviceVolume(deviceUID: String?, volume: Float): Int
    private var devicesChangedCallback: Runnable? = null

    /**
     * Implements a callback which gets called by the native coreaudio counterpart to notify the
     * Java counterpart that the list of devices has changed.
     */
    fun devicesChangedCallback() {
        val devicesChangedCallback = devicesChangedCallback
        devicesChangedCallback?.run()
    }

    fun setDevicesChangedCallback(devicesChangedCallback: Runnable?) {
        CoreAudioDevice.devicesChangedCallback = devicesChangedCallback
    }

    fun log(error: ByteArray?) {
        val errorString = newString(error)
        Timber.i("Audio error: %s", errorString)
    }
}