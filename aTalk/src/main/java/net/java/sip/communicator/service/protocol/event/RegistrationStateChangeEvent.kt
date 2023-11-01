/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2018 - present 8x8, Inc.
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
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import java.beans.PropertyChangeEvent

/**
 * Instances of this class represent a change in the status of the provider that triggered them. A
 * status change may have occurred because the user requested it or because an error or a failure
 * have occurred, in which case the reason and reason code would be set accordingly.
 *
 *
 * Keep in mind that reasons are not localized and services such as the user interface should only
 * show them in a "details box". In the rest of the time, such services should consult the error
 * code and provide corresponding, localized, reason phrases.
 *
 *
 * Note, that we have tried to provide a maximum number of error codes in order to enumerate all
 * possible reason codes that may be returned from servers in the various protocols. Each protocol
 * would only return a subset of these.
 *
 *
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class RegistrationStateChangeEvent
/**
 * Creates an event instance indicating a change of the provider state from `oldValue` to
 * `newValue`.
 *
 * @param source the provider that generated the event
 * @param oldValue the status the source provider was in before entering the new state.
 * @param newValue the status the source provider is currently in.
 * @param reasonCode a value corresponding to one of the REASON_XXX fields of this class, indicating the
 * reason for this state transition.
 * @param reason a String further explaining the reason code or null if no such explanation is necessary.
 */
(source: ProtocolProviderService?, oldValue: RegistrationState?, newValue: RegistrationState?,
    /**
     * The reason code returned by the server in order to explain the state transition.
     */
    private val reasonCode: Int,
    /**
     * A (non localized) String containing information further explaining the reason code.
     */
    private val reason: String?) : PropertyChangeEvent(source, RegistrationStateChangeEvent::class.java.name, oldValue, newValue) {
    /**
     * Whether this event is after user request.
     */
    private var userRequest = false

    /**
     * Returns the provider that has generated this event
     *
     * @return the provider that generated the event.
     */
    fun getProvider(): ProtocolProviderService {
        return getSource() as ProtocolProviderService
    }

    /**
     * Returns the status of the provider before this event took place.
     *
     * @return a RegistrationState instance indicating the event the source provider was in before
     * it entered its new state.
     */
    fun getOldState(): RegistrationState {
        return super.getOldValue() as RegistrationState
    }

    /**
     * Returns the status of the provider after this event took place. (i.e. at the time the event
     * is being dispatched).
     *
     * @return a RegistrationState instance indicating the event the source provider is in after the
     * status change occurred.
     */
    fun getNewState(): RegistrationState {
        return super.getNewValue() as RegistrationState
    }

    /**
     * Returns a string representation of this event.
     *
     * @return a String containing the name of the event as well as the names of the old and new
     * `RegistrationState`s
     */
    override fun toString(): String {
        return ("RegistrationStateChangeEvent[ oldState="
                + getOldState().getStateName()
                + "; newState=" + getNewState()
                + "; userRequest=" + isUserRequest()
                + "; reasonCode=" + getReasonCode()
                + "; reason=" + getReason() + "]")
    }

    /**
     * One of the REASON_XXX fields, indicating the reason code returned by the server in order to
     * explain the state transition.
     *
     * @return a value corresponding to one of the REASON_XXX fields of this class.
     */
    fun getReasonCode(): Int {
        return reasonCode
    }

    /**
     * Returns a (non localized) String containing information further explaining the reason code,
     * or null if no particular reason has been specified.
     *
     * Keep in mind that reason String-s returned by this method are not localized and services such
     * as the user interface should only show them in a "details box". In the rest of the time, such
     * services should consult the error code and provide corresponding, localized, reason phrases.
     *
     * @return a non localized String explaining the reason for the state transition.
     */
    fun getReason(): String? {
        return reason
    }

    /**
     * Whether this event is from user request.
     *
     * @return whether this event is from user request.
     */
    fun isUserRequest(): Boolean {
        return userRequest
    }

    /**
     * Changes the event to indicate that it is created from use request.
     *
     * @param userRequest `true` if from user request
     */
    fun setUserRequest(userRequest: Boolean) {
        this.userRequest = userRequest
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that no reason is specified for this event transition.
         */
        const val REASON_NOT_SPECIFIED = -1

        /**
         * Indicates that the change in the registration state that has just occurred has been requested
         * by the user.
         */
        const val REASON_USER_REQUEST = 0

        /**
         * Indicates that the server has refused registration due to a problem with the authentication
         * (most probably a wrong password).
         */
        const val REASON_AUTHENTICATION_FAILED = 1

        /**
         * Indicates that the same user identifier has logged somewhere else. This code is often
         * returned when transiting into disconnected state. Some protocols, however, support multiple
         * login's and servers would only return this code for purely informational reasons.
         */
        const val REASON_MULTIPLE_LOGIN = 2

        /**
         * Indicates that the server does not recognize the used identifier that we tried to register
         * with. This also happen when admin remove the user on the server or initial login.
         */
        const val REASON_NON_EXISTING_USER_ID = 3

        /**
         * Indicates that we have too many existing registrations from the local IP address and the
         * server won't allow us to open any more of them.
         */
        const val REASON_CLIENT_LIMIT_REACHED_FOR_IP = 4

        /**
         * Indicates that we have been disconnecting and reconnecting to the server at a rate that ha
         * become too fast. We're temporarily banned and would have to wait a bit before trying again.
         * It is often a good idea for the user interface to prevent the user from actually trying again
         * for a certain amount of time.
         */
        const val REASON_RECONNECTION_RATE_LIMIT_EXCEEDED = 5

        /**
         * Indicates that an internal application error has occurred and it resulted in the state
         * transition indicated by this event.
         */
        const val REASON_INTERNAL_ERROR = 6

        /**
         * Indicates that the specified server was not found (i.e. the FQDN was not resolved or the ip
         * address was not reachable).
         */
        const val REASON_SERVER_NOT_FOUND = 8

        /**
         * Indicates that the specified server does not support TLS and the has required TLS use.
         */
        const val REASON_TLS_REQUIRED = 9

        /**
         * Indicates that the specified server has temporary block access due to many attempts.
         */
        const val REASON_POLICY_VIOLATION = 10

        /**
         * Indicates that the server has refused inBand registration due to a problem either
         * service not available or probably a wrong password.
         */
        const val REASON_IB_REGISTRATION_FAILED = 10

        /**
         * Indicates that the last session stream establishment is resumed.
         */
        const val REASON_RESUMED = 11

        /**
         * Indicates that the specified server returned an error input.
         */
        const val REASON_SERVER_RETURNED_ERRONEOUS_INPUT = 99
    }
}