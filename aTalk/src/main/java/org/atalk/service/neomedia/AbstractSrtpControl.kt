/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.event.SrtpListener

/**
 * Provides an abstract, base implementation of [SrtpControl] to facilitate implementers.
 *
 * @author Lyubomir Marinov
 * @author MilanKral
 * @author Eng Chong Meng
 */
abstract class AbstractSrtpControl<T : SrtpControl.TransformEngine> protected constructor(srtpControlType: SrtpControlType?) : SrtpControl {

    /**
     * {@inheritDoc}
     */
    final override val srtpControlType: SrtpControlType

    /**
     * The `SrtpListener` listening to security events (to be) fired by this `SrtpControl` instance.
     */
    override var srtpListener: SrtpListener? = null

    /**
     * The `Object`s currently registered as users of this
     * `SrtpControl` (through [.registerUser]).
     */
    private val users = HashSet<Any>()

    /**
     * Initializes a new `AbstractSrtpControl` instance with a specific `SrtpControlType`.
     *
     * @param srtpControlType the `SrtpControlType` of the new instance
     */
    init {
        if (srtpControlType == null) throw NullPointerException("srtpControlType")
        this.srtpControlType = srtpControlType
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of `AbstractSrtpControl` cleans up its associated `TransformEngine` (if any).
     */
    override fun cleanup(user: Any?) {
        synchronized(users) { if (users.remove(user) && users.isEmpty()) doCleanup() }
    }

    /**
     * Initializes a new `TransformEngine` instance to be associated with
     * and used by this `SrtpControl` instance.
     *
     * @return a new `TransformEngine` instance to be associated with and
     * used by this `SrtpControl` instance
     */
    protected abstract fun createTransformEngine(): T?

    /**
     * Prepares this `SrtpControl` for garbage collection.
     */
    protected open fun doCleanup() {
        if (transformEngine != null) {
            transformEngine!!.cleanup()
            transformEngine = null
        }
    }

    /**
     * {@inheritDoc}
     */
    override var transformEngine: T? = null
        get() {
            if (field == null) field = createTransformEngine()
            return field
        }

    /**
     * {@inheritDoc}
     *
     * The implementation of `AbstractSrtpControl` does nothing because support for
     * multistream mode is the exception rather than the norm.
     */
    override fun setMasterSession(masterSession: Boolean) {}

    /**
     * {@inheritDoc}
     *
     * The implementation of `AbstractSrtpControl` does nothing because support for
     * multistream mode is the exception rather than the norm.
     */
    override fun setMultistream(master: SrtpControl?) {}

    /**
     * {@inheritDoc}
     */
    override fun registerUser(user: Any) {
        synchronized(users) { users.add(user) }
    }
}