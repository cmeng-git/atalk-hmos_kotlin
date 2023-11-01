/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.version

/**
 * The version service keeps track of the SIP Communicator version that we are currently running.
 * Other modules (such as a Help->About dialog) query and use this service in order to show the
 * current application version.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface VersionService {
    /**
     * Returns a `Version` object containing version details of the SIP Communicator
     * version that we're currently running.
     *
     * @return a `Version` object containing version details of the SIP Communicator
     * version that we're currently running.
     */
    val currentVersion: Version?
    val currentVersionCode: Long
    val currentVersionName: String?

    /**
     * Returns a Version instance corresponding to the `version` string.
     *
     * @param version
     * a version String that we have obtained by calling a `Version.toString()` method.
     * @return the `Version` object corresponding to the `version` string.
     */
    fun parseVersionString(version: String): Version?
}