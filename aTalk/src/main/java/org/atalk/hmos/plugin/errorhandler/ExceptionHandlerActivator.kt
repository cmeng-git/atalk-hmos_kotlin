/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.plugin.errorhandler

import net.java.sip.communicator.util.ServiceUtils
import org.atalk.service.fileaccess.FileAccessService
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class ExceptionHandlerActivator : BundleActivator {
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }

    companion object {
        private var bundleContext: BundleContext? = null

        /**
         * Returns a reference to a FileAccessService implementation currently registered in the bundle context or null if
         * no such implementation was found.
         *
         * @return a currently valid implementation of the FileAccessService .
         */
        var fileAccessService: FileAccessService? = null
            get() {
                if (field == null) {
                    field = ServiceUtils.getService(bundleContext, FileAccessService::class.java)
                }
                return field
            }
            private set
    }
}