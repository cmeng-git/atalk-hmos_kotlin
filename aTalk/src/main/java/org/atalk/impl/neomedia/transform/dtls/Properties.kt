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
package org.atalk.impl.neomedia.transform.dtls

import org.atalk.service.neomedia.DtlsControl.Setup
import org.atalk.util.event.PropertyChangeNotifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Gathers properties of [DtlsControlImpl] which it shares with
 * [DtlsTransformEngine] and [DtlsPacketTransformer] i.e. assigning
 * a value to a `DtlsControlImpl` property triggers assignments to the
 * respective properties of `DtlsTransformEngine` and `DtlsPacketTransfomer`.
 *
 * @author Lyubomir Marinov
 */
/**
 * Initializes a new `Properties` instance.
 *
 * @param isSrtpDisabled `true` to specify pure DTLS without SRTP extensions or `false` to specify DTLS/SRTP.
 */
class Properties
(
        /**
         * Indicates whether this `DtlsControl` will work in DTLS/SRTP or pure DTLS mode.
         */
        val isSrtpDisabled: Boolean) : PropertyChangeNotifier() {

    /**
     * The actual `Map` of property names to property values represented
     * by this instance. Stores only assignable properties i.e. `final`s
     * are explicitly defined (e.g. [.srtpDisabled]).
     */
    private val properties: MutableMap<String, Any> = ConcurrentHashMap()

    /**
     * Indicates if SRTP extensions are disabled which means we're working in pure DTLS mode.
     *
     * @return `true` if SRTP extensions must be disabled.
     */
    /**
     * Gets the value of the property with a specific name.
     *
     * @param name the name of the property to get the value of
     * @return the value of the property with the specified `name`
     */
    operator fun get(name: String): Any? {
        return properties[name]
    }

    /**
     * Gets the value of the `setup` SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;
     * which determines whether this instance acts as a DTLS client or a DTLS server.
     *
     * @return the value of the `setup` SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;
     * which determines whether this instance acts as a DTLS client or a DTLS server.
     */
    val setup: Setup?
        get() = get(SETUP_PNAME) as Setup?

    /**
     * Sets the value of the property with a specific name.
     *
     * @param name the name of the property to set the value of
     * @param value the value to set on the property with the specified `name`
     */
    fun put(name: String, value: Any?) {
        // XXX ConcurrentHashMap doesn't allow null values and we want to allow
        // them. (It doesn't allow null keys either and we don't want to allow them.)
        val oldValue = if (value == null) properties.remove(name)
        else properties.put(name, value)

        if (oldValue != value)
            firePropertyChange(name, oldValue, value)
    }

    companion object {
        /**
         * The `RTPConnector` which uses the `TransformEngine` of this `SrtpControl`.
         */
        val CONNECTOR_PNAME = Properties::class.java.name + ".connector"
        val MEDIA_TYPE_PNAME = Properties::class.java.name + ".mediaType"

        /**
         * Whether rtcp-mux is in use.
         */
        val RTCPMUX_PNAME = Properties::class.java.name + ".rtcpmux"

        /**
         * The value of the `setup` SDP attribute defined by RFC 4145
         * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;
         * which determines whether this instance acts as a DTLS client or a DTLS server.
         */
        val SETUP_PNAME = Properties::class.java.name + ".setup"
    }
}