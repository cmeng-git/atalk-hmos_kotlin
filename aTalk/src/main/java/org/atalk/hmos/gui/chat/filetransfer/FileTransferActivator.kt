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
package org.atalk.hmos.gui.chat.filetransfer

import net.java.sip.communicator.service.protocol.OperationSetFileTransfer
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener
import org.atalk.hmos.gui.chat.ChatSessionManager.createChatForContact
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.osgi.framework.*
import timber.log.Timber

/**
 * Android FileTransferActivator activator which registers `ScFileTransferListener`
 * for each protocol service provider specific to this system. It listens in to any incoming
 * fileTransferRequestReceived and generate a message to be display in the respective chatPanel
 * for user action.
 * Note: Each protocol must registered only once, otherwise multiple file received messages get
 * generated for every fileTransferRequestReceived.
 *
 * @author Eng Chong Meng
 */
class FileTransferActivator : BundleActivator, ServiceListener, ScFileTransferListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private var bundleContext: BundleContext? = null

    /**
     * Starts the service. Check the current registered protocol providers which supports
     * FileTransfer and adds a listener to them.
     *
     * @param bc BundleContext
     */
    @Throws(Exception::class)
    override fun start(bc: BundleContext) {
        bundleContext = bc
        bundleContext!!.addServiceListener(this)
        var ppsRefs: Array<ServiceReference<*>?>? = null
        try {
            ppsRefs = bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
        } catch (ex: InvalidSyntaxException) {
            ex.printStackTrace()
        }
        if (ppsRefs != null && ppsRefs.isNotEmpty()) {
            for (ppsRef in ppsRefs) {
                val pps = bundleContext!!.getService(ppsRef as ServiceReference<ProtocolProviderService> )
                handleProviderAdded(pps)
            }
        }
    }

    /**
     * Stops the service.
     *
     * @param bc BundleContext
     */
    @Throws(Exception::class)
    override fun stop(bc: BundleContext) {
        bundleContext = bc
        bundleContext!!.removeServiceListener(this)
        var ppsRefs: Array<ServiceReference<*>?>? = null
        try {
            ppsRefs = bundleContext!!.getServiceReferences(ProtocolProviderService::class.java.name, null)
        } catch (e: InvalidSyntaxException) {
            e.printStackTrace()
        }
        if (ppsRefs != null && ppsRefs.isNotEmpty()) {
            for (ppsRef in ppsRefs) {
                val pps = bundleContext!!.getService(ppsRef as ServiceReference<ProtocolProviderService>)
                handleProviderRemoved(pps)
            }
        }
    }

    /**
     * When new protocol provider is registered we check if it does supports FileTransfer and
     * add a listener to it if so.
     *
     * @param event ServiceEvent received when there is a service changed
     */
    override fun serviceChanged(event: ServiceEvent) {
        val serviceRef = event.serviceReference
        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.bundle.state == Bundle.STOPPING) return
        val sService = bundleContext!!.getService(serviceRef as ServiceReference<Any>)
        // we don't care if the source service is not a protocol provider
        if (sService is ProtocolProviderService) {
            when (event.type) {
                ServiceEvent.REGISTERED -> handleProviderAdded(sService)
                ServiceEvent.UNREGISTERING -> handleProviderRemoved(sService)
            }
        }
    }

    /**
     * Used to attach the File Transfer Service to existing or just registered protocol provider.
     * Checks if the provider has implementation of OperationSetFileTransfer
     *
     * @param provider ProtocolProviderService
     */
    private fun handleProviderAdded(provider: ProtocolProviderService) {
        val opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer::class.java)
        if (opSetFileTransfer != null) {
            opSetFileTransfer.addFileTransferListener(this)
        } else {
            Timber.log(TimberLog.FINER, "Service did not have a file transfer op. set: %s", provider.toString())
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private fun handleProviderRemoved(provider: ProtocolProviderService) {
        val opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer::class.java)
        opSetFileTransfer?.removeFileTransferListener(this)
    }

    /**
     * Called when a new `IncomingFileTransferRequest` has been received.
     *
     * @param event the `FileTransferRequestEvent` containing the newly received request and other details.
     */
    override fun fileTransferRequestReceived(event: FileTransferRequestEvent) {
        val request = event.getRequest()
        val opSet = event.getFileTransferOperationSet()
        val sender = request.getSender()
        val date = event.getTimestamp()
        val chatPanel = createChatForContact(sender)
        chatPanel?.addFTReceiveRequest(opSet, request, date)
    }

    /**
     * Nothing to do here, because we already know when a file transfer is created.
     *
     * @param event the `FileTransferCreatedEvent` that notified us
     */
    override fun fileTransferCreated(event: FileTransferCreatedEvent?) {}

    /**
     * Called when a new `IncomingFileTransferRequest` has been rejected. Nothing to do
     * here, because we are the one who rejects the request.
     *
     * @param event the `FileTransferRequestEvent` containing the received request which was rejected.
     */
    override fun fileTransferRequestRejected(event: FileTransferRequestEvent) {}

    /**
     * Called when an `IncomingFileTransferRequest` has been canceled from the contact who send it.
     *
     * @param event the `FileTransferRequestEvent` containing the request which was canceled.
     */
    override fun fileTransferRequestCanceled(event: FileTransferRequestEvent) {}
}