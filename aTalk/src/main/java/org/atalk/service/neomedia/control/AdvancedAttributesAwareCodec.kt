/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control

import javax.media.Control

/**
 * An interface used to pass additional attributes (received via SDP/Jingle) to codecs.
 *
 * @author Damian Minkov
 */
interface AdvancedAttributesAwareCodec : Control {
    /**
     * Sets the additional attributes to `attributes`
     *
     * @param attributes The additional attributes to set
     */
    fun setAdvancedAttributes(attributes: Map<String, String>)
}