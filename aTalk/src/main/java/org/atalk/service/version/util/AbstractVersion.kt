/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.version.util

import org.atalk.service.version.Version
import kotlin.math.max

/**
 * Base class for `Version` implementation that uses major, minor and nightly build id for
 * versioning purposes.
 *
 * @author Emil Ivov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class AbstractVersion
/**
 * Creates version object with custom major, minor and nightly build id.
 *
 * @param versionMajor the major version to use.
 * @param versionMinor the minor version to use.
 * @param nightlyBuildID
 * the nightly build id value for new version object.
 */ protected constructor(
        /**
         * The version major field.
         */
        override val versionMajor: Int,
        /**
         * The version minor field.
         */
        override val versionMinor: Int,
        /**
         * The nightly build id field.
         */
        nightlyBuildID: String) : Version {

    /**
     * Returns the version major of the current Jitsi version. In an example 2.3.1 version string
     * 2 is the version major. The version major number changes when a relatively extensive set
     * of new features and possibly re-architecture have been applied to the Jitsi.
     *
     * @return the version major String.
     */
    /**
     * Returns the version minor of the current Jitsi version. In an example 2.3.1 version string
     * 3 is the version minor. The version minor number changes after adding enhancements and
     * possibly new features to a given aTalk version.
     *
     * @return the version minor integer.
     */
    /**
     * If this is a nightly build, returns the build identifies (e.g. nightly-2007.12.07-06.45.17).
     * If this is not a nightly build Jitsi version, the method returns null.
     *
     * @return a String containing a nightly build identifier or null if this is a release version
     * and therefore not a nightly build
     */

    override val nightlyBuildID : String? = nightlyBuildID
        get() {
            return if (!isNightly) null else field
        }

    /**
     * Compares another `Version` object to this one and returns a negative, zero or a
     * positive integer if this version instance represents respectively an earlier, same, or
     * later version as the one indicated by the `version` parameter.
     *
     * @param version
     * the `Version` instance that we'd like to compare to this one.
     * @return a negative integer, zero, or a positive integer as this object represents a version
     * that is earlier, same, or more recent than the one referenced by the `version`
     * parameter.
     */
    override fun compareTo(version: Version?): Int {
        if (version == null) return -1
        if (versionMajor != version.versionMajor) return versionMajor - version.versionMajor
        if (versionMinor != version.versionMinor) return versionMinor - version.versionMinor
        try {
            return compareNightlyBuildIDByComponents(nightlyBuildID, version.nightlyBuildID)
        } catch (th: Throwable) {
            // if parsing fails will continue with lexicographically compare
        }
        return nightlyBuildID!!.compareTo(version.nightlyBuildID!!)
    }

    /**
     * Compares the `version` parameter to this version and returns true if and only if
     * both reference the same Jitsi version and false otherwise.
     *
     * @param version
     * the version instance that we'd like to compare with this one.
     * @return true if and only the version param references the same Jitsi version as this
     * Version instance and false otherwise.
     */
    override fun equals(version: Any?): Boolean {
        // simply compare the version strings
        return toString() == (version?.toString() ?: "null")
    }

    /**
     * @see java.lang.Object.hashCode
     */
    override fun hashCode(): Int {
        return toString().hashCode()
    }

    /**
     * Returns a String representation of this Version instance in the generic form of major
     * .minor[.nightly.build.id]. If you'd just like to obtain the version of Jitsi so that you
     * could display it (e.g. in a Help->About dialog) then all you need is calling this method.
     *
     * @return a major.minor[.build] String containing the complete Jitsi version.
     */
    override fun toString(): String {
        val versionStringBuff = StringBuffer()
        versionStringBuff.append(versionMajor.toString())
        versionStringBuff.append(".")
        versionStringBuff.append(versionMinor.toString())
        if (isPreRelease) {
            versionStringBuff.append("-")
            versionStringBuff.append(preReleaseID)
        }
        if (isNightly) {
            versionStringBuff.append(".")
            versionStringBuff.append(nightlyBuildID)
        }
        return versionStringBuff.toString()
    }

    companion object {
        /**
         * As normally nightly.build.id is in the form of <build-num> or <build-num>.<revision> we
         * will first try to compare them by splitting the id in components and compare them one by
         * one as numbers
         *
         * @param v1
         * the first version to compare
         * @param v2
         * the second version to compare
         * @return a negative integer, zero, or a positive integer as the first parameter `v1`
         * represents a version that is earlier, same, or more recent than the one referenced by the
         * `v2` parameter.
        </revision></build-num></build-num> */
        private fun compareNightlyBuildIDByComponents(v1: String?, v2: String?): Int {
            val s1 = v1!!.split("\\.".toRegex())
            val s2 = v2!!.split("\\.".toRegex())
            val len = max(s1.size, s2.size)
            for (i in 0 until len) {
                var n1 = 0
                var n2 = 0
                if (i < s1.size) n1 = s1[i].toInt()
                if (i < s2.size) n2 = s2[i].toInt()
                if (n1 != n2) return n1 - n2
            }

            // will happen if both versions have identical numbers in
            // their components (even if one of them is longer, has more components)
            return 0
        }
    }
}