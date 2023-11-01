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

/**
 * Sample SCTP client that uses UDP socket for transfers.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object SampleClient {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val localAddr = "127.0.0.1"
        val localPort = 48002
        val localSctpPort = 5002
        val remoteAddr = "127.0.0.1"
        val remotePort = 48001
        val remoteSctpPort = 5001
        Sctp.init()
        val client = Sctp.createSocket(localSctpPort)
        val link = UdpLink(client, localAddr, localPort, remoteAddr, remotePort)
        client!!.setLink(link)
        client.connect(remoteSctpPort)
        try {
            Thread.sleep(1000)
        } catch (e: Exception) {
        }
        val sent = client.send(ByteArray(200), false, 0, 0)
        Timber.i("Client sent: %s", sent)
        Thread.sleep(4000)
        client.close()
        Sctp.finish()
    }
}