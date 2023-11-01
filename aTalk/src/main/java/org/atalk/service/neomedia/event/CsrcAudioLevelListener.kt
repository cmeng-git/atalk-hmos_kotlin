/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event

/**
 * The `CsrcAudioLevelListener` delivers audio level events reported by the remote party in
 * cases where it (the remote party) is acting as a mixer, mixing flows from numerous contributors.
 * It is up to upper layers such as SIP to define means of determining the exact members that the
 * CSRC IDs and hence audio levels participants belong to.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface CsrcAudioLevelListener {
    /**
     * Called by the media service implementation after it has received audio levels for the various
     * participants (Contributing SouRCes) that are taking part in a conference call.
     *
     * @param audioLevels
     * a `long` array in which the elements at the even indices specify the CSRC IDs
     * and the elements at the odd indices specify the respective audio levels
     */
    fun audioLevelsReceived(audioLevels: LongArray?)
}