/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.impl.neomedia.MediaServiceImpl
import java.io.IOException
import javax.media.CaptureDeviceManager
import javax.media.format.AudioFormat

/**
 * Manages the list of active (currently plugged-in) capture devices and manages user preferences
 * between all known devices (previously and actually plugged-in).
 *
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
open class CaptureDevices
/**
 * Initializes the capture device list management.
 *
 * @param audioSystem The audio system managing this capture device list.
 */
(audioSystem: AudioSystem) : Devices(audioSystem) {
    /**
     * {@inheritDoc}
     */
    override fun getDevices(): List<CaptureDeviceInfo2> {
        var devices = super.getDevices()
        if (devices.isNotEmpty()) {
            val thisDevices = ArrayList<CaptureDeviceInfo2>()
            val format = AudioFormat(AudioFormat.LINEAR, -1.0, 16, -1)

            for (device in devices) {
                for (deviceFormat in device.formats) {
                    if (deviceFormat.matches(format)) {
                        thisDevices.add(device)
                        break
                    }
                }
            }
            devices = thisDevices
        }
        return devices
    }

    /**
     * Returns the property of the capture devices.
     */
    override val propDevice: String
        get() = PROP_DEVICE

    /**
     * {@inheritDoc}
     */
    override fun setDevices(devices: List<CaptureDeviceInfo2>?) {
        super.setDevices(devices)

        if (devices != null) {
            var commit = false

            for (activeDevice in devices) {
                CaptureDeviceManager.addDevice(activeDevice)
                commit = true
            }

            if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad) {
                try {
                    CaptureDeviceManager.commit()
                } catch (ioe: IOException) {
                    // Whatever.
                }
            }
        }
    }

    companion object {
        const val PROP_DEVICE = "captureDevice"
    }
}