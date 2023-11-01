/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * The `OperationSetResourceAwareTelephony` defines methods for creating a call toward a
 * specific resource, from which a callee is connected.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
interface OperationSetResourceAwareTelephony : OperationSet {
    /**
     * Creates a new `Call` and invites a specific `CallPeer` given by her
     * `Contact` on a specific `ContactResource` to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    fun createCall(callee: Contact?, calleeResource: ContactResource?): Call<*>?

    /**
     * Creates a new `Call` and invites a specific `CallPeer` given by her
     * `Contact` on a specific `ContactResource` to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    fun createCall(callee: String?, calleeResource: String?): Call<*>?

    /**
     * Creates a new `Call` and invites a specific `CallPeer` to it given by her
     * `String` URI.
     *
     * @param uri the address of the callee who we should invite to a new `Call`
     * @param calleeResource the specific resource to which the invite should be sent
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    fun createCall(uri: String?, calleeResource: String?, conference: CallConference?): Call<*>?

    /**
     * Creates a new `Call` and invites a specific `CallPeer` given by her
     * `Contact` to it.
     *
     * @param callee the address of the callee who we should invite to a new call
     * @param calleeResource the specific resource to which the invite should be sent
     * @param conference the `CallConference` in which the newly-created `Call` is to participate
     * @return a newly created `Call`. The specified `callee` is available in the
     * `Call` as a `CallPeer`
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     */
    @Throws(OperationFailedException::class)
    fun createCall(callee: Contact?, calleeResource: ContactResource?, conference: CallConference?): Call<*>?
}