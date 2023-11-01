/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.OperationSetTLS
import java.security.cert.Certificate
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * An implementation of the OperationSetTLS for the Jabber protocol.
 *
 * @author Markus Kilas
 * @author Eng Chong Meng
 */
class OperationSetTLSJabberImpl(private val mPPS: ProtocolProviderServiceJabberImpl) : OperationSetTLS {
    /**
     * @see OperationSetTLS.getCipherSuite
     */
    override fun getCipherSuite(): String? {
        val socket = mPPS.sslSocket
        return socket?.session?.cipherSuite
    }

    /**
     * @see OperationSetTLS.getProtocol
     */
    override fun getProtocol(): String? {
        val socket = mPPS.sslSocket
        return socket?.session?.protocol
    }

    /**
     * @see OperationSetTLS.getServerCertificates
     */
    override fun getServerCertificates(): Array<Certificate?>? {
        var certChain: Array<Certificate?>? = null
        val socket = mPPS.sslSocket
        if (socket != null) {
            try {
                certChain = socket.session.peerCertificates
            } catch (ex: SSLPeerUnverifiedException) {
                ex.printStackTrace()
            }
        }
        return certChain
    }
}