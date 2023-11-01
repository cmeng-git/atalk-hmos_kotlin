/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.certificate

/**
 * Data object for client certificate configuration entries.
 *
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
class CertificateConfigEntry
/**
 * Default construct with only display defined
 * @param displayName Certificate display name
 */
(var displayName: String? = null) {
    /**
     * Gets the key store type.
     */
    var keyStoreType: KeyStoreType? = null

    /**
     * Gets the key store password.
     *
     * @return the key store password
     */
    var keyStorePassword: String? = null

    fun getKSPassword(): CharArray? {
        return keyStorePassword?.toCharArray()
    }

    /**
     * Gets the alias.
     */
    var alias: String? = null

    /**
     * Gets the id.
     */
    var id: String? = null

    /**
     * Gets the key store.
     */
    var keyStore: String? = null

    /**
     * Checks if is save password.
     */
    var isSavePassword = false

    /**
     * Human readable and uniquely identify the certificate
     * Min is the displayName e.g. 'None' certificate
     *
     * @return String representing the certificate
     */
    override fun toString(): String {
        var certName = ""
        if (id != null) certName = "$id-"
        certName += displayName
        if (keyStoreType != null) certName += " [" + keyStoreType!!.name + "]"
        return certName
    }

    companion object {
        /*
         * CERT_NONE for use on android device to denote no client TLS Certificate being selected.
         */
        val CERT_NONE = CertificateConfigEntry("None")
    }
}