/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

import javax.media.Control

/**
 * An interface used to pass additional format parameters (received via SDP/Jingle) to codecs.
 *
 * @author Boris Grozev
 */
interface FormatParametersAwareCodec : Control {
    /**
     * Sets the format parameters to `fmtps`
     *
     * @param fmtps
     * The format parameters to set
     */
    fun setFormatParameters(fmtps: Map<String, String>)
}