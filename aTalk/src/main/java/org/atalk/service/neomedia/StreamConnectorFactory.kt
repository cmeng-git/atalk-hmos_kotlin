/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * Represents a factory of `StreamConnector` instances.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface StreamConnectorFactory {
    /**
     * Initializes a `StreamConnector` instance.
     *
     * @return a `StreamConnector` instance
     */
    fun createStreamConnector(): StreamConnector?
}