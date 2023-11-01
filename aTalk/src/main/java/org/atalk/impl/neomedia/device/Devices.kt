/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device

import org.atalk.service.libjitsi.LibJitsi
import java.util.*
import javax.media.MediaLocator

/**
 * Manages the list of active (currently plugged-in) capture/notify/playback devices and manages
 * user preferences between all known devices (previously and actually plugged-in).
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class Devices
/**
 * Initializes the device list management.
 *
 * @param audioSystem The audio system managing this device list.
 */
    (
        /**
         * The audio system managing this device list.
         */
        private val audioSystem: AudioSystem,
) {
    /**
     * The selected active device.
     */
    private var device: CaptureDeviceInfo2? = null

    /**
     * The list of device ID/names saved by the configuration service and previously saved given user preference order.
     */
    private val devicePreferences = ArrayList<String>()

    /**
     * The list of `CaptureDeviceInfo2`s which are active/plugged-in.
     */
    private var devices: List<CaptureDeviceInfo2>? = null

    /**
     * Adds a new device in the preferences (at the first active position if the isSelected argument is true).
     *
     * @param newDeviceIdentifier The identifier of the device to add int first active position of the preferences.
     * @param isSelected True if the device is the selected one.
     */
    private fun addToDevicePreferences(newDeviceIdentifier: String, isSelected: Boolean) {
        synchronized(devicePreferences) {
            devicePreferences.remove(newDeviceIdentifier)
            // A selected device is placed on top of the list: this is the new preferred device.
            if (isSelected) {
                devicePreferences.add(0, newDeviceIdentifier)
            }
            else {
                devicePreferences.add(newDeviceIdentifier)
            }
        }
    }

    /**
     * Gets a `CaptureDeviceInfo2` which is known to this instance and is identified by a specific `MediaLocator`.
     *
     * @param locator the `MediaLocator` of the `CaptureDeviceInfo2` to be returned
     * @return a `CaptureDeviceInfo2` which is known to this instance and is identified by the specified `locator`
     */
    fun getDevice(locator: MediaLocator): CaptureDeviceInfo2? {
        var device: CaptureDeviceInfo2? = null
        if (devices != null) {
            for (aDevice in devices!!) {
                val aLocator = aDevice.locator
                if (locator == aLocator) {
                    device = aDevice
                    break
                }
            }
        }
        return device
    }

    /**
     * Returns the list of the `CaptureDeviceInfo2`s which are active/plugged-in.
     *
     * @return the list of the `CaptureDeviceInfo2`s which are active/plugged-in
     */
    open fun getDevices(): List<CaptureDeviceInfo2> {
        return if (this.devices == null)
            emptyList()
        else
            this.devices!!
    }

    /**
     * Returns the property of the capture devices.
     *
     * @return The property of the capture devices.
     */
    protected abstract val propDevice: String

    /**
     * Gets the selected active device.
     *
     * @param activeDevices the list of the active devices
     * @return the selected active device
     */
    fun getSelectedDevice(activeDevices: List<CaptureDeviceInfo2>?): CaptureDeviceInfo2? {
        if (activeDevices != null) {
            val property = propDevice
            loadDevicePreferences(property)
            renameOldFashionedIdentifier(activeDevices)
            val isEmptyList = devicePreferences.isEmpty()

            // Search if an active device is a new one (is not stored in the
            // preferences yet). If true, then active this device and set it as
            // default device (only for USB devices since the user has
            // deliberately plugged in the device).
            for (i in activeDevices.indices.reversed()) {
                val activeDevice = activeDevices[i]

                if (!devicePreferences.contains(activeDevice.getModelIdentifier())) {
                    // By default, select automatically the USB devices.
                    var isSelected = activeDevice.isSameTransportType("USB")
                    val cfg = LibJitsi.configurationService
                    // Deactivate the USB device automatic selection if the property is set to true.
                    if (cfg.getBoolean(PROP_DISABLE_USB_DEVICE_AUTO_SELECTION, false)) {
                        isSelected = false
                    }

                    // When initiates the first list (when there is no user
                    // preferences yet), set the Bluetooh and Airplay to the end
                    // of the list (this corresponds to move all other type
                    // of devices on top of the preference list).
                    if (isEmptyList && !activeDevice.isSameTransportType("Bluetooth")
                            && !activeDevice.isSameTransportType("AirPlay")) {
                        isSelected = true
                    }

                    // Adds the device in the preference list (to the end of the list, or on top if selected.
                    saveDevice(property, activeDevice, isSelected)
                }
            }

            // Search if an active device match one of the previously configured in the preferences.
            synchronized(devicePreferences) {
                for (devicePreference in devicePreferences) {
                    for (activeDevice in activeDevices) {
                        // If we have found the "preferred" device among active device.
                        if (devicePreference == activeDevice.getModelIdentifier()) {
                            return activeDevice
                        }
                        else if (devicePreference == NoneAudioSystem.LOCATOR_PROTOCOL) {
                            return null
                        }
                    }
                }
            }
        }

        // if nothing was found, then returns null.
        return null
    }

    /**
     * Loads device name ordered with user's preference from the `ConfigurationService`.
     *
     * @param property the name of the `ConfigurationService` property which specifies the user's preference.
     */
    private fun loadDevicePreferences(property: String) {
        val cfg = LibJitsi.configurationService
        val newProperty = audioSystem.getPropertyName(property + "_list")
        var deviceIdentifiersString = cfg.getString(newProperty)

        synchronized(devicePreferences) {
            if (deviceIdentifiersString != null) {
                devicePreferences.clear()
                // Parse the string into a device list.
                val deviceIdentifiers = deviceIdentifiersString!!.substring(2,
                    deviceIdentifiersString!!.length - 2).split("\", \"".toRegex()).toTypedArray()
                Collections.addAll(devicePreferences, *deviceIdentifiers)
            }
            else {
                // Use the old/legacy property to load the last preferred device.
                val oldProperty = audioSystem.getPropertyName(property)
                deviceIdentifiersString = cfg.getString(oldProperty)
                if (deviceIdentifiersString != null
                        && !NoneAudioSystem.LOCATOR_PROTOCOL.equals(deviceIdentifiersString, ignoreCase = true)) {
                    devicePreferences.clear()
                    devicePreferences.add(deviceIdentifiersString!!)
                }
                else {
                }
            }
        }
    }

    /**
     * Renames the old fashioned identifier (name only), into new fashioned one (UID, or name + transport type).
     *
     * @param activeDevices The list of the active devices.
     */
    private fun renameOldFashionedIdentifier(activeDevices: List<CaptureDeviceInfo2>) {
        // Renames the old fashioned device identifier for all active devices.
        for (activeDevice in activeDevices) {
            val name = activeDevice.name
            val id = activeDevice.getModelIdentifier()

            // We can only switch to the new fashioned notation, only if the OS
            // API gives us a unique identifier (different from the device name).
            if (name != id) {
                synchronized(devicePreferences) {
                    do {
                        val nameIndex = devicePreferences.indexOf(name)
                        // If there is one old fashioned identifier.
                        if (nameIndex == -1)
                            break
                        else {
                            val idIndex = devicePreferences.indexOf(id)

                            // If the corresponding new fashioned identifier does not
                            // exist, then renames the old one into the new one.
                            if (idIndex == -1)
                                devicePreferences[nameIndex] = id
                            else  // Remove the duplicate.
                                devicePreferences.removeAt(nameIndex)
                        }
                    }
                    while (true)
                }
            }
        }
    }

    /**
     * Saves the new selected device in top of the user preferences.
     *
     * @param property the name of the `ConfigurationService` property into which the user's
     * preference with respect to the specified `CaptureDeviceInfo` is to be saved
     * @param device The device selected by the user.
     * @param isSelected True if the device is the selected one.
     */
    private fun saveDevice(property: String, device: CaptureDeviceInfo2?, isSelected: Boolean) {
        val selectedDeviceIdentifier = device?.getModelIdentifier()
                ?: NoneAudioSystem.LOCATOR_PROTOCOL

        // Sorts the user preferences to put the selected device on top.
        addToDevicePreferences(selectedDeviceIdentifier, isSelected)

        // Saves the user preferences.
        writeDevicePreferences(property)
    }

    /**
     * Selects the active device.
     *
     * @param device the selected active device
     * @param save `true` to save the choice in the configuration; `false`, otherwise
     */
    fun setDevice(device: CaptureDeviceInfo2?, save: Boolean) {
        // Checks if there is a change.
        if (device == null || device != this.device) {
            val property = propDevice
            val oldValue = this.device

            // Saves the new selected device in top of the user preferences.
            if (save)
                saveDevice(property, device, true)
            this.device = device
            audioSystem.propertyChange(property, oldValue, this.device)
        }
    }

    /**
     * Sets the list of `CaptureDeviceInfo2`s which are active/plugged-in.
     *
     * @param devices the list of `CaptureDeviceInfo2`s which are active/plugged-in
     */
    open fun setDevices(devices: List<CaptureDeviceInfo2>?) {
        this.devices = if (devices == null) null else ArrayList(devices)
    }

    /**
     * Saves the device preferences and write it to the configuration file.
     *
     * property the name of the `ConfigurationService` property
     */
    private fun writeDevicePreferences(property_: String) {
        var property = property_
        val cfg = LibJitsi.configurationService
        property = audioSystem.getPropertyName(property + "_list")
        val value = StringBuilder("[\"")

        synchronized(devicePreferences) {
            val devicePreferenceCount = devicePreferences.size
            if (devicePreferenceCount != 0) {
                value.append(devicePreferences[0])
                for (i in 1 until devicePreferenceCount)
                    value.append("\", \"").append(devicePreferences[i])
            }
        }
        value.append("\"]")
        cfg.setProperty(property, value.toString())
    }

    companion object {
        /**
         * The name of the `ConfigurationService` `boolean` property which indicates
         * whether the automatic selection of USB devices must be disabled. The default value is `false`.
         */
        private const val PROP_DISABLE_USB_DEVICE_AUTO_SELECTION = "neomedia.device.disableUsbDeviceAutoSelection"
    }
}