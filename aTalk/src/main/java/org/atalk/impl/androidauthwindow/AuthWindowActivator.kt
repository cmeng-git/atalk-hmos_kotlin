/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidauthwindow

import net.java.sip.communicator.service.gui.AuthenticationWindowService
import net.java.sip.communicator.util.SimpleServiceActivator

/**
 * Bundle activator for Android `AuthenticationWindowService` impl.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AuthWindowActivator
/**
 * Creates new instance of `AuthenticationWindowService`.
 */
    : SimpleServiceActivator<AuthenticationWindowService?>(AuthenticationWindowService::class.java, "AuthenticationWindowService") {
    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): AuthenticationWindowService {
        return AuthWindowServiceImpl()
    }
}