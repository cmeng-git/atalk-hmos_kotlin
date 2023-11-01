/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia

import org.atalk.service.neomedia.MediaDirection
import org.atalk.service.neomedia.StreamConnector
import timber.log.Timber
import java.io.IOException
import javax.media.rtp.RTPConnector
import javax.media.rtp.SessionAddress

/**
 * Provides a base/default implementation of `RTPConnector` which has factory methods for its
 * control and data input and output streams and has an associated `StreamConnector`.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
abstract class AbstractRTPConnector(connector: StreamConnector?) : RTPConnector {
    /**
     * The pair of datagram sockets for RTP and RTCP traffic that this instance uses in the form of
     * a `StreamConnector`.
     *
     * Gets the `StreamConnector` which represents the pair of datagram sockets for RTP and
     * RTCP traffic used by this instance.

     */
    val connector: StreamConnector

    /**
     * RTCP packet input stream used by `RTPManager`.
     */
    private var controlInputStream: RTPConnectorInputStream<*>? = null

    /**
     * RTCP packet output stream used by `RTPManager`.
     */
    private var controlOutputStream: RTPConnectorOutputStream? = null

    /**
     * RTP packet input stream used by `RTPManager`.
     */
    private var dataInputStream: RTPConnectorInputStream<*>? = null

    /**
     * RTP packet output stream used by `RTPManager`.
     */
    private var dataOutputStream: RTPConnectorOutputStream? = null

    /**
     * Initializes a new `AbstractRTPConnector` which is to use a given pair of datagram
     * sockets for RTP and RTCP traffic specified in the form of a `StreamConnector`.
     *
     * connector the pair of datagram sockets for RTP and RTCP traffic the new instance is to use
     */
    init {
        if (connector == null) throw NullPointerException("connector")
        this.connector = connector
    }

    /**
     * Add a stream target. A stream target is the destination address which this RTP session will
     * send its data to. For a single session, we can add multiple SessionAddresses, and for each
     * address, one copy of data will be sent to.
     *
     * @param target Destination target address
     * @throws IOException if there was a socket-related error while adding the specified target
     */
    @Throws(IOException::class)
    fun addTarget(target: SessionAddress) {
        val controlAddress = target.controlAddress
        if (controlAddress != null) {
            getControlOutputStream()!!.addTarget(controlAddress, target.controlPort)
        }
        getDataOutputStream().addTarget(target.dataAddress, target.dataPort)
    }

    /**
     * Closes all sockets, stream, and the `StreamConnector` that this `RTPConnector`
     * is using.
     */
    override fun close() {
        if (dataOutputStream != null) {
            dataOutputStream!!.close()
            dataOutputStream = null
        }
        if (controlOutputStream != null) {
            controlOutputStream!!.close()
            controlOutputStream = null
        }
        if (dataInputStream != null) {
            dataInputStream!!.close()
            dataInputStream = null
        }
        if (controlInputStream != null) {
            controlInputStream!!.close()
            controlInputStream = null
        }
        connector.close()
    }

    /**
     * Creates the RTCP packet input stream to be used by `RTPManager`.
     *
     * @return a new RTCP packet input stream to be used by `RTPManager`
     * @throws IOException if an error occurs during the creation of the RTCP packet input stream
     */
    @Throws(IOException::class)
    protected abstract fun createControlInputStream(): RTPConnectorInputStream<*>?

    /**
     * Creates the RTCP packet output stream to be used by `RTPManager`.
     *
     * @return a new RTCP packet output stream to be used by `RTPManager`
     * @throws IOException if an error occurs during the creation of the RTCP packet output stream
     */
    @Throws(IOException::class)
    protected abstract fun createControlOutputStream(): RTPConnectorOutputStream?

    /**
     * Creates the RTP packet input stream to be used by `RTPManager`.
     *
     * @return a new RTP packet input stream to be used by `RTPManager`
     * @throws IOException if an error occurs during the creation of the RTP packet input stream
     */
    @Throws(IOException::class)
    protected abstract fun createDataInputStream(): RTPConnectorInputStream<*>?

    /**
     * Creates the RTP packet output stream to be used by `RTPManager`.
     *
     * @return a new RTP packet output stream to be used by `RTPManager`
     * @throws IOException if an error occurs during the creation of the RTP packet output stream
     */
    @Throws(IOException::class)
    protected abstract fun createDataOutputStream(): RTPConnectorOutputStream?

    /**
     * Returns the input stream that is handling incoming RTCP packets.
     *
     * @return the input stream that is handling incoming RTCP packets.
     * @throws IOException if an error occurs during the creation of the RTCP packet input stream
     */
    @Throws(IOException::class)
    override fun getControlInputStream(): RTPConnectorInputStream<*> {
        return getControlInputStream(true)!!
    }

    /**
     * Gets the `PushSourceStream` which gives access to the RTCP data received from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create `true` to create the `PushSourceStream` which gives access to the RTCP
     * data received from the remote targets if it does not exist yet; otherwise, `false`
     * @return the `PushBufferStream` which gives access to the RTCP data received from the
     * remote targets; `null` if it does not exist yet and `create` is `false`
     * @throws IOException if creating the `PushSourceStream` fails
     */
    @Throws(IOException::class)
    fun getControlInputStream(create: Boolean): RTPConnectorInputStream<*>? {
        if (controlInputStream == null && create) controlInputStream = createControlInputStream()
        return controlInputStream
    }

    /**
     * Returns the input stream that is handling outgoing RTCP packets.
     *
     * @return the input stream that is handling outgoing RTCP packets.
     * @throws IOException if an error occurs during the creation of the RTCP packet output stream
     */
    @Throws(IOException::class)
    override fun getControlOutputStream(): RTPConnectorOutputStream? {
        return getControlOutputStream(true)
    }

    /**
     * Gets the `OutputDataStream` which is used to write RTCP data to be sent to from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create `true` to create the `OutputDataStream` which is to be used to write
     * RTCP data to be sent to the remote targets if it does not exist yet; otherwise, `false`
     * @return the `OutputDataStream` which is used to write RTCP data to be sent to the
     * remote targets; `null` if it does not exist yet and `create` is `false`
     * @throws IOException if creating the `OutputDataStream` fails
     */
    @Throws(IOException::class)
    fun getControlOutputStream(create: Boolean): RTPConnectorOutputStream? {
        if (controlOutputStream == null && create) controlOutputStream = createControlOutputStream()
        return controlOutputStream
    }

    /**
     * Returns the input stream that is handling incoming RTP packets.
     *
     * @return the input stream that is handling incoming RTP packets.
     * @throws IOException if an error occurs during the creation of the RTP packet input stream
     */
    @Throws(IOException::class)
    override fun getDataInputStream(): RTPConnectorInputStream<*> {
        return getDataInputStream(true)!!
    }

    /**
     * Gets the `PushSourceStream` which gives access to the RTP data received from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create `true` to create the `PushSourceStream` which gives access to the RTP
     * data received from the remote targets if it does not exist yet; otherwise, `false`
     * @return the `PushBufferStream` which gives access to the RTP data received from the
     * remote targets; `null` if it does not exist yet and `create` is `false`
     * @throws IOException if creating the `PushSourceStream` fails
     */
    @Throws(IOException::class)
    fun getDataInputStream(create: Boolean): RTPConnectorInputStream<*>? {
        if (dataInputStream == null && create) dataInputStream = createDataInputStream()
        return dataInputStream
    }

    /**
     * Returns the input stream that is handling outgoing RTP packets.
     *
     * @return the input stream that is handling outgoing RTP packets.
     * @throws IOException if an error occurs during the creation of the RTP
     */
    @Throws(IOException::class)
    override fun getDataOutputStream(): RTPConnectorOutputStream {
        return getDataOutputStream(true)!!
    }

    /**
     * Gets the `OutputDataStream` which is used to write RTP data to be sent to from the
     * remote targets and optionally creates it if it does not exist yet.
     *
     * @param create `true` to create the `OutputDataStream` which is to be used to write RTP
     * data to be sent to the remote targets if it does not exist yet; otherwise, `false`
     * @return the `OutputDataStream` which is used to write RTP data to be sent to the
     * remote targets; `null` if it does not exist yet and `create` is `false`
     * @throws IOException if creating the `OutputDataStream` fails
     */
    @Throws(IOException::class)
    fun getDataOutputStream(create: Boolean): RTPConnectorOutputStream? {
        if (dataOutputStream == null && create) dataOutputStream = createDataOutputStream()
        return dataOutputStream
    }

    /**
     * Provides a dummy implementation to [RTPConnector.getReceiveBufferSize] that always returns `-1`.
     */
    override fun getReceiveBufferSize(): Int {
        // Not applicable
        return -1
    }

    /**
     * Provides a dummy implementation to [RTPConnector.getRTCPBandwidthFraction] that
     * always returns `-1`.
     */
    override fun getRTCPBandwidthFraction(): Double {
        // Not applicable
        return -1.0
    }

    /**
     * Provides a dummy implementation to [RTPConnector.getRTCPSenderBandwidthFraction] that
     * always returns `-1`.
     */
    override fun getRTCPSenderBandwidthFraction(): Double {
        // Not applicable
        return -1.0
    }

    /**
     * Provides a dummy implementation to [RTPConnector.getSendBufferSize] that always returns `-1`.
     */
    override fun getSendBufferSize(): Int {
        // Not applicable
        return -1
    }

    /**
     * Provides a dummy implementation to [RTPConnector.setReceiveBufferSize].
     *
     * @param size ignored.
     */
    @Throws(IOException::class)
    override fun setReceiveBufferSize(size: Int) {
        // Nothing should be done here :-)
    }

    /**
     * Provides a dummy implementation to [RTPConnector.setSendBufferSize].
     *
     * @param size ignored.
     */
    @Throws(IOException::class)
    override fun setSendBufferSize(size: Int) {
        // Nothing should be done here :-)
    }

    /**
     * Removes a target from our session. If a target is removed, there will be no data sent to that address.
     *
     * @param target Destination target to be removed
     */
    fun removeTarget(target: SessionAddress) {
        if (controlOutputStream != null) controlOutputStream!!.removeTarget(target.controlAddress, target.controlPort)
        if (dataOutputStream != null) dataOutputStream!!.removeTarget(target.dataAddress, target.dataPort)
    }

    /**
     * Remove all stream targets. After this operation is done. There will be no targets receiving
     * data, so no data will be sent.
     */
    fun removeTargets() {
        if (controlOutputStream != null) controlOutputStream!!.removeTargets()
        if (dataOutputStream != null) dataOutputStream!!.removeTargets()
    }

    /**
     * Configures this `AbstractRTPConnector` to allow RTP in the specified direction. That
     * is, enables/disables the input and output data streams according to `direction`.
     *
     * Note that the control (RTCP) streams are not affected (they are always kept enabled).
     *
     * @param direction Specifies how to configure the data streams of this `AbstractRTPConnector`. The
     * input stream will be enabled or disabled depending on whether `direction`
     * allows receiving. The output stream will be enabled or disabled depending on whether
     * `direction` allows sending.
     */
    fun setDirection(direction: MediaDirection) {
        val receive = direction.allowsReceiving()
        val send = direction.allowsSending()
        Timber.d("setDirection %s", direction)
        try {
            // Forcing the stream to be created causes problems.
            val dataInputStream = getDataInputStream(false)
            dataInputStream?.setEnabled(receive)
        } catch (ioe: IOException) {
            Timber.e("Failed to %s data input stream.", if (receive) "enable" else "disable")
        }
        try {
            // Forcing the stream to be created causes problems.
            val dataOutputStream = getDataOutputStream(false)
            dataOutputStream?.setEnabled(send)
        } catch (ioe: IOException) {
            Timber.e("Failed to %s data output stream.", if (send) "enable" else "disable")
        }
    }
}