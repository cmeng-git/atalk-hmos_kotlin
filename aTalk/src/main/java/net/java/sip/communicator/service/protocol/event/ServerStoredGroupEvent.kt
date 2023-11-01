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
package net.java.sip.communicator.service.protocol.event

import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import java.util.*

/**
 * Events of this class indicate a change in one of the properties of a ServerStoredGroup.
 *
 * @author Emil Ivov
 */
class ServerStoredGroupEvent(sourceGroup: ContactGroup?, eventID: Int, parentGroup: ContactGroup?,
                             sourceProvider: ProtocolProviderService?, opSet: OperationSetPersistentPresence?) : EventObject(sourceGroup) {
    private var eventID = -1
    private var sourceProvider: ProtocolProviderService? = null
    private var parentOperationSet: OperationSetPersistentPresence? = null
    private var parentGroup: ContactGroup? = null

    /**
     * Creates a ServerStoredGroupChangeEvent instance.
     *
     * @param sourceGroup
     * the group that this event is pertaining to.
     * @param eventID
     * an int describing the cause of the event
     * @param parentGroup
     * the group that the source group is a child of.
     * @param sourceProvider
     * a reference to the protocol provider where this is happening
     * @param opSet
     * a reference to the operation set responsible for the event
     */
    init {
        this.eventID = eventID
        this.sourceProvider = sourceProvider
        parentOperationSet = opSet
        this.parentGroup = parentGroup
    }

    /**
     * Returns a reference to the `ContactGroup` that this event is pertaining to.
     *
     * @return a reference to the ContactGroup that caused the event.
     */
    fun getSourceGroup(): ContactGroup {
        return getSource() as ContactGroup
    }

    /**
     * Returns an int describing the cause of this event.
     *
     * @return an int describing the cause of this event.
     */
    fun getEventID(): Int {
        return eventID
    }

    /**
     * Returns a reference to the provider under which the event is being generated
     *
     * @return a ProtocolProviderService instance indicating the provider responsible for the event.
     */
    fun getSourceProvider(): ProtocolProviderService? {
        return sourceProvider
    }

    /**
     * Returns a reference to the operation set that generated the event
     *
     * @return a reference to an OperationSetPersistentPresence instance, responsible for generating
     * the event.
     */
    fun getSourceOperationSet(): OperationSetPersistentPresence? {
        return parentOperationSet
    }

    /**
     * Returns the group containing the event source group
     *
     * @return a reference to the `ContactGroup` instance that is parent of the
     * `ContactGroup` which is the source of this event.
     */
    fun getParentGroup(): ContactGroup? {
        return parentGroup
    }

    /**
     * Returns a String representation of this event.
     *
     * @return a String containing details describin this event.
     */
    override fun toString(): String {
        val buff = StringBuffer("ServerStoredGroupEvent:[EventID= ")
        buff.append(getEventID())
        buff.append(" SourceGroup=")
        buff.append(getSource())
        buff.append("]")
        return buff.toString()
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Indicates that a contact group has been successfully created.
         */
        const val GROUP_CREATED_EVENT = 1

        /**
         * Indicates that a contact group has been successfully deleted.
         */
        const val GROUP_REMOVED_EVENT = 2

        /**
         * Indicates that a group has been successfully renamed.
         */
        const val GROUP_RENAMED_EVENT = 3

        /**
         * Indicates that a group has just been resolved against the server.
         */
        const val GROUP_RESOLVED_EVENT = 4

        /**
         * Indicates that we have failed to create a group.
         */
        const val GROUP_CREATION_FAILED_EVENT = 5
    }
}