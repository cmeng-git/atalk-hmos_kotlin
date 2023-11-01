/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.impl.neomedia.AbstractRTPConnector
import org.atalk.service.neomedia.event.SrtpListener
import org.atalk.util.MediaType

/**
 * Controls SRTP encryption in the MediaStream.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface SrtpControl {
    /**
     * Adds a `cleanup()` method to `TransformEngine` which is to go in hand with the
     * `cleanup()` method of `SrtpControl`.
     *
     * @author Lyubomir Marinov
     */
    interface TransformEngine : org.atalk.impl.neomedia.transform.TransformEngine {
        /**
         * Cleans up this `TransformEngine` and prepares it for garbage collection.
         */
        fun cleanup()
    }

    /**
     * Cleans up this `SrtpControl` and its `TransformEngine`.
     *
     * @param user the instance which requests the clean up.
     */
    fun cleanup(user: Any?)

    /**
     * Gets the default secure/insecure communication status for the supported call sessions.
     *
     * @return default secure communication status for the supported call sessions.
     */
    val secureCommunicationStatus: Boolean

    /**
     * Gets the `SrtpControlType` of this instance.
     *
     * @return the `SrtpControlType` of this instance
     */
    val srtpControlType: SrtpControlType

    /**
     * Returns the `SrtpListener` which listens for security events.
     *
     * @return the `SrtpListener` which listens for security events
     */
    /**
     * Sets a `SrtpListener` that will listen for security events.
     *
     * @param srtpListener the `SrtpListener` that will receive the events
     */
    var srtpListener: SrtpListener?

    /**
     * Returns the transform engine currently used by this stream.
     *
     * @return the RTP stream transformation engine
     */
    val transformEngine: TransformEngine?

    /**
     * Indicates if the key exchange method is dependent on secure transport of the signaling channel.
     *
     * @return `true` when secure signaling is required to make the encryption secure; `false`, otherwise.
     */
    fun requiresSecureSignalingTransport(): Boolean

    /**
     * Sets the `RTPConnector` which is to use or uses this SRTP engine.
     *
     * @param connector the `RTPConnector` which is to use or uses this SRTP engine
     */
    fun setConnector(connector: AbstractRTPConnector?)

    /**
     * When in multistream mode, enables the master session.
     *
     * @param masterSession whether current control, controls the master session.
     */
    fun setMasterSession(masterSession: Boolean)

    /**
     * Sets the multistream data, which means that the master stream has successfully started and
     * this will start all other streams in this session.
     *
     * @param master The security control of the master stream.
     */
    fun setMultistream(master: SrtpControl?)

    /**
     * Starts and enables zrtp in the stream holding this control.
     *
     * @param mediaType the media type of the stream this control controls.
     */
    fun start(mediaType: MediaType)

    /**
     * Registers `user` as an instance which is currently using this `SrtpControl`.
     *
     * @param user
     */
    fun registerUser(user: Any)

    companion object {
        const val RTP_SAVP = "RTP/SAVP"
        const val RTP_SAVPF = "RTP/SAVPF"
    }
}