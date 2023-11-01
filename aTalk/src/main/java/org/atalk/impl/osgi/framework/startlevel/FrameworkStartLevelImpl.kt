/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework.startlevel

import org.atalk.impl.osgi.framework.AsyncExecutor
import org.atalk.impl.osgi.framework.BundleImpl
import org.atalk.impl.osgi.framework.launch.FrameworkImpl
import org.osgi.framework.FrameworkListener
import org.osgi.framework.startlevel.FrameworkStartLevel
import java.util.concurrent.TimeUnit

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class FrameworkStartLevelImpl(
        private val bundle: BundleImpl) : FrameworkStartLevel {

    private val executor = AsyncExecutor<Command>(5, TimeUnit.MINUTES)
    private var initialBundleStartLevel = 0
    private var startLevel = 0

    override fun getBundle(): BundleImpl {
        return bundle
    }

    private val framework: FrameworkImpl?
        get() = getBundle().framework

    override fun getInitialBundleStartLevel(): Int {
        var initialBundleStartLevel = initialBundleStartLevel
        if (initialBundleStartLevel == 0) initialBundleStartLevel = 1
        return initialBundleStartLevel
    }

    @Synchronized
    override fun getStartLevel(): Int {
        return startLevel
    }

    fun internalSetStartLevel(startLevel: Int, vararg listeners: FrameworkListener) {
        require(startLevel >= 0) { "startLevel" }
        executor.execute(Command(startLevel, *listeners))
    }

    override fun setInitialBundleStartLevel(initialBundleStartLevel: Int) {
        require(initialBundleStartLevel > 0) { "initialBundleStartLevel" }
        this.initialBundleStartLevel = initialBundleStartLevel
    }

    override fun setStartLevel(startLevel: Int, vararg listeners: FrameworkListener) {
        require(startLevel != 0) { "startLevel" }
        internalSetStartLevel(startLevel, *listeners)
    }

    fun stop() {
        executor.shutdownNow()
    }

    private inner class Command(private val startLevel: Int, vararg listeners: FrameworkListener) : Runnable {
        private val listeners: Array<FrameworkListener>

        init {
            this.listeners = arrayOf(*listeners)
        }

        override fun run() {
            val startLevel = getStartLevel()
            val framework = framework!!
            if (startLevel < this.startLevel) {
                for (intermediateStartLevel in startLevel + 1..this.startLevel) {
                    val oldStartLevel = getStartLevel()
                    framework.startLevelChanging(oldStartLevel, intermediateStartLevel, *listeners)
                    synchronized(this@FrameworkStartLevelImpl) { this@FrameworkStartLevelImpl.startLevel = intermediateStartLevel }
                    framework.startLevelChanged(oldStartLevel, intermediateStartLevel, *listeners)
                }
            } else if (this.startLevel < startLevel) {
                for (intermediateStartLevel in startLevel downTo this.startLevel + 1) {
                    val oldStartLevel = getStartLevel()
                    val newStartLevel = intermediateStartLevel - 1
                    framework.startLevelChanging(oldStartLevel, newStartLevel, *listeners)
                    synchronized(this@FrameworkStartLevelImpl) { this@FrameworkStartLevelImpl.startLevel = newStartLevel }
                    framework.startLevelChanged(oldStartLevel, newStartLevel, *listeners)
                }
            } else {
                framework.startLevelChanging(startLevel, startLevel, *listeners)
                framework.startLevelChanged(startLevel, startLevel, *listeners)
            }
        }
    }
}