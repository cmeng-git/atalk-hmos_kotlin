/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.calendar.CalendarService
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallChangeListener
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.CallListener
import net.java.sip.communicator.service.protocol.event.CallPeerEvent
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import org.osgi.framework.BundleContext
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern

/**
 * Imposes the policy to have one call in progress i.e. to put existing calls on hold when a new
 * call enters in progress.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class SingleCallInProgressPolicy(
        /**
         * The `BundleContext` to the Calls of which this policy applies.
         */
        private val bundleContext: BundleContext) {
    /**
     * The `Call`s this policy manages i.e. put on hold when one of them enters in progress.
     */
    private val calls = ArrayList<Call<*>?>()

    /**
     * The listener utilized by this policy to discover new `Call` and track their in-progress state.
     */
    private val listener = SingleCallInProgressPolicyListener()

    /**
     * The implementation of the policy to have the presence statuses of online accounts (i.e. registered
     * `ProtocolProviderService`s) set to &quot;On the phone&quot; when at least one `Call` is in progress.
     */
    private val onThePhoneStatusPolicy = OnThePhoneStatusPolicy()

    /**
     * Initializes a new `SingleCallInProgressPolicy` instance which will apply to the
     * `Call`s of a specific `BundleContext`.
     *
     * bundleContext the `BundleContext` to the `Call`s of which the new policy should apply
     */
    init {
        if (ProtocolProviderActivator.getConfigurationService()!!
                        .getBoolean(PNAME_SINGLE_CALL_IN_PROGRESS_POLICY_ENABLED, true)) {
            bundleContext.addServiceListener(listener)
        }
    }

    /**
     * Registers a specific `Call` with this policy in order to have the rules of the latter apply to the former.
     *
     * @param call the `Call` to register with this policy in order to have the rules of the
     * latter apply to the former
     */
    private fun addCallListener(call: Call<*>?) {
        Timber.log(TimberLog.FINER, "Add call change listener")
        synchronized(calls) {
            if (!calls.contains(call)) {
                val callState = call!!.callState
                if (callState != null && callState != CallState.CALL_ENDED) {
                    calls.add(call)
                }
            }
        }
        call!!.addCallChangeListener(listener)
    }

    /**
     * Registers a specific `OperationSetBasicTelephony` with this policy in order to have
     * the rules of the latter apply to the `Call`s created by the former.
     *
     * @param telephony the `OperationSetBasicTelephony` to register with this policy in order to have
     * the rules of the latter apply to the `Call`s created by the former
     */
    private fun addOperationSetBasicTelephonyListener(
            telephony: OperationSetBasicTelephony<out ProtocolProviderService>) {
        Timber.log(TimberLog.FINER, "Call listener added to provider.")
        telephony.addCallListener(listener)
    }

    /**
     * Handles changes in the state of a `Call` this policy applies to in order to detect
     * when new calls become in-progress and when the other calls should be put on hold.
     *
     * @param ev a `CallChangeEvent` value which describes the `Call` and the change in its state
     */
    private fun callStateChanged(ev: CallChangeEvent) {
        val call = ev.sourceCall
        Timber.log(TimberLog.FINER, "Call state changed.")
        if (CallState.CALL_INITIALIZATION == ev.oldValue && CallState.CALL_IN_PROGRESS == call.callState) {
            val conference = call.getConference()
            synchronized(calls) {
                for (otherCall in calls) {
                    if (call != otherCall && CallState.CALL_IN_PROGRESS == otherCall!!.callState) {
                        /*
                         * Only put on hold calls which are visually distinctive from the specified
                         * call i.e. do not put on hold calls which participate in the same
                         * telephony conference as the specified call.
                         */
                        var putOnHold: Boolean
                        val otherConference = otherCall.getConference()
                        if (conference == null)
                            putOnHold = otherConference == null
                        else
                            putOnHold = conference != otherConference
                        if (putOnHold) putOnHold(otherCall)
                    }
                }
            }
        } else if (CallState.CALL_ENDED == ev.newValue) {
            handleCallEvent(CallEvent.CALL_ENDED, call)
        }

        /*
         * Forward to onThePhoneStatusPolicy for which we are proxying the Call-related events.
         */
        onThePhoneStatusPolicy.callStateChanged(ev)
    }

    /**
     * Performs end-of-life cleanup associated with this instance e.g. removes added listeners.
     */
    fun dispose() {
        bundleContext.removeServiceListener(listener)
    }

    /**
     * Handles the start and end of the `Call`s this policy applies to in order to have them
     * or stop having them put the other existing calls on hold when the former change their states
     * to `CallState.CALL_IN_PROGRESS`.
     *
     *
     * Also handles call rejection via "busy here" according to the call policy.
     *
     *
     * @param type one of [CallEvent.CALL_ENDED], [CallEvent.CALL_INITIATED] and
     * [CallEvent.CALL_RECEIVED] which describes the type of the event to be handled
     * @param ev a `CallEvent` value which describes the change and the `Call` associated with it
     */
    private fun handleCallEvent(type: Int, call: Call<*>?) {
        Timber.log(TimberLog.FINER, "Call event fired.")
        when (type) {
            CallEvent.CALL_ENDED -> removeCallListener(call)
            CallEvent.CALL_INITIATED, CallEvent.CALL_RECEIVED -> addCallListener(call)
        }
        /*
         * Forward to onThePhoneStatusPolicy for which we are proxying the Call-related events.
         */
        onThePhoneStatusPolicy.handleCallEvent(type, call)
    }

    /**
     * Notifies this instance that an incoming `Call` has been received.
     *
     * @param ev a `CallEvent` which describes the received incoming `Call`
     */
    private fun incomingCallReceived(ev: CallEvent) {
        val call = ev.sourceCall

        // check whether we should hangup this call saying we are busy already on call
        if (CallState.CALL_INITIALIZATION == call.callState) {
            val config = ProtocolProviderActivator.getConfigurationService()
            if (config!!.getBoolean(PNAME_CALL_WAITING_DISABLED, false)) {
                var rejectCallWithBusyHere = false
                synchronized(calls) {
                    for (otherCall in calls) {
                        if (call != otherCall && CallState.CALL_IN_PROGRESS == otherCall!!.callState) {
                            rejectCallWithBusyHere = true
                            break
                        }
                    }
                }
                if (rejectCallWithBusyHere) {
                    rejectCallWithBusyHere(call)
                    return
                }
            }

            val provider = call.pps
            if (config.getBoolean(PNAME_REJECT_IN_CALL_ON_DND, false)
                    || provider.accountID.getAccountPropertyBoolean(
                            ACCOUNT_PROPERTY_REJECT_IN_CALL_ON_DND, false)) {
                var presence = provider.getOperationSet(OperationSetPresence::class.java)

                // if our provider has no presence op set, lets search for connected provider which will have
                if (presence == null) {
                    // There is no presence OpSet. Let's check the connected CUSAX provider if available
                    val cusaxOpSet = provider.getOperationSet(OperationSetCusaxUtils::class.java)
                    if (cusaxOpSet != null) {
                        val linkedCusaxProvider = cusaxOpSet.getLinkedCusaxProvider()
                        if (linkedCusaxProvider != null) {
                            // we found the provider, let's take its presence opset
                            presence = linkedCusaxProvider.getOperationSet(OperationSetPresence::class.java)
                        }
                    }
                }
                if (presence != null) {
                    val presenceStatus = presence.getPresenceStatus()?.status
                            ?: PresenceStatus.AVAILABLE_THRESHOLD

                    // between AVAILABLE and EXTENDED AWAY (>20, <= 31) are the busy statuses as DND and On the phone
                    if (presenceStatus > PresenceStatus.ONLINE_THRESHOLD
                            && presenceStatus <= PresenceStatus.EXTENDED_AWAY_THRESHOLD) {
                        rejectCallWithBusyHere(call)
                        return
                    }
                }
            }
        }
        handleCallEvent(CallEvent.CALL_RECEIVED, call)
    }

    /**
     * Puts the `CallPeer`s of a specific `Call` on hold.
     *
     * @param call the `Call` the `CallPeer`s of which are to be put on hold
     */
    private fun putOnHold(call: Call<*>?) {
        val telephony = call!!.pps.getOperationSet(OperationSetBasicTelephony::class.java)
        if (telephony != null) {
            val peerIter = call.getCallPeers()
            while (peerIter.hasNext()) {
                val peer = peerIter.next()
                val peerState = peer!!.getState()
                if (CallPeerState.DISCONNECTED != peerState
                        && CallPeerState.FAILED != peerState
                        && !CallPeerState.isOnHold(peerState)) {
                    try {
                        telephony.putOnHold(peer)
                    } catch (ex: OperationFailedException) {
                        Timber.e(ex, "Failed to put %s on hold.", peer)
                    }
                }
            }
        }
    }

    /**
     * Rejects a `call` with busy here code.
     *
     * @param call the call to reject.
     */
    private fun rejectCallWithBusyHere(call: Call<*>?) {
        // We're interested in one-to-one incoming calls.
        if (call!!.callPeerCount == 1) {
            val peer = call.getCallPeers().next()
            val telephony = call.pps.getOperationSet(OperationSetBasicTelephony::class.java)
            if (telephony != null) {
                try {
                    telephony.hangupCallPeer(peer, OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE, null)
                } catch (ex: OperationFailedException) {
                    Timber.e(ex, "Failed to reject %s", peer)
                }
            }
        }
    }

    /**
     * Unregisters a specific `Call` from this policy in order to have the rules of the
     * latter no longer applied to the former.
     *
     * @param call the `Call` to unregister from this policy in order to have the rules of the
     * latter no longer apply to the former
     */
    private fun removeCallListener(call: Call<*>?) {
        Timber.log(TimberLog.FINER, "Remove call change listener.")
        call!!.removeCallChangeListener(listener)
        synchronized(calls) { calls.remove(call) }
    }

    /**
     * Unregisters a specific `OperationSetBasicTelephony` from this policy in order to have
     * the rules of the latter no longer apply to the `Call`s created by the former.
     *
     * @param telephony the `OperationSetBasicTelephony` to unregister from this policy in order to
     * have the rules of the latter apply to the `Call`s created by the former
     */
    private fun removeOperationSetBasicTelephonyListener(
            telephony: OperationSetBasicTelephony<out ProtocolProviderService>) {
        telephony.removeCallListener(listener)
    }

    /**
     * Handles the registering and unregistering of `OperationSetBasicTelephony` instances in
     * order to apply or unapply the rules of this policy to the `Call`s originating from them.
     *
     * @param ev a `ServiceEvent` value which described a change in an OSGi service and which is
     * to be examined for the registering or unregistering of a
     * `ProtocolProviderService` and thus a `OperationSetBasicTelephony`
     */
    private fun serviceChanged(ev: ServiceEvent) {
        val service = bundleContext.getService(ev.serviceReference)
        if (service is ProtocolProviderService) {
            Timber.log(TimberLog.FINER, "Protocol provider service changed.")
            val telephony = service.getOperationSet(OperationSetBasicTelephony::class.java)
            if (telephony != null) {
                when (ev.type) {
                    ServiceEvent.REGISTERED -> addOperationSetBasicTelephonyListener(telephony)
                    ServiceEvent.UNREGISTERING -> removeOperationSetBasicTelephonyListener(telephony)
                }
            } else {
                Timber.log(TimberLog.FINER, "The protocol provider service doesn't support " + "telephony.")
            }
        }
    }

    /**
     * Implements the policy to have the presence statuses of online accounts (i.e. registered
     * `ProtocolProviderService`s) set to &quot;On the phone&quot; when at least one `Call` is in progress.
     *
     * @author Lyubomir Marinov
     */
    private inner class OnThePhoneStatusPolicy {
        /**
         * The regular expression which removes whitespace from the `statusName` property
         * value of `PresenceStatus` instances in order to recognize the
         * `PresenceStatus` which represents &quot;On the phone&quot;.
         */
        private val presenceStatusNameWhitespace = Pattern.compile("\\s")

        /**
         * The `PresenceStatus`es of `ProtocolProviderService`s before they were
         * changed to &quot;On the phone&quot; remembered so that they can be restored after the
         * last `Call` in progress ends.
         */
        private val presenceStatuses = Collections.synchronizedMap(WeakHashMap<ProtocolProviderService?, PresenceStatus?>())

        /**
         * Notifies this instance that the `callState` of a specific `Call` has changed.
         *
         * @param ev a `CallChangeEvent` which represents the details of the notification such
         * as the affected `Call` and its old and new `CallState`s
         */
        fun callStateChanged(ev: CallChangeEvent) {
            Timber.log(TimberLog.FINER, "Call state changed.[2]")
            val call = ev.sourceCall
            val oldCallState = ev.oldValue
            val newCallState = call.callState
            if ((CallState.CALL_INITIALIZATION == oldCallState && CallState.CALL_IN_PROGRESS == newCallState || CallState.CALL_IN_PROGRESS == oldCallState && CallState.CALL_ENDED == newCallState)) {
                run()
            } else {
                Timber.log(TimberLog.FINER, "Not applicable call state.")
            }
        }

        /**
         * Finds the first `PresenceStatus` among the set of `PresenceStatus`es
         * supported by a specific `OperationSetPresence` which represents &quot; On the phone&quot;.
         *
         * @param presence the `OperationSetPresence` which represents the set of supported
         * `PresenceStatus`es
         * @return the first `PresenceStatus` among the set of `PresenceStatus`es
         * supported by `presence` which represents &quot;On the phone&quot; if such
         * a `PresenceStatus` was found; otherwise, `null`
         */
        private fun findOnThePhonePresenceStatus(presence: OperationSetPresence): PresenceStatus? {
            for (presenceStatus in presence.getSupportedStatusSet()) {
                if (presenceStatusNameWhitespace.matcher(presenceStatus!!.statusName)
                                .replaceAll("").equals("OnThePhone", ignoreCase = true)) {
                    return presenceStatus
                }
            }
            return null
        }

        private fun forgetPresenceStatus(pps: ProtocolProviderService): PresenceStatus? {
            return presenceStatuses.remove(pps)
        }

        private fun forgetPresenceStatuses() {
            presenceStatuses.clear()
        }

        /**
         * Notifies this instance that a new outgoing `Call` was initiated, an incoming
         * `Call` was received or an existing `Call` ended.
         *
         * @param type one of [CallEvent.CALL_ENDED], [CallEvent.CALL_INITIATED] and
         * [CallEvent.CALL_RECEIVED] which describes the type of the event to be handled
         * @param call the `Call` instance.
         */
        fun handleCallEvent(type: Int, call: Call<*>?) {
            run()
        }

        /**
         * Determines whether there is at least one existing `Call` which is currently in
         * progress i.e. determines whether the local user is currently on the phone.
         *
         * @return `true` if there is at least one existing `Call` which is currently
         * in progress i.e. if the local user is currently on the phone; otherwise, `false`
         */
        private fun isOnThePhone(): Boolean {
            synchronized(calls) {
                for (call in calls) {
                    if (CallState.CALL_IN_PROGRESS == call!!.callState) return true
                }
            }
            return false
        }

        /**
         * Invokes [OperationSetPresence.publishPresenceStatus] on a
         * specific `OperationSetPresence` with a specific `PresenceStatus` and catches any exceptions.
         *
         * @param presence the `OperationSetPresence` on which the method is to be invoked
         * @param presenceStatus the `PresenceStatus` to provide as the respective method argument value
         */
        private fun publishPresenceStatus(presence: OperationSetPresence, presenceStatus: PresenceStatus) {
            try {
                presence.publishPresenceStatus(presenceStatus, null)
            } catch (t: Throwable) {
                if (t is ThreadDeath) throw t
            }
        }

        private fun rememberPresenceStatus(pps: ProtocolProviderService?, presenceStatus: PresenceStatus?): PresenceStatus? {
            return presenceStatuses.put(pps, presenceStatus)
        }

        /**
         * Finds the first `PresenceStatus` among the set of `PresenceStatus`es
         * supported by a specific `OperationSetPresence` which represents &quot; In meeting&quot;.
         *
         * @param presence the `OperationSetPresence` which represents the set of supported `PresenceStatus`es
         * @return the first `PresenceStatus` among the set of `PresenceStatus`es
         * supported by `presence` which represents &quot;In meeting&quot; if such a
         * `PresenceStatus` was found; otherwise, `null`
         */
        private fun findInMeetingPresenceStatus(presence: OperationSetPresence): PresenceStatus? {
            for (presenceStatus in presence.getSupportedStatusSet()) {
                if (presenceStatusNameWhitespace.matcher(presenceStatus!!.statusName)
                                .replaceAll("").equals("InAMeeting", ignoreCase = true)) {
                    return presenceStatus
                }
            }
            return null
        }

        /**
         * Applies this policy to the current state of the application.
         */
        private fun run() {
            Timber.log(TimberLog.FINER, "On the phone status policy run.")
            if (!ProtocolProviderActivator.getConfigurationService()!!.getBoolean(
                            PNAME_ON_THE_PHONE_STATUS_ENABLED, false)) {
                Timber.log(TimberLog.FINER, "On the phone status is not enabled.")
                forgetPresenceStatuses()
                return
            }

            val ppsRefs = try {
                bundleContext.getServiceReferences(ProtocolProviderService::class.java.name, null)
            } catch (ise: InvalidSyntaxException) {
                Timber.log(TimberLog.FINER, "Can't access protocol providers refences.")
                null
            }

            if (ppsRefs == null || ppsRefs.isEmpty()) {
                forgetPresenceStatuses()
            } else {
                val isOnThePhone = isOnThePhone()
                val calendar = ProtocolProviderActivator.calendarService
                if (!isOnThePhone && calendar != null && calendar.onThePhoneStatusChanged(presenceStatuses)) {
                    Timber.log(TimberLog.FINER, "We are not on the phone.")
                    forgetPresenceStatuses()
                    return
                }
                for (ppsRef in ppsRefs) {
                    val pps = bundleContext.getService(ppsRef) as ProtocolProviderService?
                    if (pps == null) {
                        Timber.log(TimberLog.FINER, "Provider is null.")
                        continue
                    }

                    val presence = pps.getOperationSet(OperationSetPresence::class.java)
                    if (presence == null) {
                        Timber.log(TimberLog.FINER, "Presence is null.")
                        /*
                         * "On the phone" is a PresenceStatus so it is available only to accounts
                         * which support presence in the first place.
                         */
                        forgetPresenceStatus(pps)
                    } else if (pps.isRegistered) {
                        Timber.log(TimberLog.FINER, "Provider is registered.")
                        val onThePhonePresenceStatus = findOnThePhonePresenceStatus(presence)
                        if (onThePhonePresenceStatus == null) {
                            Timber.log(TimberLog.FINER, "Can't find on the phone status.")
                            /*
                             * If do not know how to define "On the phone" for an OperationSetPresence,
                             * then we'd better not mess with it in the first place.
                             */
                            forgetPresenceStatus(pps)
                        } else if (isOnThePhone) {
                            Timber.log(TimberLog.FINER, "Setting the status to on the phone.")
                            val presenceStatus = presence.getPresenceStatus()
                            if (presenceStatus == null) {
                                Timber.log(TimberLog.FINER, "Presence status is null.")
                                /*
                                 * It is strange that an OperationSetPresence does not have a
                                 * PresenceStatus so it may be safer to not mess with it.
                                 */
                                forgetPresenceStatus(pps)
                            } else if (onThePhonePresenceStatus != presenceStatus) {
                                Timber.log(TimberLog.FINER, "On the phone status is published.")
                                publishPresenceStatus(presence, onThePhonePresenceStatus)
                                if (presenceStatus == findInMeetingPresenceStatus(presence) && calendar != null) {
                                    val statuses = calendar.rememberedStatuses
                                    for (provider in statuses!!.keys) rememberPresenceStatus(provider, statuses[provider])
                                } else if (onThePhonePresenceStatus == presence.getPresenceStatus()) {
                                    rememberPresenceStatus(pps, presenceStatus)
                                } else {
                                    forgetPresenceStatus(pps)
                                }
                            } else {
                                Timber.log(TimberLog.FINER, "Currently the status is on the phone.")
                            }
                        } else {
                            Timber.log(TimberLog.FINER, "Unset on the phone status.")
                            val presenceStatus = forgetPresenceStatus(pps)
                            if (presenceStatus != null && onThePhonePresenceStatus == presence.getPresenceStatus()) {
                                Timber.log(TimberLog.FINER, "Unset on the phone status.[2]")
                                publishPresenceStatus(presence, presenceStatus)
                            }
                        }
                    } else {
                        Timber.log(TimberLog.FINER, "Protocol provider is not registered")
                        /*
                         * Offline accounts do not get their PresenceStatus modified for the purposes of "On the phone".
                         */
                        forgetPresenceStatus(pps)
                    }
                }
            }
        }
    }

    /**
     * Implements the listeners interfaces used by this policy.
     *
     * @author Lyubomir Marinov
     */
    private inner class SingleCallInProgressPolicyListener : CallChangeListener, CallListener, ServiceListener {
        /**
         * Stops tracking the state of a specific `Call` and no longer tries to put it on hold when it ends.
         *
         * @see CallListener.callEnded
         */
        override fun callEnded(event: CallEvent) {
            /*
             * Not using call ended, cause the CallListener is removed
             * when protocol disconnects and it can happen that this is
             * before the callEnded event in case of running call during
             * removing an account and this can lead to leaking calls.
             */
        }

        /**
         * Does nothing because adding `CallPeer`s to `Call`s isn't related to the
         * policy to put existing calls on hold when a new call becomes in-progress and just
         * implements `CallChangeListener`.
         *
         * @see CallChangeListener.callPeerAdded
        `` */
        override fun callPeerAdded(evt: CallPeerEvent) {
            /*
             * Not of interest, just implementing CallChangeListener in which only
             * #callStateChanged(CallChangeEvent) is of interest.
             */
        }

        /**
         * Does nothing because removing `CallPeer`s to `Call`s isn't related to the policy to put
         * existing calls on hold when a new call becomes in-progress and just implements `CallChangeListener`.
         *
         * @see CallChangeListener.callPeerRemoved
        `` */
        override fun callPeerRemoved(evt: CallPeerEvent) {
            /*
             * Not of interest, just implementing CallChangeListener in which only
             * #callStateChanged(CallChangeEvent) is of interest.
             */
        }

        /**
         * Upon a `Call` changing its state to `CallState.CALL_IN_PROGRESS`, puts the
         * other existing `Call`s on hold.
         *
         * @param evt the `CallChangeEvent` that we are to deliver.
         * @see CallChangeListener.callStateChanged
         */
        override fun callStateChanged(evt: CallChangeEvent) {
            // we are interested only in CALL_STATE_CHANGEs
            if (evt.eventType == CallChangeEvent.CALL_STATE_CHANGE) this@SingleCallInProgressPolicy.callStateChanged(evt)
        }

        /**
         * Remembers an incoming `Call` so that it can put the other existing `Call`s
         * on hold when it changes its state to `CallState.CALL_IN_PROGRESS`.
         *
         * @see CallListener.incomingCallReceived
         */
        override fun incomingCallReceived(event: CallEvent) {
            this@SingleCallInProgressPolicy.incomingCallReceived(event)
        }

        /**
         * Remembers an outgoing `Call` so that it can put the other existing `Call`s
         * on hold when it changes its state to `CallState.CALL_IN_PROGRESS`.
         *
         * @see CallListener.outgoingCallCreated
         */
        override fun outgoingCallCreated(event: CallEvent) {
            handleCallEvent(CallEvent.CALL_INITIATED, event.sourceCall)
        }

        /**
         * Starts/stops tracking the new `Call`s originating from a specific
         * `ProtocolProviderService` when it registers/unregisters in order to take them into
         * account when putting existing calls on hold upon a new call entering its in-progress state.
         *
         * @param ev the `ServiceEvent` event describing a change in the state of a service
         * registration which may be a `ProtocolProviderService` supporting
         * `OperationSetBasicTelephony` and thus being able to create new `Call`s
         */
        override fun serviceChanged(ev: ServiceEvent) {
            Timber.log(TimberLog.FINER, "Service changed.")
            this@SingleCallInProgressPolicy.serviceChanged(ev)
        }
    }

    companion object {
        /**
         * Account property to enable per account rejecting calls if the account presence is in DND or OnThePhone status.
         */
        private const val ACCOUNT_PROPERTY_REJECT_IN_CALL_ON_DND = "RejectIncomingCallsWhenDnD"

        /**
         * The name of the configuration property which specifies whether call waiting is disabled i.e.
         * whether it should reject new incoming calls when there are other calls already in progress.
         */
        private const val PNAME_CALL_WAITING_DISABLED = "protocol.CallWaitingDisabled"

        /**
         * The name of the configuration property which specifies whether
         * `OnThePhoneStatusPolicy` is enabled i.e. whether it should set the presence statuses
         * of online accounts to &quot;On the phone&quot; when at least one `Call` is in progress.
         */
        private const val PNAME_ON_THE_PHONE_STATUS_ENABLED = "protocol.OnThePhoneStatusPolicy.enabled"

        /**
         * Global property which will enable rejecting incoming calls for all accounts, if the account
         * is in DND or OnThePhone status.
         */
        private const val PNAME_REJECT_IN_CALL_ON_DND = "protocol.$ACCOUNT_PROPERTY_REJECT_IN_CALL_ON_DND"

        /**
         * The name of the configuration property which specifies whether `SingleCallInProgressPolicy`
         * is enabled i.e. whether it should put existing calls on hold when a new call enters in progress.
         */
        private const val PNAME_SINGLE_CALL_IN_PROGRESS_POLICY_ENABLED = "protocol.SingleCallInProgressPolicy.enabled"
    }
}