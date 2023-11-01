/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.atalk.service.neomedia.MediaService
import org.atalk.service.neomedia.codec.EncodingConfiguration
import java.io.Serializable

/**
 * An interface to get/set settings in the encodings panel.
 *
 * @author Boris Grozev
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class EncodingsRegistrationUtil : Serializable {
    /**
     * Whether to override global encoding settings.
     */
    private var overrideEncodingSettings = false

    /**
     * Encoding properties associated with this account.
     */
    private var encodingProperties: MutableMap<String, String> = HashMap()

    /**
     * Get the stored encoding properties
     *
     * @return The stored encoding properties.
     */
    fun getEncodingProperties(): MutableMap<String, String> {
        return encodingProperties
    }

    /**
     * Set the encoding properties
     *
     * @param encodingProperties The encoding properties to set.
     */
    fun setEncodingProperties(encodingProperties: MutableMap<String, String>) {
        this.encodingProperties = encodingProperties
    }

    /**
     * Whether override encodings is enabled
     *
     * @return Whether override encodings is enabled
     */
    fun isOverrideEncodings(): Boolean {
        return overrideEncodingSettings
    }

    /**
     * Set the override encodings setting to `override`
     *
     * @param override The value to set the override encoding settings to.
     */
    fun setOverrideEncodings(override: Boolean) {
        overrideEncodingSettings = override
    }

    /**
     * Loads encoding properties from given `accountID` into this encodings registration object.
     *
     * @param accountID the `AccountID` to be loaded.
     * @param mediaService the `MediaService` that will be used to create `EncodingConfiguration`.
     */
    fun loadAccount(accountID: AccountID, mediaService: MediaService) {
        val overrideEncodings = accountID.getAccountPropertyString(ProtocolProviderFactory.OVERRIDE_ENCODINGS)
        val isOverrideEncodings = java.lang.Boolean.parseBoolean(overrideEncodings)
        setOverrideEncodings(isOverrideEncodings)
        val encodingProperties = HashMap<String, String>()
        val encodingConfiguration = mediaService.createEmptyEncodingConfiguration()
        encodingConfiguration!!.loadProperties(accountID.accountProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX)
        encodingConfiguration.storeProperties(encodingProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".")
        setEncodingProperties(encodingProperties)
    }

    /**
     * Stores encoding configuration properties in given `propertiesMap`.
     *
     * @param propertiesMap the properties map that will be used.
     */
    fun storeProperties(propertiesMap: MutableMap<String, String?>) {
        propertiesMap[ProtocolProviderFactory.OVERRIDE_ENCODINGS] = java.lang.Boolean.toString(isOverrideEncodings())
        propertiesMap.putAll(getEncodingProperties())
    }

    /**
     * Creates new instance of `EncodingConfiguration` reflecting this object's encoding configuration state.
     *
     * @param mediaService the `MediaService` that will be used to create new instance of
     * `EncodingConfiguration`.
     * @return `EncodingConfiguration` reflecting this object's encoding configuration state.
     */
    fun createEncodingConfig(mediaService: MediaService): EncodingConfiguration {
        val encodingConfiguration = mediaService.createEmptyEncodingConfiguration()
        encodingConfiguration!!.loadProperties(encodingProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".")
        return encodingConfiguration
    }
}