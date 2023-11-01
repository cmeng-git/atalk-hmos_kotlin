/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.impl.neomedia.device.AudioSystem
import org.atalk.impl.neomedia.device.CoreAudioDevice
import org.atalk.service.neomedia.BasicVolumeControl
import timber.log.Timber

/**
 * Implementation of VolumeControl which uses system sound architecture (MacOsX or Windows
 * CoreAudio) to change input/output hardware volume.
 *
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
open class HardwareVolumeControl(mediaServiceImpl: MediaServiceImpl, volConfPropertyName: String) : BasicVolumeControl(volConfPropertyName) {
    /**
     * The media service implementation.
     */
    var mediaServiceImpl: MediaServiceImpl? = null

    /**
     * Creates volume control instance and initializes initial level value if stored in the
     * configuration service.
     *
     * mediaServiceImpl The media service implementation.
     * volConfPropertyName the name of the configuration property which specifies
     * the value of the volume level of the new instance
     */
    init {
        this.mediaServiceImpl = mediaServiceImpl

        // Gets the device volume (an error use the default volume).
        this.volumeLevel = defaultVolumeLevel
        val volume = volume
        if (volume != -1f) {
            this.volumeLevel = volume
        }
    }

    /**
     * Modifies the hardware microphone sensibility (hardware amplification).
     */
    protected fun updateHardwareVolume() {
        // Gets the selected input dvice UID.
        val deviceUID = captureDeviceUID

        // Computes the hardware volume.
        val jitsiHarwareVolumeFactor = MAX_VOLUME_LEVEL / gainReferenceLevel
        var hardwareVolumeLevel = this.volumeLevel * jitsiHarwareVolumeFactor
        if (hardwareVolumeLevel > 1.0f) {
            hardwareVolumeLevel = 1.0f
        }

        // Changes the input volume of the capture device.
        if (setInputDeviceVolume(deviceUID, hardwareVolumeLevel) != 0) {
            Timber.d("Could not change hardware input device level")
        }
    }

    /**
     * Returns the selected input device UID.
     *
     * @return The selected input device UID. Or null if not found.
     */
    private val captureDeviceUID: String?
        get() {
            return (mediaServiceImpl!!.deviceConfiguration.audioSystem?.getSelectedDevice(AudioSystem.DataFlow.CAPTURE))?.uid
        }

    /**
     * Changes the device volume via the system API.
     *
     * @param deviceUID The device ID.
     * @param volume The volume requested.
     * @return 0 if everything works fine.
     */
    private fun setInputDeviceVolume(deviceUID: String?, volume: Float): Int {
        if (deviceUID == null) {
            return -1
        }
        if (CoreAudioDevice.initDevices() == -1) {
            CoreAudioDevice.freeDevices()
            Timber.d("Could not initialize CoreAudio input devices")
            return -1
        }
        // Change the input volume of the capture device.
        if (CoreAudioDevice.setInputDeviceVolume(deviceUID, volume) != 0) {
            CoreAudioDevice.freeDevices()
            Timber.d("Could not change CoreAudio input device level")
            return -1
        }
        CoreAudioDevice.freeDevices()
        return 0
    }

    /**
     * Returns the device volume via the system API.
     *
     * @param deviceUID The device ID.
     * @Return A scalar value between 0 and 1 if everything works fine. -1 if an error occurred.
     */
    private fun getInputDeviceVolume(deviceUID: String?): Float {
        var volume: Float
        if (deviceUID == null) {
            return (-1).toFloat()
        }
        if (CoreAudioDevice.initDevices() == -1) {
            CoreAudioDevice.freeDevices()
            Timber.d("Could not initialize CoreAudio input devices")
            return (-1).toFloat()
        }
        // Get the input volume of the capture device.
        if (CoreAudioDevice.getInputDeviceVolume(deviceUID).also { volume = it } == -1f) {
            CoreAudioDevice.freeDevices()
            Timber.d("Could not get CoreAudio input device level")
            return (-1).toFloat()
        }
        CoreAudioDevice.freeDevices()
        return volume
    }
    // If the hardware voume for this device is not available, then switch to the software volume.
    /**
     * Current volume value.
     *
     * @return the current volume level.
     * @see VolumeControl
     */
    val volume: Float
        get() {
            val deviceUID = captureDeviceUID
            var volume = getInputDeviceVolume(deviceUID)
            // If the hardware voume for this device is not available, then switch
            // to the software volume.
            if (volume == -1f) {
                volume = super.volumeLevel
            }
            return volume
        }

    companion object {// Starts to activate the gain (software amplification), only once the
        // microphone sensibility is sets to its maximum (hardware amplification).
        /**
         * Returns the reference volume level for computing the gain.
         *
         * @return The reference volume level for computing the gain.
         */
        /**
         * The maximal power level used for hardware amplification. Over this value software
         * amplification is used.
         */
        protected const val gainReferenceLevel = 1.0f

        /**
         * Returns the default volume level.
         *
         * @return The default volume level.
         */
        protected val defaultVolumeLevel: Float
            // By default set the microphone at the middle of its hardware sensibility range.
            get() = gainReferenceLevel / 2
    }
}