/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.update

/**
 * Checking for software updates service.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface UpdateService {
    /**
     * Checks for updates and notify user of any new version, and take necessary action.
     */
    fun checkForUpdates()

    /**
     * Determines whether we are currently running the latest version.
     *
     * @return `true` if we are currently running the latest version; otherwise, `false`
     */
    val isLatestVersion: Boolean

    /**
     * Gets the latest available (software) version online.
     *
     * @return the latest (software) version
     */
    fun getLatestVersion(): String?
}