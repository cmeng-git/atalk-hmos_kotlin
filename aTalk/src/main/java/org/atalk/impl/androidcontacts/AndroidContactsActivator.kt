/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidcontacts

import net.java.sip.communicator.service.contactsource.ContactSourceService
import net.java.sip.communicator.util.SimpleServiceActivator

/**
 * Activator of `AndroidContactSource` service.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidContactsActivator : SimpleServiceActivator<AndroidContactSource?>(ContactSourceService::class.java, "Android contacts") {
    /**
     * {@inheritDoc}
     */
    override fun createServiceImpl(): AndroidContactSource {
        return AndroidContactSource()
    }
}