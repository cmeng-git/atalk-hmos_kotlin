/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec

import org.atalk.service.libjitsi.LibJitsi
import org.atalk.service.neomedia.format.MediaFormat

/**
 * An EncodingConfiguration implementation that synchronizes its preferences with a ConfigurationService.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
class EncodingConfigurationConfigImpl(
        /**
         * Holds the prefix that will be used to store properties
         */
        private val propPrefix: String) : EncodingConfigurationImpl() {
    /**
     * The `ConfigurationService` instance that will be used to store properties
     */
    private val cfg = LibJitsi.configurationService

    /**
     * Constructor. Loads the configuration from `prefix`
     *
     * @param prefix the prefix to use when loading and storing properties
     */
    init {
        loadConfig()
    }

    /**
     * Loads the properties stored under `this.propPrefix`
     */
    private fun loadConfig() {
        val properties = HashMap<String, String?>()
        for (pName in cfg.getPropertyNamesByPrefix(propPrefix, false)) {
            properties[pName] = cfg.getString(pName)
        }
        loadProperties(properties)
    }

    /**
     * Sets the preference associated with `encoding` to `priority`, and stores the
     * appropriate property in the configuration service.
     *
     * @param encoding the `MediaFormat` specifying the encoding to set the priority of
     * @param priority a positive `int` indicating the priority of `encoding` to set
     * @see EncodingConfigurationImpl.setPriority
     */
    override fun setPriority(encoding: MediaFormat, priority: Int) {
        super.setPriority(encoding, priority)
        cfg.setProperty(propPrefix + "." + getEncodingPreferenceKey(encoding), priority)
    }
}