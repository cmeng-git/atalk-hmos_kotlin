/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.login

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl
import org.jivesoftware.smack.SmackException.NoResponseException
import org.jivesoftware.smack.SmackException.NotConnectedException
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smack.packet.Nonza
import org.jivesoftware.smack.packet.Stanza
import org.jivesoftware.smack.packet.TopLevelStreamElement
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

class LoginSynchronizationPoint<E : Exception>(pps: ProtocolProviderServiceJabberImpl, waitFor: String) {
    private val mConnect: XMPPConnection
    private val loginInitLock: Lock
    private val condition: Condition
    private val waitFor: String

    // Note that there is no need to make 'state' and 'failureException' volatile. Since 'lock'
    // and 'unlock' have the same memory synchronization effects as synchronization block enter and leave.
    private var state: State? = null
    private var failureException: E? = null

    /**
     * Construct a new synchronization point for the given connection.
     *
     * pps the ProtocolServiceProvider of this synchronization point.
     * waitFor a description of the event this synchronization point handles.
     */
    init {
        mConnect = pps.connection!!
        loginInitLock = pps.loginInitLock
        condition = pps.loginInitLock.newCondition()
        this.waitFor = waitFor
        init()
    }

    /**
     * Initialize (or reset) this synchronization point.
     */
    fun init() {
        loginInitLock.lock()
        state = State.Initial
        failureException = null
        loginInitLock.unlock()
    }

    /**
     * Send the given top level stream element and wait for a response.
     *
     * @param request the plain stream element to send.
     * @return `null` if synchronization point was successful, or the failure Exception.
     * @throws NoResponseException if no response was received.
     * @throws NotConnectedException if the connection is not connected.
     */
    @Throws(NoResponseException::class, NotConnectedException::class, InterruptedException::class)
    fun sendAndWaitForResponse(request: TopLevelStreamElement?): E? {
        assert(state == State.Initial)
        loginInitLock.lock()
        try {
            if (request != null) {
                when (request) {
                    is Stanza -> {
                        mConnect.sendStanza(request as Stanza?)
                    }
                    is Nonza -> {
                        mConnect.sendNonza(request as Nonza?)
                    }
                    else -> {
                        throw IllegalStateException("Unsupported element type")
                    }
                }
                state = State.RequestSent
            }
            waitForConditionOrTimeout()
        } finally {
            loginInitLock.unlock()
        }
        return checkForResponse()
    }

    /**
     * Send the given plain stream element and wait for a response.
     *
     * @param request the plain stream element to send.
     * @throws E if an failure was reported.
     * @throws NoResponseException if no response was received.
     * @throws NotConnectedException if the connection is not connected.
     */
    @Throws(NoResponseException::class, NotConnectedException::class, InterruptedException::class)
    fun sendAndWaitForResponseOrThrow(request: Nonza?) {
        sendAndWaitForResponse(request)
        // For State.Success, do nothing
        if (state == State.Failure) {
            if (failureException != null) {
                throw failureException!!
            }
        }
    }

    /**
     * Check if this synchronization point is successful or wait the connections reply timeout.
     *
     * @throws NoResponseException if there was no response marking the synchronization point as success or failed.
     * @throws E if there was a failure
     * @throws InterruptedException Interrupted Exception
     */
    @Throws(NoResponseException::class, InterruptedException::class)
    fun checkIfSuccessOrWaitOrThrow() {
        checkIfSuccessOrWait()
        if (state == State.Failure) {
            throw failureException!!
        }
    }

    /**
     * Check if this synchronization point is successful or wait the connections reply timeout.
     *
     * @return `null` if synchronization point was successful, or the failure Exception.
     * @throws NoResponseException if there was no response marking the synchronization point as success or failed.
     * @throws InterruptedException Interrupted Exception
     */
    @Throws(NoResponseException::class, InterruptedException::class)
    fun checkIfSuccessOrWait(): E? {
        loginInitLock.lock()
        try {
            when (state) {
                State.Success -> return null
                State.Failure -> return failureException
                else -> {}
            }
            waitForConditionOrTimeout()
        } finally {
            loginInitLock.unlock()
        }
        return checkForResponse()
    }

    /**
     * Report this synchronization point as successful.
     */
    fun reportSuccess() {
        loginInitLock.lock()
        try {
            state = State.Success
            condition.signalAll()
        } finally {
            loginInitLock.unlock()
        }
    }

    /**
     * Report this synchronization point as failed because of the given exception.
     * The `failureException` must be set.
     *
     * @param failureException the exception causing this synchronization point to fail.
     */
    fun reportFailure(failureException: E?) {
        assert(failureException != null)
        loginInitLock.lock()
        try {
            state = State.Failure
            this.failureException = failureException
            condition.signalAll()
        } finally {
            loginInitLock.unlock()
        }
    }

    /**
     * Check if this synchronization point was successful.
     *
     * @return true if the synchronization point was successful, false otherwise.
     */
    fun wasSuccessful(): Boolean {
        loginInitLock.lock()
        return try {
            state == State.Success
        } finally {
            loginInitLock.unlock()
        }
    }

    /**
     * Check if this synchronization point has its request already sent.
     *
     * @return true if the request was already sent, false otherwise.
     */
    fun requestSent(): Boolean {
        loginInitLock.lock()
        return try {
            state == State.RequestSent
        } finally {
            loginInitLock.unlock()
        }
    }

    /**
     * Wait for the condition to become something else as [State.RequestSent] or
     * [State.Initial]. [.reportSuccess] and [.reportFailure]
     * will either set this synchronization point to [State.Success] or
     * [State.Failure]. If none of them is set after the connections reply timeout,
     * this method will set the state of [State.NoResponse].
     *
     * @throws InterruptedException Interrupted Exception
     */
    @Throws(InterruptedException::class)
    private fun waitForConditionOrTimeout() {
        val msTime = mConnect.replyTimeout
        var remainingWait = TimeUnit.MILLISECONDS.toNanos(msTime)
        while (state == State.RequestSent || state == State.Initial) {
            if (remainingWait <= 0) {
                state = State.NoResponse
                break
            }
            remainingWait = condition.awaitNanos(remainingWait)
        }
    }

    /**
     * Check for a response and throw a [NoResponseException] if there was none.
     * The exception is thrown, if state is one of 'Initial', 'NoResponse' or 'RequestSent'
     *
     * @return `true</code> if synchronization point was successful, <code>false` on failure.
     * @throws NoResponseException No Response Exception
     */
    @Throws(NoResponseException::class)
    private fun checkForResponse(): E? {
        return when (state) {
            State.Initial, State.NoResponse, State.RequestSent -> throw NoResponseException.newWith(mConnect, waitFor)
            State.Success -> null
            State.Failure -> failureException
            else -> throw AssertionError("Unknown state $state")
        }
    }

    /* RequestSent and No Response currently not use by aTalk */
    private enum class State {
        Initial, RequestSent, NoResponse, Success, Failure
    }
}