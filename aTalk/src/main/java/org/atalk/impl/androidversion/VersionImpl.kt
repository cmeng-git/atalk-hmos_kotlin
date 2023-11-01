/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidversion

import net.java.sip.communicator.util.ServiceUtils.getService
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.service.resources.ResourceManagementService
import org.atalk.service.version.util.AbstractVersion

/**
 * Android version service implementation.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VersionImpl
/**
 * Creates new instance of `VersionImpl` with given major, minor and nightly build id
 * parameters.
 *
 * @param major the major version number.
 * @param minor the minor version number.
 * @param nightBuildID the nightly build id.
 */
(major: Int, minor: Int, nightBuildID: String?) : AbstractVersion(major, minor, nightBuildID!!) {

    override val isNightly = false

    /**
     * {@inheritDoc}
     */
    override val isPreRelease: Boolean
        get() = false

    /**
     * Returns the version pre-release ID of the current aTalk version and null if this version
     * is not a pre-release.
     *
     * @return a String containing the version pre-release ID.
     */
    override val preReleaseID: String?
        get() = null

    // if resource bundle is not found or the key is missing return the default name
    /*
    * XXX There is no need to have the ResourceManagementService instance as a static
    * field of the VersionImpl
    class because it will be used once only anyway.
    */
    /**
     * Returns the name of the application that we're currently running. Default MUST be aTalk.
     *
     * @return the name of the application that we're currently running. Default MUST be aTalk.
     */
    override val applicationName: String?
        get() {
            if (Companion.applicationName == null) {
                try {
                    /*
                 * XXX There is no need to have the ResourceManagementService instance as a static
                 * field of the VersionImpl class because it will be used once only anyway.
                 */
                    val resources = getService(
                            VersionActivator.bundleContext, ResourceManagementService::class.java)
                    if (resources != null) {
                        Companion.applicationName = resources.getSettingsString("service.gui.APPLICATION_NAME")
                    }
                } catch (e: Exception) {
                    // if resource bundle is not found or the key is missing return the default name
                } finally {
                    if (Companion.applicationName == null) Companion.applicationName = DEFAULT_APPLICATION_NAME
                }
            }
            return Companion.applicationName
        }

    companion object {
        /**
         * Default application name.
         */
        private val DEFAULT_APPLICATION_NAME = aTalkApp.getResString(R.string.APPLICATION_NAME)

        /**
         * The name of this application.
         */
        private var applicationName: String? = null
    }
}