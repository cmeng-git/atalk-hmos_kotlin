/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media

import net.java.sip.communicator.service.protocol.*
import net.java.sip.communicator.service.protocol.event.CallEvent
import net.java.sip.communicator.service.protocol.event.CallListener
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.recording.Recorder
import org.atalk.util.MediaType
import timber.log.Timber
import java.text.ParseException

/**
 * Represents a default implementation of `OperationSetBasicTelephony` in order to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific
 * details.
 *
 * @param <T> the implementation specific provider class like for example `ProtocolProviderServiceSipImpl`.
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
</T> */
abstract class AbstractOperationSetBasicTelephony<T : ProtocolProviderService> : OperationSetBasicTelephony<T> {
    /**
     * A list of listeners registered for call events.
     */
    private val callListeners: MutableList<CallListener> = ArrayList<CallListener>()

    /**
     * {@inheritDoc}
     *
     * Forwards to [OperationSetBasicTelephony.createCall] with
     * `null` as the `CallConference` argument.
     */
    @Throws(OperationFailedException::class)
    override fun createCall(callee: Contact): Call<*>? {
        return createCall(callee, null)
    }

    /**
     * {@inheritDoc}
     *
     * Forwards to [OperationSetBasicTelephony.createCall] with
     * [Contact.address] as the `String` argument.
     */
    @Throws(OperationFailedException::class)
    override fun createCall(callee: Contact, conference: CallConference?): Call<*>? {
        return try {
            createCall(callee.address, conference)
        } catch (pe: ParseException) {
            throw OperationFailedException(pe.message, OperationFailedException.ILLEGAL_ARGUMENT, pe)
        }
    }

    /**
     * {@inheritDoc}
     *
     * Forwards to [OperationSetBasicTelephony.createCall] with
     * `null` as the `CallConference` argument.
     */
    @Throws(OperationFailedException::class, ParseException::class)
    override fun createCall(uri: String): Call<*>? {
        return createCall(uri, null)
    }

    /**
     * {@inheritDoc}
     *
     * Always throws an exception.
     */
    @Throws(OperationFailedException::class)
    override fun createCall(cd: ConferenceDescription?, chatRoom: ChatRoom?): Call<*>? {
        throw OperationFailedException("Creating a call with a ConferenceDescription is not implemented in "
                + javaClass, OperationFailedException.INTERNAL_ERROR)
    }
    /**
     * Creates and dispatches a `CallEvent` notifying registered listeners that an event with
     * id `eventID` has occurred on `sourceCall`.
     *
     * eventID the ID of the event to dispatch
     * sourceCall the call on which the event has occurred.
     * mediaDirections direction map for media types
     */
    /**
     * Creates and dispatches a `CallEvent` notifying registered listeners that an event with
     * id `eventID` has occurred on `sourceCall`.
     *
     * @param eventID the ID of the event to dispatch
     * @param sourceCall the call on which the event has occurred.
     */
    @JvmOverloads
    fun fireCallEvent(eventID: Int, sourceCall: Call<*>, mediaDirections: Map<MediaType, MediaDirection>? = null) {
        fireCallEvent(CallEvent(sourceCall, eventID, mediaDirections))
    }

    /**
     * Creates and dispatches a `CallEvent` notifying registered listeners that an event with
     * id `eventID` has occurred on `sourceCall`.
     *
     * @param event the event to dispatch
     */
    private fun fireCallEvent(event: CallEvent) {
        var listeners: List<CallListener?>
        synchronized(callListeners) { listeners = ArrayList<CallListener>(callListeners) }
        Timber.log(TimberLog.FINER, "Dispatching a CallEvent to %s listeners. The event is: %s", listeners.size, event)
        for (listener in listeners) {
            Timber.log(TimberLog.FINER, "Dispatching a CallEvent to %s. The event is: %s", listener!!.javaClass, event)
            when (event.eventID) {
                CallEvent.CALL_INITIATED -> listener.outgoingCallCreated(event)
                CallEvent.CALL_RECEIVED -> listener.incomingCallReceived(event)
                CallEvent.CALL_ENDED -> listener.callEnded(event)
            }
        }
    }

    /**
     * Registers `listener` with this provider, so that it could be notified when incoming calls are received.
     *
     * @param listener the listener to register with this provider.
     */
    override fun addCallListener(listener: CallListener) {
        synchronized(callListeners) { if (!callListeners.contains(listener)) callListeners.add(listener) }
    }

    /**
     * Removes the `listener` from the list of call listeners.
     *
     * @param listener the listener to unregister.
     */
    override fun removeCallListener(listener: CallListener) {
        synchronized(callListeners) { callListeners.remove(listener) }
    }

    /**
     * Sets the mute state of the `Call`.
     *
     * Muting audio streams sent from the call is implementation specific and one of the possible
     * approaches to it is sending silence.
     *
     * @param call the `Call` whose mute state is to be set
     * @param mute `true` to mute the call streams being sent to `peers`; otherwise, `false`
     */
    override fun setMute(call: Call<*>?, mute: Boolean) {
        if (call is MediaAwareCall<*, *, *>) call.isMute = mute else {
            /*
             * While throwing UnsupportedOperationException may be a possible approach,
             * putOnHold/putOffHold just do nothing when not supported so this implementation takes
             * inspiration from them.
             */
        }
    }

    /**
     * Creates a new `Recorder` which is to record the specified `Call` (into a file
     * which is to be specified when starting the returned `Recorder`).
     *
     * `AbstractOperationSetBasicTelephony` implements the described functionality for
     * `MediaAwareCall` only; otherwise, does nothing and just returns `null`.
     *
     *
     * @param call the `Call` which is to be recorded by the returned `Recorder` when the
     * latter is started
     * @return a new `Recorder` which is to record the specified `call` (into a file
     * which is to be specified when starting the returned `Recorder`)
     * @throws OperationFailedException if anything goes wrong while creating the new `Recorder` for the specified
     * `call`
     * @see OperationSetBasicTelephony.createRecorder
     */
    @Throws(OperationFailedException::class)
    override fun createRecorder(call: Call<*>?): Recorder? {
        return if (call is MediaAwareCall<*, *, *>) call.createRecorder() else null
    }
}