/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.launchutils

import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.util.*

/**
 * The `ArgDelegator` implements an utility for handling args that have
 * been passed as command line arguments but that need the OSGi environment
 * and SIP Communicator to be fully loaded. The class maintains a list of
 * registered delegates (`ArgDelegationPeer`s) that do the actual arg
 * handling. The `ArgDelegator` is previewed for use with the SIP
 * Communicator argdelegation service. It would therefore record all args
 * until the corresponding `DelegationPeer` has registered here.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
internal class ArgDelegator {
    /**
     * The delegation peer that we pass arguments to. This peer is going to
     * get set only after Felix starts and all its services have been properly loaded.
     */
    private var uriDelegationPeer: ArgDelegationPeer? = null

    /**
     * We use this list to store arguments that we have been asked to handle
     * before we had a registered delegation peer.
     */
    private val recordedArgs = LinkedList<String>()

    /**
     * Passes the `uriArg` to the uri delegation peer or, in case
     * no peer is currently registered, stores it and keeps it until one
     * appears.
     *
     * @param uriArg the uri argument that we'd like to delegate to our peer.
     */
    fun handleUri(uriArg: String) {
        synchronized(recordedArgs) {
            if (uriDelegationPeer == null) {
                recordedArgs.add(uriArg)
                return
            }
        }
        uriDelegationPeer!!.handleUri(uriArg)
    }

    /**
     * Sets a delegation peer that we can now use to pass arguments to and
     * makes it handle all arguments that have been already registered.
     *
     * @param delegationPeer the delegation peer that we can use to deliver
     * command line URIs to.
     */
    fun setDelegationPeer(delegationPeer: ArgDelegationPeer?) {
        synchronized(recordedArgs) {
            Timber.log(TimberLog.FINER, "Someone set a delegationPeer. Will dispatch %s args", recordedArgs.size)
            uriDelegationPeer = delegationPeer
            for (arg in recordedArgs) {
                Timber.log(TimberLog.FINER, "Dispatching arg: %s", arg)
                uriDelegationPeer!!.handleUri(arg)
            }
            recordedArgs.clear()
        }
    }

    /**
     * Called when the user has tried to launch a second instance of
     * SIP Communicator while a first one was already running. This method
     * simply calls its peer method from the `ArgDelegationPeer` and
     * does nothing if no peer is currently registered.
     */
    fun handleConcurrentInvocationRequest() {
        synchronized(recordedArgs) { if (uriDelegationPeer != null) uriDelegationPeer!!.handleConcurrentInvocationRequest() }
    }
}