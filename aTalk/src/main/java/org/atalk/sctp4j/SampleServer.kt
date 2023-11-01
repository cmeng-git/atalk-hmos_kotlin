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
 * Sample SCTP server that uses UDP socket for transfers.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object SampleServer {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val localAddr = "127.0.0.1"
        val localPort = 48001
        val localSctpPort = 5001
        val remoteAddr = "127.0.0.1"
        val remotePort = 48002
        Sctp.init()
        val sock1 = Sctp.createSocket(localSctpPort)
        val link = UdpLink(sock1, localAddr, localPort, remoteAddr, remotePort)
        sock1!!.setLink(link)
        sock1.listen()
        while (!sock1.accept()) {
            Thread.sleep(100)
        }

        sock1.setDataCallback(object : SctpDataCallback {
            override fun onSctpPacket(
                    data: ByteArray, sid: Int, ssn: Int, tsn: Int,
                    ppid: Long,
                    context: Int, flags: Int,
            ) {
                Timber.i("Server got some data: %d; stream: %s; payload protocol id: %s",
                        data.size, sid, ppid)
            }
        })

        Thread.sleep(40000)
        sock1.close()
        Sctp.finish()
    }
}