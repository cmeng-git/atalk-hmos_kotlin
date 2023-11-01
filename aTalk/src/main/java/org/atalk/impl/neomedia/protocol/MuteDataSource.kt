/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol

/**
 * All datasources that support muting functionality implement `MuteDataSource`.
 *
 * @author Damian Minkov
 */
interface MuteDataSource {
    /**
     * Determines whether this `DataSource` is mute.
     *
     * @return `true` if this `DataSource` is mute; otherwise, `false`
     */
    /**
     * Sets the mute state of this `DataSource`.
     *
     * @param mute
     * `true` to mute this `DataSource`; otherwise, `false`
     */
    var isMute: Boolean
}