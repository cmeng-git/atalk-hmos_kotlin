/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol

import net.java.sip.communicator.service.protocol.event.BLFStatusListener
import net.java.sip.communicator.util.DataObject

/**
 * Provides operations necessary to monitor line activity and pickup calls if needed.
 * BLF stands for Busy Lamp Field.
 *
 * @author Damian Minkov
 */
interface OperationSetTelephonyBLF : OperationSet {
    /**
     * Adds BLFStatus listener
     *
     * @param listener
     * the listener to add.
     */
    fun addStatusListener(listener: BLFStatusListener?)

    /**
     * Removes BLFStatus listener.
     *
     * @param listener
     * the listener to remove.
     */
    fun removeStatusListener(listener: BLFStatusListener?)

    /**
     * To pickup the call for the monitored line if possible.
     *
     * @param line
     * to try to pick up.
     *
     * @throws OperationFailedException
     * if `line` address is not valid.
     */
    @Throws(OperationFailedException::class)
    fun pickup(line: Line?)

    /**
     * List of currently monitored lines.
     *
     * @return list of currently monitored lines.
     */
    fun getCurrentlyMonitoredLines(): List<Line?>?

    /**
     * The monitored line.
     */
    class Line
    /**
     * Constructs Line.
     *
     * @param address
     * the address of the line.
     * @param name
     * the display name if any
     * @param group
     * the group name if any
     * @param pickup
     * the pickup dial template
     * @param provider
     * the parent provider.
     */
    (
            /**
             * The address of the line.
             */
            private val address: String,
            /**
             * The display name of the line.
             */
            private val name: String,
            /**
             * The group under witch to display the line.
             */
            private val group: String,
            /**
             * Asterisk pickup prefix.
             */
            private val pickupTemplate: String,
            /**
             * The parent provider.
             */
            private val provider: ProtocolProviderService) : DataObject() {
        /**
         * The address of the line.
         *
         * @return address of the line.
         */
        fun getAddress(): String {
            return address
        }

        /**
         * The name of the line.
         *
         * @return the name of the line.
         */
        fun getName(): String {
            return name
        }

        /**
         * The group name.
         *
         * @return the group name.
         */
        fun getGroup(): String {
            return group
        }

        /**
         * The pickup template.
         * @return the pickup template.
         */
        fun getPickupTemplate(): String {
            return pickupTemplate
        }

        /**
         * The provider.
         *
         * @return the provider.
         */
        fun getProvider(): ProtocolProviderService {
            return provider
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val line = o as Line
            return address == line.address
        }

        override fun hashCode(): Int {
            return address.hashCode()
        }
    }
}