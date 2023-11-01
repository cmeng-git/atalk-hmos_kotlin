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
package net.java.sip.communicator.service.netaddr.event

import java.net.InetAddress
import java.util.*

/**
 * A ChangeEvent is fired on change of the network configuration of the computer.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class ChangeEvent @JvmOverloads constructor(source: Any?,
        type: Int,
        address: InetAddress? = null,
        standby: Boolean = false,
        initial: Boolean = false) : EventObject(source) {
    /**
     * The type of the current event.
     */
    private var type = -1

    /**
     * Whether this event is after computer have been suspended.
     */
    private var standby = false

    /**
     * The address that changed.
     */
    private val address: InetAddress?

    /**
     * Is this event initial one. When starting, no actual
     * change has occurred in the system.
     */
    private val initial: Boolean
    /**
     * Creates event.
     * @param source the source of the event, the interface.
     * @param type the type of the event.
     * @param address the address that changed.
     * @param standby is the event after a suspend of the computer.
     * @param initial is this event initial one.
     */
    /**
     * Creates event.
     * @param source the source of the event, the interface.
     * @param type the type of the event.
     */
    /**
     * Creates event.
     * @param source the source of the event, the interface.
     * @param type the type of the event.
     * @param address the address that changed.
     */
    init {
        this.type = type
        this.address = address
        this.standby = standby
        this.initial = initial
    }

    /**
     * Creates event.
     * @param source the source of the event.
     * @param type the type of the event.
     * @param standby is the event after a suspend of the computer.
     */
    constructor(source: Any?, type: Int, standby: Boolean) : this(source, type, null, standby, false) {}

    /**
     * The type of this event.
     * @return the type
     */
    fun getType(): Int {
        return type
    }

    /**
     * The address that changed.
     * @return the address
     */
    fun getAddress(): InetAddress? {
        return address
    }

    /**
     * Whether this event is after suspend of the computer.
     * @return the standby
     */
    fun isStandby(): Boolean {
        return standby
    }

    /**
     * Overrides toString method.
     * @return string representing the event.
     */
    override fun toString(): String {
        val buff = StringBuilder()
        buff.append("ChangeEvent ")
        when (type) {
            IFACE_DOWN -> buff.append("Interface down")
            IFACE_UP -> buff.append("Interface up")
            ADDRESS_DOWN -> buff.append("Address down")
            ADDRESS_UP -> buff.append("Address up")
            DNS_CHANGE -> buff.append("Dns has changed")
        }
        buff.append(", standby=$standby")
                .append(", source=$source")
                .append(", address=$address")
                .append(", isInitial=$initial")
        return buff.toString()
    }

    /**
     * Is this event initial one. When starting, no actual
     * change has occurred in the system.
     * @return is this event initial one.
     */
    fun isInitial(): Boolean {
        return initial
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Event type for interface going down.
         */
        const val IFACE_DOWN = 0

        /**
         * Event type for interface going up.
         */
        const val IFACE_UP = 1

        /**
         * Event type for address going down.
         */
        const val ADDRESS_DOWN = 2

        /**
         * Event type for interface going down.
         */
        const val ADDRESS_UP = 3

        /**
         * Event type for dns change.
         */
        const val DNS_CHANGE = 4
    }
}