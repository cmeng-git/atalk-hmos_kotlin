/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * The `MediaDirections` enumeration contains a list of media directions that indicate
 * read/write capabilities of different entities in this `MediaService` such as for example devices.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
/**
 * Creates a `MediaDirection` instance with the specified name.
 *
 * @param directionName the name of the `MediaDirections` we'd like to create.
 */
/**
 * The name of this direction.
 */

enum class MediaDirection(private val directionName: String) {
    /**
     * Indicates that the related entity does not support neither input nor output (i.e. neither
     * send nor receive) operations.
     */
    INACTIVE("inactive"),

    /**
     * Represents a direction from the entity that this direction pertains to to the outside. When
     * applied to a `MediaDevice` the direction indicates that the device is a read-only one.
     * In the case of a stream a `SENDONLY` direction indicates that the stream is only
     * sending data to the remote party without receiving.
     */
    SENDONLY("sendonly"),

    /**
     * Represents a direction pointing to the entity that this object pertains to and from the
     * outside. When applied to a `MediaDevice` the direction indicates that the device is a
     * write-only one. In the case of a `MediaStream` a `RECVONLY` direction indicates
     * that the stream is only receiving data from the remote party without sending any.
     */
    RECVONLY("recvonly"),

    /**
     * Indicates that the related entity supports both input and output (send and receive)
     * operations.
     */
    SENDRECV("sendrecv");

    /**
     * Returns the name of this `MediaDirection` (e.g. "sendonly" or "sendrecv"). The name
     * returned by this method is meant for use by session description mechanisms such as SIP/SDP or
     * XMPP/Jingle.
     *
     * @return the name of this `MediaDirection` (e.g. "sendonly", "recvonly", "sendrecv").
     */
    override fun toString(): String {
        return directionName
    }

    /**
     * Applies an extra direction constraint to this `MediaDirection` or in other words
     * performs an `and` operation. This method is primarily meant for use by the
     * `getReverseMediaDirection(MediaDirection)` method while working on Offer/Answer media
     * negotiation..
     *
     * @param direction that direction constraint that we'd like to apply to this `MediaDirection`
     * @return the new `MediaDirection` obtained after applying the `direction`
     * constraint to this `MediaDirection`.
     */
    fun and(direction: MediaDirection): MediaDirection {
        return if (this == SENDRECV) {
            direction
        } else if (this == SENDONLY) {
            if (direction == SENDONLY || direction == SENDRECV) SENDONLY else INACTIVE
        } else if (this == RECVONLY) {
            if (direction == RECVONLY || direction == SENDRECV) RECVONLY else INACTIVE
        } else INACTIVE
    }

    /**
     * Reverses a direction constraint on this `MediaDirection` or in other words performs an
     * `or` operation. This method is meant for use in cases like putting a stream off hold
     * or in other words reversing the `SENDONLY` constraint.
     *
     * @param direction the direction that we'd like to enable (i.e. add) to this `MediaDirection`
     * @return the new `MediaDirection` obtained after adding the specified
     * `direction` this `MediaDirection`.
     */
    fun or(direction: MediaDirection): MediaDirection {
        return if (this == SENDRECV) {
            this
        } else if (this == SENDONLY) {
            if (direction.allowsReceiving()) SENDRECV else this
        } else if (this == RECVONLY) {
            if (direction.allowsSending()) SENDRECV else this
        } else  // INACTIVE
            direction
    }

    /**
     * Returns the `MediaDirection` value corresponding to a remote party's perspective of
     * this `MediaDirection`. In other words, if I say I'll be sending only, for you this
     * means that you'll be receiving only. If however, I say I'll be both sending and receiving
     * (i.e. `SENDRECV`) then it means you'll be doing the same (i.e. again `SENDRECV`
     * ).
     *
     * @return the `MediaDirection` value corresponding to a remote party's perspective of
     * this `MediaDirection`.
     */
    val reverseDirection: MediaDirection
        get() = when (this) {
            SENDRECV -> SENDRECV
            SENDONLY -> RECVONLY
            RECVONLY -> SENDONLY
            else -> INACTIVE
        }

    /**
     * Returns the `MediaDirection` value corresponding to a remote party's perspective of
     * this `MediaDirection` applying a remote party constraint. In other words, if I say
     * I'll only be sending media (i.e. `SENDONLY`) and you know that you can both send and
     * receive (i.e. `SENDRECV`) then to you this means that you'll be only receiving media
     * (i.e. `RECVONLY`). If however I say that I can only receive a particular media type
     * (i.e. `RECVONLY`) and you are in the same situation then this means that neither of us
     * would be sending nor receiving and the stream would appear `INACTIVE` to you (and me
     * for that matter). The method is meant for use during Offer/Answer SDP negotiation.
     *
     * @param remotePartyDir the remote party `MediaDirection` constraint that we'd have to consider when
     * trying to obtain a `MediaDirection` corresponding to remoteParty's constraint.
     * @return the `MediaDirection` value corresponding to a remote party's perspective of
     * this `MediaDirection` applying a remote party constraint.
     */
    fun getDirectionForAnswer(remotePartyDir: MediaDirection): MediaDirection {
        return this.and(remotePartyDir.reverseDirection)
    }

    /**
     * Determines whether the directions specified by this `MediaDirection` instance allow
     * for outgoing (i.e. sending) streams or in other words whether this is a `SENDONLY` or
     * a `SENDRECV` instance
     *
     * @return `true` if this `MediaDirection` instance includes the possibility of
     * sending and `false` otherwise.
     */
    fun allowsSending(): Boolean {
        return this == SENDONLY || this == SENDRECV
    }

    /**
     * Determines whether the directions specified by this `MediaDirection` instance allow
     * for incoming (i.e. receiving) streams or in other words whether this is a `RECVONLY`
     * or a `SENDRECV` instance
     *
     * @return `true` if this `MediaDirection` instance includes the possibility of
     * receiving and `false` otherwise.
     */
    fun allowsReceiving(): Boolean {
        return this == RECVONLY || this == SENDRECV
    }

    companion object {
        /**
         * Returns a `MediaDirection` value corresponding to the specified
         * `mediaDirectionStr` or in other words `SENDONLY` for "sendonly",
         * `RECVONLY` for "recvonly", `SENDRECV` for "sendrecv", and `INACTIVE` for "inactive".
         *
         * @param mediaDirectionStr the direction `String` that we'd like to parse.
         * @return a `MediaDirection` value corresponding to the specified
         * `mediaDirectionStr`.
         * @throws IllegalArgumentException in case `mediaDirectionStr` is not a valid media direction.
         */
        @Throws(IllegalArgumentException::class)
        fun fromString(mediaDirectionStr: String): MediaDirection {
            for (value in values()) if (value.toString() == mediaDirectionStr) return value
            throw IllegalArgumentException("$mediaDirectionStr is not a valid media direction")
        }
    }
}