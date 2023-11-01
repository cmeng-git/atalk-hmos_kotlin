/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.impl.provdisc.dhcp

import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.*

/**
 * DHCP transaction class.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class DHCPTransaction constructor(
        /**
         * The socket that will be used to retransmit DHCP packet.
         */
        private val sock: DatagramSocket,
        /**
         * The DHCP packet content.
         */
        private val message: DatagramPacket) {
    /**
     * Number of retransmission before giving up.
     */
    private var maxRetransmit = 2

    /**
     * Current number of retransmission.
     */
    private var nbRetransmit = 0

    /**
     * Fix interval for retransmission. This final interval will be obtained
     * by adding a random number between [-1, 1].
     */
    private var interval = 2

    /**
     * The Timer that will trigger retransmission.
     */
    private var timer: Timer? = null

    /**
     * Constructor.
     *
     * @param sock UDP socket
     * @param message DHCP packet content
     */
    init {
        timer = Timer()
    }

    /**
     * Schedule a timer for retransmission.
     *
     * @throws Exception if message cannot be sent on the socket
     */
    @Throws(Exception::class)
    fun schedule() {
        sock.send(message)

        /* choose a random between [-1, 1] */
        val rand = Random().nextInt(2) - 1
        timer!!.schedule(RetransmissionHandler(), ((interval + rand) * 1000).toLong())
    }

    /**
     * Cancel the transaction (i.e stop retransmission).
     */
    fun cancel() {
        timer!!.cancel()
    }

    /**
     * Set the maximum retransmission for a transaction.
     *
     * @param maxRetransmit maximum retransmission for this transaction
     */
    fun setMaxRetransmit(maxRetransmit: Int) {
        this.maxRetransmit = maxRetransmit
    }

    /**
     * Set the fixed interval for retransmission.
     *
     * @param interval interval to set
     */
    fun setInterval(interval: Int) {
        this.interval = interval
    }

    /**
     * A `TimerTask` that will handle retransmission of DHCP INFORM.
     *
     * @author Sebastien Vincent
     */
    private inner class RetransmissionHandler constructor() : TimerTask() {
        /**
         * Thread entry point.
         */
        public override fun run() {
            val rand = Random().nextInt(2) - 1
            try {
                sock.send(message)
            } catch (e: Exception) {
                Timber.w(e, "Failed to send DHCP packet")
            }
            nbRetransmit++
            if (nbRetransmit < maxRetransmit) {
                timer!!.schedule(RetransmissionHandler(),
                        (
                                (interval + rand) * 1000).toLong())
            }
        }
    }
}