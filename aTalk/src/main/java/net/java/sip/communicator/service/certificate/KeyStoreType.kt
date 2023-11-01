/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.certificate

/**
 * Data object for KeyStore configurations. Primarily used during adding/
 * editing client certificate configurations.
 *
 * @author Ingo Bauersachs
 */
/**
 * Creates a new instance of this class.
 * @param name the display name of the keystore type.
 * @param fileExtensions known file name extensions (including the dot)
 * @param hasKeyStorePassword
 */
class KeyStoreType(
        val name: String,

        val fileExtensions: Array<String>,

        private val hasKeyStorePassword: Boolean,
) {
    override fun toString(): String {
        return name
    }

    /**
     * Flag that indicates if the keystore supports passwords.
     * @return `true` if the keystore supports passwords, `false`
     * otherwise.
     */
    fun hasKeyStorePassword(): Boolean {
        return hasKeyStorePassword
    }
}