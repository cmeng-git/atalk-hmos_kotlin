/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.CallChangeAdapter
import net.java.sip.communicator.service.protocol.event.CallChangeEvent
import net.java.sip.communicator.service.protocol.event.CallEvent
import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.util.*

/**
 * Keeps a list of all calls currently active and maintained by this protocol provider. Offers
 * methods for finding a call by its ID, peer session and others. This class is meant for use by
 * protocol implementations and cannot be accessed from other bundles.
 *
 * @param <T> `Call`
 * @param <U> `OperationSetBasicTelephony`
 * @author Emil Ivov
 * @author Eng Chong Meng
</U></T> */
abstract class ActiveCallsRepository<T : Call<*>?, U : OperationSetBasicTelephony<out ProtocolProviderService>?>
/**
 * Creates a new instance of this repository.
 *
 * opSet a reference to the `AbstractOperationSetBasicTelephony` extension that created us.
 */
(
    /**
     * The operation set that created us. Instance is mainly used for firing events when necessary.
     */
    protected val parentOperationSet: U) : CallChangeAdapter() {
    /**
     * A table mapping call ids against call instances.
     */
    private val activeCalls = Hashtable<String?, T>()

    /**
     * Adds the specified call to the list of calls tracked by this repository.
     *
     * @param call CallSipImpl
     */
    fun addCall(call: T) {
        activeCalls[call!!.callId] = call
        call.addCallChangeListener(this)
    }

    /**
     * If `evt` indicates that the call has been ended we remove it from the repository.
     *
     * @param evt the `CallChangeEvent` instance containing the source calls and its old and new
     * state.
     */
    override fun callStateChanged(evt: CallChangeEvent) {
        if (evt.eventType == CallChangeEvent.CALL_STATE_CHANGE && evt.newValue == CallState.CALL_ENDED) {
            val sourceCall = activeCalls.remove(evt.sourceCall.callId)
            Timber.log(TimberLog.FINER, "Removing call %s from the list of active calls because it entered an ENDED state", sourceCall)
            fireCallEvent(CallEvent.CALL_ENDED, sourceCall!!)
        }
    }

    /**
     * Returns an iterator over all currently active (non-ended) calls.
     *
     * @return an iterator over all currently active (non-ended) calls.
     */
    fun getActiveCalls(): Iterator<T> {
        synchronized(activeCalls) {
            /*
             * Given that we know the elements that will go into the new List, it is more optimal in
             * terms of memory and execution time to use ArrayList rather than LinkedList.
             */
            return ArrayList(activeCalls.values).iterator()
        }
    }

    /**
     * Returns the number of calls currently tracked by this repository.
     *
     * @return the number of calls currently tracked by this repository.
     */
    fun getActiveCallCount(): Int {
        synchronized(activeCalls) { return activeCalls.size }
    }

    /**
     * Creates and dispatches a `CallEvent` notifying registered listeners that an event with
     * id `eventID` has occurred on `sourceCall`.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred.
     */
    protected fun fireCallEvent(eventID: Int, sourceCall: Call<*>) {
        fireCallEvent(eventID, sourceCall, null)
    }

    /**
     * Creates and dispatches a `CallEvent` notifying registered listeners that an event with
     * id `eventID` has occurred on `sourceCall`.
     *
     *
     * TODO The method is ugly because it can be implemented if `parentOperationSet` is an
     * `AbstractOperationSetBasicTelephony`. But after the move of the latter in the
     * `.service.protocol.media` package, it is not visible here.
     *
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred
     * @param cause the `CallChangeEvent`, if any, which is the cause that necessitated a new
     * `CallEvent` to be fired
     */
    protected abstract fun fireCallEvent(eventID: Int, sourceCall: Call<*>, cause: CallChangeEvent?)
}