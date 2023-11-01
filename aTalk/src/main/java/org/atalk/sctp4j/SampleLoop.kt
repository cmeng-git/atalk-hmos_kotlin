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
 * Sample that uses two `SctpSocket`s with [DirectLink].
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object SampleLoop {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        Sctp.init()
        val server = Sctp.createSocket(5001)
        val client = Sctp.createSocket(5002)
        val link = DirectLink(server, client)
        server!!.setLink(link)
        client!!.setLink(link)

        // Make server passive
        server.listen()

        // Client thread
        Thread {
            try {
                client.connect(server.port)
                Timber.i("Client: connect")
                try {
                    Thread.sleep(1000)
                } catch (ignore: Exception) {
                }
                val sent = client.send(ByteArray(200), false, 0, 0)
                Timber.i("Client sent: %s", sent)
            } catch (e: IOException) {
                Timber.e("%s", e.message)
            }
        }.start()

        server.setDataCallback(object : SctpDataCallback {
            override fun onSctpPacket(data: ByteArray, sid: Int, ssn: Int, tsn: Int, ppid: Long, context: Int, flags: Int) {
                Timber.i("Server got some data: %s stream: %s payload protocol id: %s", data.size, sid, ppid)
            }
        }
        )

        Thread.sleep((5 * 1000).toLong())
        server.close()
        client.close()
        Sctp.finish()
    }
}