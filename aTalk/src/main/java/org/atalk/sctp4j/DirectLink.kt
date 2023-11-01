/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.sctp4j

import timber.log.Timber
import java.io.IOException

/**
 * A direct connection that passes packets between two `SctpSocket` instances.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class DirectLink(
        /**
         * Instance "a" of this direct connection.
         */
        private val a: SctpSocket?,
        /**
         * Instance "b" of this direct connection.
         */
        private val b: SctpSocket?) : NetworkLink {
    /**
     * {@inheritDoc}
     */
    @Throws(IOException::class)
    override fun onConnOut(s: SctpSocket, packet: ByteArray) {
        val dest = if (s === a) b else a
        Thread {
            try {
                dest!!.onConnIn(packet, 0, packet.size)
            } catch (e: IOException) {
                Timber.e(e, "%s", e.message)
            }
        }.start()
    }
}