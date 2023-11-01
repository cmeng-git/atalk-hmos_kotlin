/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

import org.atalk.service.neomedia.control.KeyFrameControl.KeyFrameRequestee
import org.atalk.service.neomedia.control.KeyFrameControl.KeyFrameRequester
import java.util.*

/**
 * Provides a default implementation of [KeyFrameControl].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class KeyFrameControlAdapter : KeyFrameControl {

    /**
     * The `KeyFrameRequestee`s made available by this `KeyFrameControl`.
     */
    private var keyFrameRequestees = ArrayList<KeyFrameRequestee>(0)

    /**
     * The `KeyFrameRequester`s made available by this `KeyFrameControl`.
     */
    private var keyFrameRequesters = ArrayList<KeyFrameRequester>(0)

    /**
     * An unmodifiable view of [.keyFrameRequestees] appropriate to be returned by [.getKeyFrameRequestees].
     */
    private var unmodifiableKeyFrameRequestees: List<KeyFrameRequestee>? = null

    /**
     * An unmodifiable view of [.keyFrameRequesters] appropriate to be returned by [.getKeyFrameRequesters].
     */
    private var unmodifiableKeyFrameRequesters: List<KeyFrameRequester>? = null

    /**
     * Implements [KeyFrameControl.addKeyFrameRequestee].
     */
    override fun addKeyFrameRequestee(index: Int, keyFrameRequestee: KeyFrameRequestee) {
        var index = index
        synchronized(this) {
            if (!keyFrameRequestees.contains(keyFrameRequestee)) {
                val newKeyFrameRequestees = ArrayList<KeyFrameRequestee>(getKeyFrameRequestees().size + 1)
                newKeyFrameRequestees.addAll(getKeyFrameRequestees())
                /*
				 * If this KeyFrameControl is to determine the index at which keyFrameRequestee is
				 * to be added according to its own internal logic, then it will prefer
				 * KeyFrameRequestee implementations from outside of neomedia rather than from its inside.
				 */
                if (-1 == index) {
                    index = if (keyFrameRequestee.javaClass.name.contains(".neomedia.")) newKeyFrameRequestees.size else 0
                }
                newKeyFrameRequestees.add(index, keyFrameRequestee)

                keyFrameRequestees = newKeyFrameRequestees
                unmodifiableKeyFrameRequestees = null
            }
        }
    }

    /**
     * Implements [KeyFrameControl.addKeyFrameRequester].
     */
    override fun addKeyFrameRequester(index: Int, keyFrameRequester: KeyFrameRequester) {
        var index = index
        synchronized(this) {
            if (!keyFrameRequesters.contains(keyFrameRequester)) {
                val newKeyFrameRequesters = ArrayList<KeyFrameRequester>(getKeyFrameRequesters().size + 1)
                newKeyFrameRequesters.addAll(getKeyFrameRequesters())
                /*
				 * If this KeyFrameControl is to determine the index at which keyFrameRequester is
				 * to be added according to its own internal logic, then it will prefer
				 * KeyFrameRequester implementations from outside of neomedia rather than from its inside.
				 */
                if (-1 == index) {
                    index = if (keyFrameRequester.javaClass.name.contains(".neomedia.")) newKeyFrameRequesters.size else 0
                }
                newKeyFrameRequesters.add(index, keyFrameRequester)

                keyFrameRequesters = newKeyFrameRequesters
                unmodifiableKeyFrameRequesters = null
            }
        }
    }

    /**
     * Implements [KeyFrameControl.getKeyFrameRequestees].
     */
    override fun getKeyFrameRequestees(): List<KeyFrameRequestee> {
        synchronized(this) {
            if (unmodifiableKeyFrameRequestees == null) {
                unmodifiableKeyFrameRequestees = Collections.unmodifiableList(keyFrameRequestees)
            }
            return unmodifiableKeyFrameRequestees!!
        }
    }

    /**
     * Implements [KeyFrameControl.getKeyFrameRequesters].
     */
    override fun getKeyFrameRequesters(): List<KeyFrameRequester> {
        synchronized(this) {
            if (unmodifiableKeyFrameRequesters == null) {
                unmodifiableKeyFrameRequesters = Collections.unmodifiableList(keyFrameRequesters)
            }
            return unmodifiableKeyFrameRequesters!!
        }
    }

    /**
     * Implements [KeyFrameControl.keyFrameRequest].
     *
     *
     * {@inheritDoc}
     */
    override fun keyFrameRequest(): Boolean {
        for (keyFrameRequestee in getKeyFrameRequestees()) {
            try {
                if (keyFrameRequestee.keyFrameRequest()) return true
            } catch (e: Exception) {
                // A KeyFrameRequestee has malfunctioned, do not let it interfere with the others.
            }
        }
        return false
    }

    /**
     * Implements [KeyFrameControl.removeKeyFrameRequestee].
     *
     *
     * {@inheritDoc}
     */
    override fun removeKeyFrameRequestee(keyFrameRequestee: KeyFrameRequestee): Boolean {
        synchronized(this) {
            val index = keyFrameRequestees.indexOf(keyFrameRequestee)
            return if (-1 != index) {
                val newKeyFrameRequestees = ArrayList(keyFrameRequestees)
                newKeyFrameRequestees.removeAt(index)
                keyFrameRequestees = newKeyFrameRequestees
                unmodifiableKeyFrameRequestees = null
                true
            } else false
        }
    }

    /**
     * Implements [KeyFrameControl.removeKeyFrameRequester].
     *
     *
     * {@inheritDoc}
     */
    override fun removeKeyFrameRequester(keyFrameRequester: KeyFrameRequester): Boolean {
        synchronized(this) {
            val index = keyFrameRequesters.indexOf(keyFrameRequester)
            return if (-1 != index) {
                val newKeyFrameRequesters = ArrayList(keyFrameRequesters)
                newKeyFrameRequesters.removeAt(index)
                keyFrameRequesters = newKeyFrameRequesters
                unmodifiableKeyFrameRequesters = null
                true
            } else false
        }
    }

    /**
     * Implements [KeyFrameControl.requestKeyFrame].
     *
     *
     * {@inheritDoc}
     */
    override fun requestKeyFrame(urgent: Boolean): Boolean {
        for (keyFrameRequester in getKeyFrameRequesters()) {
            try {
                if (keyFrameRequester.requestKeyFrame()) return true
            } catch (e: Exception) {
                /*
				 * A KeyFrameRequestee has malfunctioned, do not let it interfere with the others.
				 */
            }
        }
        return false
    }
}