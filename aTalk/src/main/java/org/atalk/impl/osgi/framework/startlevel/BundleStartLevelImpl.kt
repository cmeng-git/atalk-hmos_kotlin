/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.startlevel

import org.atalk.impl.osgi.framework.BundleImpl
import org.osgi.framework.startlevel.BundleStartLevel
import org.osgi.framework.startlevel.FrameworkStartLevel

/**
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class BundleStartLevelImpl(private val bundle: BundleImpl) : BundleStartLevel {
    private var startLevel = 0
    override fun getBundle(): BundleImpl {
        return bundle
    }

    override fun getStartLevel(): Int {
        var startLevel = startLevel
        if (startLevel == 0) {
            val frameworkStartLevel = getBundle().framework!!.adapt(FrameworkStartLevel::class.java)
            startLevel = frameworkStartLevel!!.initialBundleStartLevel ?: 1
        }
        return startLevel
    }

    override fun isActivationPolicyUsed(): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun isPersistentlyStarted(): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun setStartLevel(startLevel: Int) {
        require((startLevel <= 0) || (getBundle().bundleId != 0L)) { "startLevel" }
        this.startLevel = startLevel
    }
}