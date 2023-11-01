/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.impl.neomedia.codec.REDBlock
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver
import org.atalk.impl.neomedia.rtp.TransportCCEngine
import org.atalk.impl.neomedia.transform.TransformEngineChain
import org.atalk.service.neomedia.format.MediaFormat
import org.atalk.util.ByteArrayBuffer
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.*

/**
 * Abstract base implementation of `MediaStream` to ease the implementation of the interface.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractMediaStream : MediaStream {
    /**
     * Returns the name of this stream or `null` if no name has been set. A stream name is
     * used by some protocols, for diagnostic purposes mostly. In XMPP for example this is the name
     * of the content element that describes a stream.
     *
     * @return the name of this stream or `null` if no name has been set.
     */
    /**
     * Sets the name of this stream. Stream names are used by some protocols, for diagnostic purposes mostly.
     * In XMPP for example this is the name of the content element that describes a stream.
     *
     * @param name the name of this stream or `null` if no name has been set.
     */
    /**
     * The name of this stream, that some protocols may use for diagnostic purposes.
     */
    override var name: String? = null

    /**
     * The opaque properties of this `MediaStream`.
     */
    private val properties = Collections.synchronizedMap(HashMap<String, Any>())

    /**
     * The delegate of this instance which implements support for property change notifications for
     * its [.addPropertyChangeListener] and
     * [.removePropertyChangeListener].
     */
    private val propertyChangeSupport = PropertyChangeSupport(this)

    /**
     * The `RTPTranslator`, if any, which forwards RTP and RTCP traffic between this and
     * other `MediaStream`s.
     */
    var rtpTranslator: RTPTranslator? = null

    /**
     * Adds a `PropertyChangeListener` to this stream which is to be notified upon property
     * changes such as a SSRC ID which becomes known.
     *
     * @param listener the `PropertyChangeListener` to register for `PropertyChangeEvent`s
     * @see MediaStream.addPropertyChangeListener
     */
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    /**
     * Asserts that the state of this instance will remain consistent if a specific
     * `MediaDirection` (i.e. `direction`) and a `MediaDevice` with a specific
     * `MediaDirection` (i.e. `deviceDirection`) are both set on this instance.
     *
     * @param direction the `MediaDirection` to validate against the specified `deviceDirection`
     * @param deviceDirection the `MediaDirection` of a `MediaDevice` to validate against the
     * specified `direction`
     * @param illegalArgumentExceptionMessage the message of the `IllegalArgumentException` to be thrown if the state of this
     * instance would've been compromised if `direction` and the `MediaDevice`
     * associated with `deviceDirection` were both set on this instance
     * @throws IllegalArgumentException if the state of this instance would've been compromised were both `direction`
     * and the `MediaDevice` associated with `deviceDirection` set on this instance
     */
    @Throws(IllegalArgumentException::class)
    protected fun assertDirection(direction: MediaDirection?, deviceDirection: MediaDirection,
                                  illegalArgumentExceptionMessage: String?) {
        require(!(direction != null && direction.and(deviceDirection) != direction)) { illegalArgumentExceptionMessage as Any }
    }

    /**
     * Fires a new `PropertyChangeEvent` to the `PropertyChangeListener`s registered
     * with this instance in order to notify about a change in the value of a specific property
     * which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this instance which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected fun firePropertyChange(property: String?, oldValue: Any?, newValue: Any?) {
        propertyChangeSupport.firePropertyChange(property, oldValue, newValue)
    }

    /**
     * {@inheritDoc}
     */
    override fun getProperty(propertyName: String): Any? {
        return properties[propertyName]
    }

    /**
     * Handles attributes contained in `MediaFormat`.
     *
     * @param format the `MediaFormat` to handle the attributes of
     * @param attrs the attributes `Map` to handle
     */
    protected open fun handleAttributes(format: MediaFormat, attrs: Map<String, String>?) {}

    /**
     * Sends a given RTP or RTCP packet to the remote peer/side.
     *
     * @param pkt the packet to send.
     * @param data `true` to send an RTP packet or `false` to send an RTCP packet.
     * @throws TransmissionFailedException if the transmission failed.
     */
    @Throws(TransmissionFailedException::class)
    fun injectPacket(pkt: RawPacket, data: Boolean) {
        injectPacket(pkt, data,  /* after */null)
    }

    /**
     * Removes the specified `PropertyChangeListener` from this stream so that it won't
     * receive further property change events.
     *
     * @param listener the `PropertyChangeListener` to remove
     * @see MediaStream.removePropertyChangeListener
     */
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    /**
     * {@inheritDoc}
     */
    override fun setProperty(propertyName: String, value: Any?) {
        if (value == null) properties.remove(propertyName) else properties[propertyName] = value
    }

    /**
     * {@inheritDoc}
     */
    override fun setRTPTranslator(rtpTranslator: RTPTranslator?) {
        if (this.rtpTranslator != rtpTranslator) {
            this.rtpTranslator = rtpTranslator
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun getRTPTranslator(): RTPTranslator? {
        return rtpTranslator
    }

    /**
     * {@inheritDoc}
     */
    override val transformEngineChain: TransformEngineChain?
        get() = null

    /**
     * {@inheritDoc}
     */
    override fun getDynamicRTPPayloadType(codec: String?): Byte {
        return -1
    }

    /**
     * {@inheritDoc}
     */
    override val mediaStreamTrackReceiver: MediaStreamTrackReceiver?
        get() = null

    /**
     * {@inheritDoc}
     */
    override fun getFormat(payloadType: Byte): MediaFormat? {
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun setTransportCCEngine(engine: TransportCCEngine?) {}

    /**
     * {@inheritDoc}
     */
    override fun getPrimaryREDBlock(baf: ByteArrayBuffer): REDBlock? {
        return null
    }

    /**
     * {@inheritDoc}
     */
    override fun getPrimaryREDBlock(pkt: RawPacket): REDBlock? {
        return null
    }
}