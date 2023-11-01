/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.event.CsrcAudioLevelListener
import org.atalk.service.neomedia.event.DTMFListener
import org.atalk.service.neomedia.event.SimpleAudioLevelListener

/**
 * Extends the `MediaStream` interface and adds methods specific to audio streaming.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface AudioMediaStream : MediaStream {
    /**
     * Registers a listener that would receive notification events if the remote party starts
     * sending DTMF tones to us.
     *
     * @param listener
     * the `DTMFListener` that we'd like to register.
     */
    fun addDTMFListener(listener: DTMFListener?)

    /**
     * Removes `listener` from the list of `DTMFListener`s registered to receive
     * events for incoming DTMF tones.
     *
     * @param listener
     * the listener that we'd like to unregister
     */
    fun removeDTMFListener(listener: DTMFListener)

    /**
     * Registers `listener` as the `CsrcAudioLevelListener` that will receive
     * notifications for changes in the levels of conference participants that the remote party
     * could be mixing.
     *
     * @param listener
     * the `CsrcAudioLevelListener` that we'd like to register or `null` if
     * we'd like to stop receiving notifications.
     */
    fun setCsrcAudioLevelListener(listener: CsrcAudioLevelListener?)

    /**
     * Sets `listener` as the `SimpleAudioLevelListener` registered to receive
     * notifications for changes in the levels of the audio that this stream is sending out.
     *
     * @param listener
     * the `SimpleAudioLevelListener` that we'd like to register or `null` if
     * we want to stop local audio level measurements.
     */
    fun setLocalUserAudioLevelListener(listener: SimpleAudioLevelListener?)

    /**
     * Sets the `VolumeControl` which is to control the volume (level) of the audio received
     * in/by this `AudioMediaStream` and played back.
     *
     * @param outputVolumeControl
     * the `VolumeControl` which is to control the volume (level) of the audio
     * received in this `AudioMediaStream` and played back
     */
    fun setOutputVolumeControl(outputVolumeControl: VolumeControl)

    /**
     * Sets `listener` as the `SimpleAudioLevelListener` registered to receive
     * notifications for changes in the levels of the party that's at the other end of this stream.
     *
     * @param listener
     * the `SimpleAudioLevelListener` that we'd like to register or `null` if
     * we want to stop stream audio level measurements.
     */
    fun setStreamAudioLevelListener(listener: SimpleAudioLevelListener?)

    /**
     * Starts sending the specified `DTMFTone` until the `stopSendingDTMF()` method is
     * called (Excepts for INBAND DTMF, which stops by itself this is why where there is no need to
     * call the stopSendingDTMF). Callers should keep in mind the fact that calling this method
     * would most likely interrupt all audio transmission until the corresponding stop method is
     * called. Also, calling this method successively without invoking the corresponding stop method
     * between the calls will simply replace the `DTMFTone` from the first call with that
     * from the second.
     *
     * @param tone
     * the `DTMFTone` to start sending.
     * @param dtmfMethod
     * The kind of DTMF used (RTP, SIP-INOF or INBAND).
     * @param minimalToneDuration
     * The minimal DTMF tone duration.
     * @param maximalToneDuration
     * The maximal DTMF tone duration.
     * @param volume
     * The DTMF tone volume. Describes the power level of the tone, expressed in dBm0 after
     * dropping the sign.
     */
    fun startSendingDTMF(tone: DTMFTone, dtmfMethod: DTMFMethod?, minimalToneDuration: Int,
                         maximalToneDuration: Int, volume: Int)

    /**
     * Interrupts transmission of a `DTMFTone` started with the `startSendingDTMF`
     * method. This method has no effect if no tone is being currently sent.
     *
     * @param dtmfMethod
     * the `DTMFMethod` to stop sending.
     */
    fun stopSendingDTMF(dtmfMethod: DTMFMethod?)

    companion object {
        /**
         * The name of the property which controls whether handling of RFC4733 DTMF packets should be
         * disabled or enabled. If disabled, packets will not be processed or dropped (regardless of
         * whether there is a payload type number registered for the telephone-event format).
         */
        val DISABLE_DTMF_HANDLING_PNAME = AudioMediaStream::class.java.name + ".DISABLE_DTMF_HANDLING"
    }
}