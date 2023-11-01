/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidversion

import android.content.pm.PackageManager
import android.os.Build
import org.atalk.hmos.aTalkApp
import org.atalk.service.version.Version
import org.atalk.service.version.util.AbstractVersionService
import timber.log.Timber

/**
 * An android version service implementation. The current version is parsed from android:versionName
 * attribute from PackageInfo.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class VersionServiceImpl : AbstractVersionService() {
    /**
     * Current version instance.
     */
    private var CURRENT_VERSION: VersionImpl? = null
    private var CURRENT_VERSION_CODE = 0L
    private var CURRENT_VERSION_NAME: String? = null

    /**
     * Creates a new instance of `VersionServiceImpl` and parses current version from
     * android:versionName attribute from PackageInfo.
     */
    init {
        val ctx = aTalkApp.globalContext
        val pckgMan = ctx.packageManager
        try {
            val pckgInfo = pckgMan.getPackageInfo(ctx.packageName, 0)
            val versionName = pckgInfo.versionName
            val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                pckgInfo.longVersionCode
            else
                pckgInfo.versionCode.toLong()
            CURRENT_VERSION_NAME = versionName
            CURRENT_VERSION_CODE = versionCode

            // cmeng - version must all be digits, otherwise no online update
            CURRENT_VERSION = parseVersionString(versionName) as VersionImpl?
            Timber.i("Device installed with atalk-hmos version: %s, version code: %s",
                CURRENT_VERSION, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Returns a `Version` object containing version details of the Jitsi version that
     * we're currently running.
     *
     * @return a `Version` object containing version details of the Jitsi version that
     * we're currently running.
     */
    override fun createVersionImpl(majorVersion: Int, minorVersion: Int, nightlyBuildId: String?): Version? {
        return VersionImpl(majorVersion, minorVersion, nightlyBuildId)
    }

    override val currentVersion: Version?
        get() = CURRENT_VERSION

    override val currentVersionCode: Long
        get() = CURRENT_VERSION_CODE

    override val currentVersionName: String?
        get() = CURRENT_VERSION_NAME
}