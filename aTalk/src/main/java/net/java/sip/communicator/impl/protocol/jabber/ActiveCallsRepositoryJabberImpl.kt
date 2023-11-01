/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.ActiveCallsRepository
import net.java.sip.communicator.service.protocol.Call
import net.java.sip.communicator.service.protocol.event.CallChangeEvent

/**
 * Keeps a list of all calls currently active and maintained by this protocol
 * provider. Offers methods for finding a call by its ID, peer session and others.
 *
 * @author Emil Ivov
 * @author Symphorien Wanko
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
class ActiveCallsRepositoryJabberImpl
/**
 * It's where we store all active calls
 *
 * @param opSet the `OperationSetBasicTelphony` instance which has
 * been used to create calls in this repository
 */
(opSet: OperationSetBasicTelephonyJabberImpl?) : ActiveCallsRepository<CallJabberImpl?, OperationSetBasicTelephonyJabberImpl?>(opSet) {
    /**
     * Returns the [CallJabberImpl] containing a [ ] whose corresponding jingle session has the specified jingle `sid`.
     *
     * @param sid the jingle `sid` we're looking for.
     * @return the [CallJabberImpl] containing the peer with the
     * specified `sid` or `null` if we couldn't find one matching it.
     */
    fun findBySid(sid: String?): CallJabberImpl? {
        val calls = getActiveCalls()
        while (calls.hasNext()) {
            val call = calls.next()
            if (call!!.containsSid(sid)) return call
        }
        return null
    }

    /**
     * Returns the `Call` with ID equal to `callid`.
     *
     * @param callid the ID to search for
     * @return the `Call` with ID equal to `callid`.
     */
    fun findByCallId(callid: String): CallJabberImpl? {
        val calls = getActiveCalls()
        while (calls.hasNext()) {
            val call = calls.next()
            if (call!!.callId == callid) return call
        }
        return null
    }

    /**
     * Returns the [CallPeerJabberImpl] whose jingle session has the specified jingle `sid`.
     *
     * @param sid the jingle `sid` we're looking for.
     * @return the [CallPeerJabberImpl] with the specified `sid`
     * or `null` if we couldn't find one matching it.
     */
    fun findCallPeerBySid(sid: String?): CallPeerJabberImpl? {
        val calls = getActiveCalls()
        while (calls.hasNext()) {
            val call = calls.next()
            val peer = call!!.getPeerBySid(sid)
            if (peer != null) return peer
        }
        return null
    }

    /**
     * Returns the [CallPeerJabberImpl] whose session-initiate's stanzaId has the specified IQ `id`.
     *
     * @param stanzaId the IQ `id` we're looking for.
     * @return the [CallPeerJabberImpl] with the specified
     * `stanzaId` or `null` if we couldn't find one matching it.
     */
    fun findCallPeerByJingleIQStanzaId(stanzaId: String?): CallPeerJabberImpl? {
        val calls = getActiveCalls()
        while (calls.hasNext()) {
            val call = calls.next()
            val peer = call!!.getPeerByJingleIQStanzaId(stanzaId)
            if (peer != null) return peer
        }
        return null
    }

    /**
     * Creates and dispatches a `CallEvent` notifying registered
     * listeners that an event with id `eventID` has occurred on `sourceCall`.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred
     * @param cause the `CallChangeEvent`, if any, which is the cause
     * that necessitated a new `CallEvent` to be fired
     * @see ActiveCallsRepository.fireCallEvent
     */
    override fun fireCallEvent(eventID: Int, sourceCall: Call<*>, cause: CallChangeEvent?) {
        parentOperationSet!!.fireCallEvent(eventID, sourceCall)
    }
}