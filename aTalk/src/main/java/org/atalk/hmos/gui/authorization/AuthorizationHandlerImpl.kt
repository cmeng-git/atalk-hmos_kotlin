/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.authorization

import android.content.Intent
import net.java.sip.communicator.service.protocol.AuthorizationHandler
import net.java.sip.communicator.service.protocol.AuthorizationRequest
import net.java.sip.communicator.service.protocol.AuthorizationResponse
import net.java.sip.communicator.service.protocol.Contact
import okhttp3.internal.notifyAll
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.jivesoftware.smack.util.StringUtils

/**
 * Android implementation of `AuthorizationHandler`.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AuthorizationHandlerImpl : AuthorizationHandler {
    /**
     * Creates new instance of `AuthorizationHandlerImpl`.
     */
    init {
        // Clears the map after previous instance
        requestMap = HashMap()
    }

    /**
     * Implements the `AuthorizationHandler.processAuthorisationRequest` method.
     *
     * Called by the protocol provider whenever someone would like to add us to their contact list.
     */
    override fun processAuthorisationRequest(req: AuthorizationRequest, sourceContact: Contact): AuthorizationResponse {
        val id = System.currentTimeMillis()
        val requestHolder = AuthorizationRequestedHolder(id, req, sourceContact)
        requestMap[id] = requestHolder
        AuthorizationRequestedDialog.showDialog(id)
        requestHolder.waitForResponse()
        requestMap.remove(id)
        return AuthorizationResponse(requestHolder.responseCode, null)
    }

    /**
     * Implements the `AuthorizationHandler.createAuthorizationRequest` method.
     *
     * The method is called when the user has tried to add a contact to the contact list and this
     * contact requires authorization.
     */
    override fun createAuthorizationRequest(contact: Contact): AuthorizationRequest? {
        val request = AuthorizationRequest()
        val id = System.currentTimeMillis()
        val requestHolder = AuthorizationRequestedHolder(id, request, contact)
        requestMap[id] = requestHolder
        val dialogIntent = RequestAuthorizationDialog.getRequestAuthDialogIntent(id)
        aTalkApp.globalContext.startActivity(dialogIntent)
        requestHolder.waitForResponse()

        // If user id did not cancel the dialog when return, prepared request and remove it
        // from the requestMap
        if (requestMap.containsKey(id)) {
            requestMap.remove(id)
            return requestHolder.request
        }
        return null
    }

    /**
     * Implements the `AuthorizationHandler.processAuthorizationResponse` method.
     *
     * The method will be called whenever someone acts upon an authorization request that we
     * have previously sent.
     */
    override fun processAuthorizationResponse(response: AuthorizationResponse, sourceContact: Contact) {
        val ctx = aTalkApp.globalContext
        var msg = sourceContact.address + " "
        val responseCode = response.getResponseCode()
        if (responseCode === AuthorizationResponse.ACCEPT) {
            msg += ctx.getString(R.string.service_gui_AUTHORIZATION_ACCEPTED)
        } else if (responseCode === AuthorizationResponse.REJECT) {
            msg += ctx.getString(R.string.service_gui_AUTHENTICATION_REJECTED)
        }
        val reason = response.getReason()
        if (StringUtils.isNotEmpty(reason)) {
            msg += " $reason"
        }
        DialogActivity.showConfirmDialog(ctx,
                ctx.getString(R.string.service_gui_AUTHORIZATION_REQUEST), msg, null, null)
    }

    /**
     * Class used to store request state and communicate between this `AuthorizationHandlerImpl
    ` *  and dialog activities.
     */
    class AuthorizationRequestedHolder
    /**
     * Creates new instance of `AuthorizationRequestedHolder`.
     *
     * @param ID identifier assigned for the request
     * @param request the authorization request
     * @param contact contact related to the request
     */
    (
            /**
             * Request identifier.
             */
            val ID: Long,
            /**
             * The request object.
             */
            val request: AuthorizationRequest,
            /**
             * Contact related to the request.
             */
            val contact: Contact) {
        /**
         * Lock object used to synchronize this handler with dialog activities.
         */
        private val responseLock = Any()

        /**
         * Filed used to store response code set by the dialog activity.
         */
        var responseCode: AuthorizationResponse.AuthorizationResponseCode? = null

        /**
         * This method blocks until the dialog activity finishes its job.
         */
        fun waitForResponse() {
            synchronized(responseLock) {
                try {
                    (responseLock as Object).wait()
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
        }

        /**
         * Method should be used by the dialog activity to notify about the result.
         *
         * @param response
         */
        fun notifyResponseReceived(response: AuthorizationResponse.AuthorizationResponseCode?) {
            responseCode = response
            releaseLock()
        }

        /**
         * Releases the synchronization lock.
         */
        private fun releaseLock() {
            synchronized(responseLock) { responseLock.notifyAll() }
        }

        /**
         * Discards the request by removing it from active requests map and releasing the
         * synchronization lock.
         */
        fun discard() {
            requestMap.remove(ID)
            releaseLock()
        }

        /**
         * Submits request text and releases the synchronization lock.
         *
         * @param requestText the text that will be added to the authorization request.
         */
        fun submit(requestText: String) {
            request.reason = requestText
            releaseLock()
        }
    }

    companion object {
        /**
         * The map of currently active `AuthorizationRequestedHolder`s.
         */
        private var requestMap = HashMap<Long, AuthorizationRequestedHolder>()

        /**
         * Returns the `AuthorizationRequestedHolder` for given request `id`.
         *
         * @param id the request identifier.
         * @return the `AuthorizationRequestedHolder` for given request `id`.
         */
        fun getRequest(id: Long): AuthorizationRequestedHolder? {
            return requestMap[id]
        }
    }
}