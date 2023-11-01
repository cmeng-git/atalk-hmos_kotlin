/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * The `MediaStreamTarget` contains a pair of host:port couples indicating data (RTP) and control (RTCP) locations.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class MediaStreamTarget
/**
 * Initializes a new `MediaStreamTarget` instance with specific RTP and RTCP
 * `InetSocketAddress`es.
 *
 * @param rtpTarget the `InetSocketAddress` that the new instance is to indicate as a data/RTP address.
 * @param rtcpTarget the `InetSocketAddress` that the new instance is to indicate as a control/RTCP address.
`` */
(
        /**
         * The data (RTP) address of the target.
         */
        val dataAddress: InetSocketAddress,
        /**
         * The control (RTCP) address of the target.
         */
        val controlAddress: InetSocketAddress) {

    /**
     * Returns the `InetSocketAddress` that this `MediaTarget` is pointing to for all media (RTP) traffic.
     *
     * @return the `InetSocketAddress` that this `MediaTarget` is pointing to for all media (RTP) traffic.
     */

    /**
     * Initializes a new `MediaStreamTarget` instance with specific RTP and RTCP
     * `InetAddress`es and ports.
     *
     * @param rtpAddr the `InetAddress` that the new instance is to indicate as the IP address of a
     * data/RTP address
     * @param rtpPort the port that the new instance is to indicate as the port of a data/RTP address
     * @param rtcpAddr the `InetAddress` that the new instance is to indicate as the IP address of a
     * control/RTCP address
     * @param rtcpPort the port that the new instance is to indicate as the port of a control/RTCP address
     */
    constructor(rtpAddr: InetAddress?, rtpPort: Int, rtcpAddr: InetAddress?, rtcpPort: Int) : this(InetSocketAddress(rtpAddr, rtpPort), InetSocketAddress(rtcpAddr, rtcpPort)) {}

    /**
     * Determines whether this `MediaStreamTarget` is equal to a specific `Object`.
     *
     * @param obj the `Object` to be compared to this `MediaStreamTarget`
     * @return `true` if this `MediaStreamTarget` is equal to the specified
     * `obj`; otherwise, `false`
     */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!javaClass.isInstance(obj)) return false
        val mediaStreamTarget = obj as MediaStreamTarget?
        return (addressesAreEqual(controlAddress, mediaStreamTarget!!.controlAddress)
                && addressesAreEqual(dataAddress, mediaStreamTarget.dataAddress))
    }

    /**
     * Returns a hash code for this `MediaStreamTarget` instance which is suitable for use in hash tables.
     *
     * @return a hash code for this `MediaStreamTarget` instance which is suitable for use in hash tables
     */
    override fun hashCode(): Int {
        var hashCode = 0
        val controlAddress = controlAddress
        if (controlAddress != null) hashCode = hashCode or controlAddress.hashCode()
        val dataAddress = dataAddress
        if (dataAddress != null) hashCode = hashCode or dataAddress.hashCode()
        return hashCode
    }

    /**
     * Returns a human-readable representation of this `MediaStreamTarget` instance in the
     * form of a `String` value.
     *
     * @return a `String` value which gives a human-readable representation of this
     * `MediaStreamTarget` instance
     */
    override fun toString(): String {
        return (javaClass.simpleName + " with dataAddress " + dataAddress
                + " and controlAddress " + controlAddress)
    }

    companion object {
        /**
         * Determines whether two specific `InetSocketAddress` instances are equal.
         *
         * @param addr1 one of the `InetSocketAddress` instances to be compared
         * @param addr2 the other `InetSocketAddress` instance to be compared
         * @return `true` if `addr1` is equal to `addr2`; otherwise, `false`
         */
        fun addressesAreEqual(addr1: InetSocketAddress?, addr2: InetSocketAddress?): Boolean {
            return if (addr1 == null) addr2 == null else addr1 == addr2
        }
    }
}