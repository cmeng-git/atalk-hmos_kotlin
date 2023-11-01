/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.impl.neomedia.codec.REDBlock
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver
import org.atalk.impl.neomedia.rtp.StreamRTPManager
import org.atalk.impl.neomedia.rtp.TransportCCEngine
import org.atalk.impl.neomedia.transform.TransformEngine
import org.atalk.impl.neomedia.transform.TransformEngineChain
import org.atalk.service.neomedia.device.MediaDevice
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.service.neomedia.stats.MediaStreamStats2
import org.atalk.util.ByteArrayBuffer
import java.beans.PropertyChangeListener
import java.net.InetSocketAddress

/**
 * The `MediaStream` class represents a (generally) bidirectional RTP stream between exactly
 * two parties. The class reflects one of the media stream, in the SDP sense of the word.
 * `MediaStream` instances are created through the `openMediaStream()` method of the
 * `MediaService`.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
interface MediaStream {
    /**
     * Adds a new association in this `MediaStream` of the specified RTP payload type with
     * the specified `MediaFormat` in order to allow it to report `rtpPayloadType` in
     * RTP flows sending and receiving media in `format`. Usually, `rtpPayloadType`
     * will be in the range of dynamic RTP payload types.
     *
     * @param rtpPayloadType the RTP payload type to be associated in this `MediaStream`
     * with the specified `MediaFormat`
     * @param format the `MediaFormat` to be associated in this `MediaStream` with
     * `rtpPayloadType`
     */
    fun addDynamicRTPPayloadType(rtpPayloadType: Byte, format: MediaFormat)

    /**
     * Clears the dynamic RTP payload type associations in this `MediaStream`.
     */
    fun clearDynamicRTPPayloadTypes()

    /**
     * Adds an additional RTP payload mapping that will overriding one that we've set with
     * [.addDynamicRTPPayloadType]. This is necessary so that we can
     * support the RFC3264 case where the answerer has the right to declare what payload type
     * mappings it wants to receive RTP packets with even if they are different from those in the offer.
     * RFC3264 claims this is for support of legacy protocols such as H.323 but we've been bumping with
     * a number of cases where multi-component pure SIP systems also need to behave this way.
     *
     *
     *
     * @param originalPt the payload type that we are overriding
     * @param overloadPt the payload type that we are override it with
     */
    fun addDynamicRTPPayloadTypeOverride(originalPt: Byte, overloadPt: Byte)

    /**
     * Adds a property change listener to this stream so that it would be notified upon property
     * change events like for example an SSRC ID which becomes known.
     *
     * @param listener the listener that we'd like to register for `PropertyChangeEvent`s
     */
    fun addPropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Adds or updates an association in this `MediaStream` mapping the specified
     * `extensionID` to `rtpExtension` and enabling or disabling its use according to
     * the direction attribute of `rtpExtension`.
     *
     * @param extensionID the ID that is mapped to `rtpExtension` for the lifetime of this `MediaStream`.
     * @param rtpExtension the `RTPExtension` that we are mapping to `extensionID`.
     */
    fun addRTPExtension(extensionID: Byte, rtpExtension: RTPExtension)

    /**
     * Clears the RTP header extension associations stored in this instance.
     */
    fun clearRTPExtensions()

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     */
    fun close()

    /**
     * Returns a map containing all currently active `RTPExtension`s in use by this stream.
     *
     * @return a map containing all currently active `RTPExtension`s in use by this stream.
     */
    fun getActiveRTPExtensions(): Map<Byte, RTPExtension>

    /**
     * Gets the device that this stream uses to play back and capture media.
     *
     * @return the `MediaDevice` that this stream uses to play back and capture media.
     */
    /**
     * Sets the device that this stream should use to play back and capture media.
     *
     * @param device the `MediaDevice` that this stream should use to play back and capture media.
     */
    var device: MediaDevice?
    /**
     * Gets the direction in which this `MediaStream` is allowed to stream media.
     *
     * @return the `MediaDirection` in which this `MediaStream` is allowed to stream media
     */
    /**
     * Sets the direction in which media in this `MediaStream` is to be streamed. If this
     * `MediaStream` is not currently started, calls to [.start] later on will start
     * it only in the specified `direction`. If it is currently started in a direction
     * different than the specified, directions other than the specified will be stopped.
     *
     * @param direction the `MediaDirection` in which this `MediaStream`
     * is to stream media when it is started
     */
    var direction: MediaDirection

    /**
     * Gets the existing associations in this `MediaStream` of RTP payload types to
     * `MediaFormat`s. The returned `Map` only contains associations previously added
     * in this instance with [.addDynamicRTPPayloadType] and not globally
     * or well-known associations reported by [MediaFormat.getRTPPayloadType].
     *
     * @return a `Map` of RTP payload type expressed as `Byte` to
     * `MediaFormat` describing the existing (dynamic) associations in this instance of
     * RTP payload types to `MediaFormat`s. The `Map` represents a snapshot of the
     * existing associations at the time of the `getDynamicRTPPayloadTypes()` method call
     * and modifications to it are not reflected on the internal storage
     */
    fun getDynamicRTPPayloadTypes(): MutableMap<Byte, MediaFormat>

    /**
     * Returns the payload type number that has been negotiated for the specified
     * `encoding` or `-1` if no payload type has been negotiated for it. If
     * multiple formats match the specified `encoding`, then this method would return the
     * first one it encounters while iterating through the map.
     *
     * @param codec the encoding whose payload type we are trying to obtain.
     * @return the payload type number that has been negotiated for the specified `codec`
     * or `-1` if no payload type has been negotiated for it.
     */
    fun getDynamicRTPPayloadType(codec: String?): Byte
    /**
     * Returns the `MediaFormat` that this stream is currently transmitting in.
     *
     * @return the `MediaFormat` that this stream is currently transmitting in.
     */
    /**
     * Sets the `MediaFormat` that this `MediaStream` should transmit in.
     *
     * @param format the `MediaFormat` that this `MediaStream` should transmit in.
     */
    var format: MediaFormat?

    /**
     * Returns the `MediaFormat` that is associated to the payload type passed in as a parameter.
     *
     * @param payloadType the payload type of the `MediaFormat` to get.
     * @return the `MediaFormat` that is associated to the payload type passed in as a parameter.
     */
    fun getFormat(payloadType: Byte): MediaFormat?

    /**
     * Returns the synchronization source (SSRC) identifier of the local participant or `-1`
     * if that identifier is not known at this point.
     *
     * @return the synchronization source (SSRC) identifier of the local participant or `-1`
     * if that identifier is not known at this point.
     */
    fun getLocalSourceID(): Long

    /**
     * Returns a `MediaStreamStats` object used to get statistics about this `MediaStream`.
     *
     * @return the `MediaStreamStats` object used to get statistics about this `MediaStream`.
     */
    val mediaStreamStats: MediaStreamStats2
    /**
     * Returns the name of this stream or `null` if no name has been set. A stream name is
     * used by some protocols, for diagnostic purposes mostly. In XMPP for example this is the name
     * of the content element that describes a stream.
     *
     * @return the name of this stream or `null` if no name has been set.
     */
    /**
     * Sets the name of this stream. Stream names are used by some protocols, for diagnostic
     * purposes mostly. In XMPP for example this is the name of the content element that describes a stream.
     *
     * @param name the name of this stream or `null` if no name has been set.
     */
    var name: String?

    /**
     * Gets the value of a specific opaque property of this `MediaStream`.
     *
     * @param propertyName the name of the opaque property of this `MediaStream`
     * the value of which is to be returned
     * @return the value of the opaque property of this `MediaStream` specified by `propertyName`
     */
    fun getProperty(propertyName: String): Any?

    /**
     * Returns the address that this stream is sending RTCP traffic to.
     *
     * @return an `InetSocketAddress` instance indicating the address that we are sending RTCP packets to.
     */
    val remoteControlAddress: InetSocketAddress?

    /**
     * Returns the address that this stream is sending RTP traffic to.
     *
     * @return an `InetSocketAddress` instance indicating the address that we are sending RTP packets to.
     */
    val remoteDataAddress: InetSocketAddress?

    /**
     * Gets the synchronization source (SSRC) identifier of the remote peer or `-1` if that
     * identifier is not yet known at this point in the execution.
     *
     *
     * **Warning**: A `MediaStream` may receive multiple RTP streams and may thus have
     * multiple remote SSRCs. Since it is not clear how this `MediaStream` instance chooses
     * which of the multiple remote SSRCs to be returned by the method, it is advisable to always
     * consider [.getRemoteSourceIDs] first.
     *
     *
     * @return the synchronization source (SSRC) identifier of the remote peer or `-1` if
     * that identifier is not yet known at this point in the execution
     */
    fun getRemoteSourceID(): Long

    /**
     * Gets the synchronization source (SSRC) identifiers of the remote peer.
     *
     * @return the synchronization source (SSRC) identifiers of the remote peer
     */
    fun getRemoteSourceIDs(): List<Long>

    /**
     * Gets the `StreamRTPManager` which is to forward RTP and RTCP traffic between this
     * and other `MediaStream`s.
     *
     * @return the `StreamRTPManager` which is to forward RTP and RTCP traffic between this
     * and other `MediaStream`s
     */
    val streamRTPManager: StreamRTPManager?

    /**
     * The `ZrtpControl` which controls the ZRTP for this stream.
     *
     * @return the `ZrtpControl` which controls the ZRTP for this stream
     */
    val srtpControl: SrtpControl
    /**
     * Returns the target of this `MediaStream` to which it is to send and from which it is
     * to receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @return the `MediaStreamTarget` describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this `MediaStream` is to send and from which it is to receive
     * @see MediaStream.setTarget
     */
    /**
     * Sets the target of this `MediaStream` to which it is to send and from which it is to
     * receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @param target the `MediaStreamTarget` describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this `MediaStream` is to send and from which it is to receive
     */
    var target: MediaStreamTarget?

    /**
     * Returns the transport protocol used by the streams.
     *
     * @return the transport protocol (UDP or TCP) used by the streams. null if the stream connector is not instantiated.
     */
    val transportProtocol: StreamConnector.Protocol?
    /**
     * Determines whether this `MediaStream` is set to transmit "silence" instead of the
     * media being fed from its `MediaDevice`. "Silence" for video is understood as video
     * data which is not the captured video data and may represent, for example, a black image.
     *
     * @return `true` if this `MediaStream` is set to transmit "silence" instead of
     * the media fed from its `MediaDevice`; `false`, otherwise
     */
    /**
     * Causes this `MediaStream` to stop transmitting the media being fed from this stream's
     * `MediaDevice` and transmit "silence" instead. "Silence" for video is understood as
     * video data which is not the captured video data and may represent, for example, a black image.
     *
     * @param mute `true` if we are to start transmitting "silence" and `false` if we are
     * to use media from this stream's `MediaDevice` again.
     */
    var isMute: Boolean

    /**
     * Determines whether [.start] has been called on this `MediaStream` without
     * [.stop] or [.close] afterwards.
     *
     * @return `true` if [.start] has been called on this `MediaStream`
     * without [.stop] or [.close] afterwards
     */
    val isStarted: Boolean

    /**
     * Removes the specified property change `listener` from this stream so that it won't
     * receive further property change events.
     *
     * @param listener the listener that we'd like to remove.
     */
    fun removePropertyChangeListener(listener: PropertyChangeListener)

    /**
     * Removes the `ReceiveStream` with SSRC `ssrc`, if there is such a
     * `ReceiveStream`, from the receive streams of this `MediaStream`
     *
     * @param ssrc the SSRC for which to remove a `ReceiveStream`
     */
    fun removeReceiveStreamForSsrc(ssrc: Long)

    /**
     * Sets the `StreamConnector` to be used by this `MediaStream` for sending and receiving media.
     *
     * @param connector the `StreamConnector` to be used by this `MediaStream` for sending and
     * receiving media
     */
    fun setConnector(connector: StreamConnector)

    /**
     * Sets the value of a specific opaque property of this `MediaStream`.
     *
     * @param propertyName the name of the opaque property of this `MediaStream` the value of which is to
     * be set to the specified `value`
     * @param value the value of the opaque property of this `MediaStream` specified by
     * `propertyName` to be set
     */
    fun setProperty(propertyName: String, value: Any?)

    /**
     * Sets the `RTPTranslator` which is to forward RTP and RTCP traffic between this and
     * other `MediaStream`s.
     *
     * @param rtpTranslator the `RTPTranslator` which is to forward RTP and RTCP traffic between this and
     * other `MediaStream`s
     */
    fun setRTPTranslator(rtpTranslator: RTPTranslator?)

    /**
     * Gets the [RTPTranslator] which forwards RTP and RTCP traffic between this and other
     * `MediaStream`s.
     *
     * @return the [RTPTranslator] which forwards RTP and RTCP traffic between this and other
     * `MediaStream`s or `null`
     */
    fun getRTPTranslator(): RTPTranslator?

    /**
     * Sets the `SSRCFactory` which is to generate new synchronization source (SSRC) identifiers.
     *
     * @param ssrcFactory the `SSRCFactory` which is to generate new synchronization source (SSRC)
     * identifiers or `null` if this `MediaStream` is to employ internal logic
     * to generate new synchronization source (SSRC) identifiers
     */
    fun setSSRCFactory(ssrcFactory: SSRCFactory)

    /**
     * Starts capturing media from this stream's `MediaDevice` and then streaming it through
     * the local `StreamConnector` toward the stream's target address and port. The method
     * also puts the `MediaStream` in a listening state that would make it play all media
     * received from the `StreamConnector` on the stream's `MediaDevice`.
     */
    fun start()

    /**
     * Stops all streaming and capturing in this `MediaStream` and closes and releases all
     * open/allocated devices/resources. This method has no effect on an already closed stream and
     * is simply ignored.
     */
    fun stop()

    /**
     * Sets the external (application-provided) `TransformEngine` of this `MediaStream`.
     *
     * @param transformEngine the `TransformerEngine` to use.
     */
    fun setExternalTransformer(transformEngine: TransformEngine)

    /**
     * Sends a given RTP or RTCP packet to the remote peer/side.
     *
     * @param pkt the packet to send.
     * @param data `true` to send an RTP packet or `false` to send an RTCP packet.
     * @param after the `TransformEngine` in the `TransformEngine` chain of this
     * `MediaStream` after which the injection is to begin. If the specified
     * `after` is not in the `TransformEngine` chain of this `MediaStream`,
     * `pkt` will be injected at the beginning of the `TransformEngine` chain of
     * this `MediaStream`. Generally, the value of `after` should be `null`
     * unless the injection is being performed by a `TransformEngine` itself (while executing
     * `transform` or `reverseTransform` of a `PacketTransformer` of its own even).
     * @throws TransmissionFailedException if the transmission failed.
     */
    @Throws(TransmissionFailedException::class)
    fun injectPacket(pkt: RawPacket?, data: Boolean, after: TransformEngine?)

    /**
     * Utility method that determines whether or not a packet is a key frame.
     *
     * @param buf the buffer that holds the RTP payload.
     * @param off the offset in the buff where the RTP payload is found.
     * @param len then length of the RTP payload in the buffer.
     * @return true if the packet is a key frame, false otherwise.
     */
    fun isKeyFrame(buf: ByteArray, off: Int, len: Int): Boolean

    /**
     * Utility method that determines whether or not a packet is a key frame.
     *
     * @param pkt the packet.
     */
    fun isKeyFrame(pkt: RawPacket): Boolean

    /**
     * Gets the primary [REDBlock] that contains the payload of the RTP
     * packet passed in as a parameter.
     *
     * @param baf the [ByteArrayBuffer] that holds the RTP payload.
     * @return the primary [REDBlock] that contains the payload of the RTP
     * packet passed in as a parameter, or null if the buffer is invalid.
     * @Deprecated use getPrimaryREDBlock(RawPacket)
     */
    @Deprecated("")
    fun getPrimaryREDBlock(baf: ByteArrayBuffer): REDBlock?

    /**
     * Gets the primary [REDBlock] that contains the payload of the RTP
     * packet passed in as a parameter.
     *
     * @param pkt the [RawPacket] that holds the RTP payload.
     * @return the primary [REDBlock] that contains the payload of the RTP
     * packet passed in as a parameter, or null if the buffer is invalid.
     */
    fun getPrimaryREDBlock(pkt: RawPacket): REDBlock?

    /**
     * @return the [RetransmissionRequester] for this media stream.
     */
    val retransmissionRequester: RetransmissionRequester?

    /**
     * Gets the [TransformEngineChain] of this [MediaStream].
     */
    val transformEngineChain: TransformEngineChain?

    /**
     * Gets the [MediaStreamTrackReceiver] of this [MediaStream].
     *
     * @return the [MediaStreamTrackReceiver] of this [MediaStream], or null.
     */
    val mediaStreamTrackReceiver: MediaStreamTrackReceiver?

    /**
     * Sets the [TransportCCEngine] of this media stream. Note that for
     * this to take effect it needs to be called early, before the transform
     * chain is initialized (i.e. before a connector is set).
     *
     * @param engine the engine to set.
     */
    fun setTransportCCEngine(engine: TransportCCEngine?)

    companion object {
        /**
         * The name of the property which indicates whether the local SSRC is currently available.
         */
        const val PNAME_LOCAL_SSRC = "localSSRCAvailable"

        /**
         * The name of the property which indicates whether the remote SSRC is currently available.
         */
        const val PNAME_REMOTE_SSRC = "remoteSSRCAvailable"
    }
}