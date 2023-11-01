/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event

import org.atalk.service.neomedia.DTMFTone
import java.util.*

/**
 * `DTMFReceivedEvent`s indicate reception of a DTMF tone.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
class DTMFReceivedEvent(source: Any?, value: DTMFTone?, duration: Long, start: Boolean?) : EventObject(source) {
    /**
     * The tone.
     */
    private val value: DTMFTone?

    /**
     * The duration.
     */
    private val duration: Long

    /**
     * Whether this `DTMFReceivedEvent` represents the start of reception of a tone (if
     * `true`), the end of reception of a tone (if `false`), or the reception of a
     * tone with a given duration (if `null`).
     */
    private val start: Boolean?

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * @param source
     * the source of the event.
     * @param value
     * dmtf tone value.
     * @param start
     * whether this event represents the start of reception (if `true`), the end of
     * reception (if `false`) or the reception of a tone with a given direction (if
     * `null`).
     */
    constructor(source: Any?, value: DTMFTone?, start: Boolean) : this(source, value, -1, start) {}

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * @param source
     * the source of the event.
     * @param value
     * dmtf tone value.
     * @param duration
     * duration of the DTMF tone.
     */
    constructor(source: Any?, value: DTMFTone?, duration: Long) : this(source, value, duration, null) {}

    /**
     * Creates a `MessageReceivedEvent` representing reception of the `source` message
     * received from the specified `from` contact.
     *
     * @param source
     * the source of the event.
     * @param value
     * dmtf tone value.
     * @param duration
     * duration of the DTMF tone.
     * @param start
     * whether this event represents the start of reception (if `true`), the end of
     * reception (if `false`) or the reception of a tone with a given direction (if
     * `null`).
     */
    init {
        this.value = value
        this.duration = duration
        this.start = start
    }

    /**
     * Returns the tone this event is indicating of.
     *
     * @return the tone this event is indicating of.
     */
    fun getValue(): DTMFTone? {
        return value
    }

    /**
     * Returns the tone duration for this event.
     *
     * @return the tone duration for this event.
     */
    fun getDuration(): Long {
        return duration
    }

    /**
     * Returns the value of the `start` attribute of this `DTMFReceivedEvent`, which
     * indicates whether this `DTMFReceivedEvent` represents the start of reception of a tone
     * (if `true`), the end of reception of a tone (if `false`), or the reception of a
     * tone with a given duration (if `null`).
     */
    fun getStart(): Boolean? {
        return start
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L
    }
}