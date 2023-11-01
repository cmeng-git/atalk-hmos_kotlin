/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.util.event

/**
 * Listener interface for [T] type events.
 *
 * @param <T> the event object's class
 * @author Pawel Domas
</T> */
interface EventListener<T> {
    /**
     * Method fired when change occurs on the `eventObject`
     *
     * @param eventObject the instance that has been changed
     */
    fun onChangeEvent(eventObject: T)
}