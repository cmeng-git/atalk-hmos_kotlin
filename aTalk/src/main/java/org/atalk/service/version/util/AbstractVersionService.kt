/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.version.util

import org.atalk.service.version.Version
import org.atalk.service.version.VersionService
import java.util.regex.Pattern

/**
 * Base implementation of `VersionService` that uses major, minor and nightly build id
 * fields for versioning purposes.
 *
 * @author Emil Ivov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AbstractVersionService : VersionService {
    /**
     * Returns a Version instance corresponding to the `version` string.
     *
     * @param version a version String that we have obtained by calling a `Version.toString()` method.
     * @return the `Version` object corresponding to the `version` string. Or null
     * if we cannot parse the string.
     */
    override fun parseVersionString(version: String): Version? {
        val matcher = PARSE_VERSION_STRING_PATTERN.matcher(version)
        return if (matcher.matches() && matcher.groupCount() == 3) {
            createVersionImpl(matcher.group(1)!!.toInt(), matcher.group(2)!!.toInt(),
                    matcher.group(3))
        } else null
    }

    /**
     * Creates new `Version` instance specific to current implementation.
     *
     * @param majorVersion major version number.
     * @param minorVersion minor version number.
     * @param nightlyBuildId nightly build id string.
     * @return new `Version` instance specific to current implementation for given major,
     * minor and nightly build id parameters.
     */
    protected abstract fun createVersionImpl(majorVersion: Int, minorVersion: Int, nightlyBuildId: String?): Version?

    companion object {
        /**
         * The pattern that will parse strings to version object.
         */
        private val PARSE_VERSION_STRING_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.([\\d.]+)")
    }
}