/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.provisioning

/**
 * Provisioning service.
 *
 * @author Sebastien Vincent
 */
interface ProvisioningService {
    /**
     * Enables the provisioning with the given method. If the provisioningMethod is null disables the provisioning.
     * Indicates if the provisioning has been enabled.
     *
     * @return `true` if the provisioning is enabled, `false` - otherwise
     */
    var provisioningMethod: String?

    /**
     * Returns the provisioning URI.
     */
    var provisioningUri: String?

    /**
     * Returns provisioning username if any.
     */
    var provisioningUsername: String?

    /**
     * @return provisioning password
     */
    var provisioningPassword: String?

}