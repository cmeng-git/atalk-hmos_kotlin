/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.ContactCapabilitiesListener

/**
 * Represents an `OperationSet` to query the `OperationSet`s supported for a specific
 * `Contact`. The `OperationSet`s reported as supported for a specific
 * `Contact` are considered by the associated protocol provider to be capabilities possessed
 * by the `Contact` in question.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface OperationSetContactCapabilities : OperationSet {
    /**
     * Registers a specific `ContactCapabilitiesListener` to be notified about changes in the
     * list of `OperationSet` capabilities of `Contact`s. If the specified
     * `listener` has already been registered, adding it again has no effect.
     *
     * @param listener the `ContactCapabilitiesListener` which is to be notified about changes in the
     * list of `OperationSet` capabilities of `Contact`s
     */
    fun addContactCapabilitiesListener(listener: ContactCapabilitiesListener?)

    /**
     * Gets the `OperationSet` corresponding to the specified `Class` and supported by
     * the specified `Contact`. If the returned value is non-`null`, it indicates that
     * the `Contact` is considered by the associated protocol provider to possess the
     * `opsetClass` capability. Otherwise, the associated protocol provider considers
     * `contact` to not have the `opsetClass` capability.
     *
     * @param <T> the type extending `OperationSet` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @param contact the `Contact` for which the `opsetClass` capability is to be queried
     * @param opsetClass the `OperationSet` `Class` for which the specified `contact` is
     * to be checked whether it possesses it as a capability
     * @return the `OperationSet` corresponding to the specified `opsetClass` which is
     * considered by the associated protocol provider to be possessed as a capability by the
     * specified `contact`; otherwise, `null`
    </T> */
    fun <T : OperationSet?> getOperationSet(contact: Contact, opsetClass: Class<T>): T?

    /**
     * Gets the `OperationSet`s supported by a specific `Contact`. The returned
     * `OperationSet`s are considered by the associated protocol provider to capabilities
     * possessed by the specified `contact`.
     *
     * @param contact the `Contact` for which the supported `OperationSet` capabilities are to
     * be retrieved
     * @return a `Map` listing the `OperationSet`s considered by the associated
     * protocol provider to be supported by the specified `contact` (i.e. to be
     * possessed as capabilities). Each supported `OperationSet` capability is
     * represented by a `Map.Entry` with key equal to the `OperationSet` class
     * name and value equal to the respective `OperationSet` instance
     */
    fun getSupportedOperationSets(contact: Contact): Map<String, OperationSet>

    /**
     * Unregisters a specific `ContactCapabilitiesListener` to no longer be notified about
     * changes in the list of `OperationSet` capabilities of `Contact`s. If the
     * specified `listener` has already been unregistered or has never been registered,
     * removing it has no effect.
     *
     * @param listener the `ContactCapabilitiesListener` which is to no longer be notified about
     * changes in the list of `OperationSet` capabilities of `Contact`s
     */
    fun removeContactCapabilitiesListener(listener: ContactCapabilitiesListener?)
}