/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.portaudio

import org.atalk.impl.neomedia.portaudio.Pa.HostApiTypeId

/**
 * Implements `Exception` for the PortAudio capture and playback system.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
class PortAudioException @JvmOverloads constructor(message: String?,
        /**
         * The code of the error as defined by the native PortAudio library represented by this
         * instance if it is known or [Pa.paNoError] if it is not known.
         */
        val errorCode: Long = Pa.paNoError.toLong(), hostApiType: Int = -1) : Exception(message) {
    /**
     * Gets the code of the error as defined by the native PortAudio library represented by this
     * instance if it is known.
     *
     * @return the code of the error as defined by the native PortAudio library represented by this
     * instance if it is known or [Pa.paNoError] if it is not known
     */
    /**
     * Gets the host API, if any, which returned the error code and (detailed) message represented
     * by this instance.
     *
     * @return the host API, if any, which returned the error code and (detailed) message
     * represented by this instance; otherwise, `null`
     */
    /**
     * The host API, if any, which returned the error code and (detailed) message represented by
     * this instance.
     */
    val hostApiType: HostApiTypeId?
    /**
     * Initializes a new `PortAudioException` instance with a specific detail message.
     *
     * @param message
     * the detail message to initialize the new instance with
     * @param errorCode
     * @param hostApiType
     */
    /**
     * Initializes a new `PortAudioException` instance with a specific detail message.
     *
     * @param message
     * the detail message to initialize the new instance with
     */
    init {
        this.hostApiType = if (hostApiType < 0) null else HostApiTypeId.valueOf(hostApiType)
    }

    /**
     * Returns a human-readable representation/description of this `Throwable`.
     *
     * @return a human-readable representation/description of this `Throwable`
     */
    override fun toString(): String {
        var s = super.toString()
        val errorCode = errorCode
        val errorCodeStr = if (errorCode == Pa.paNoError.toLong()) null else java.lang.Long.toString(errorCode)
        val hostApiType = hostApiType
        val hostApiTypeStr = hostApiType?.toString()
        if (errorCodeStr != null || hostApiTypeStr != null) {
            val sb = StringBuilder(s)
            sb.append(": ")
            if (errorCodeStr != null) {
                sb.append("errorCode= ")
                sb.append(errorCodeStr)
                sb.append(';')
            }
            if (hostApiTypeStr != null) {
                if (errorCodeStr != null) sb.append(' ')
                sb.append("hostApiType= ")
                sb.append(hostApiTypeStr)
                sb.append(';')
            }
            s = sb.toString()
        }
        return s
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}