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
package net.java.sip.communicator.service.contactlist.event

import java.util.*

/**
 * A MetaContactListListener can be registered with a MetaContactListService so that it will
 * receive any changes that have occurred in the contact list layout.
 *
 * @author Yana Stamcheva
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
interface MetaContactListListener : EventListener {
    /**
     * Indicates that a MetaContact has been successfully added to the MetaContact list.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactAdded(evt: MetaContactEvent)

    /**
     * Indicates that a MetaContact has been renamed.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactRenamed(evt: MetaContactRenamedEvent)

    /**
     * Indicates that a MetaContact has been modified.
     *
     * @param evt the MetaContactModifiedEvent containing the corresponding contact
     */
    fun metaContactModified(evt: MetaContactModifiedEvent)

    /**
     * Indicates that a MetaContact has been moved inside the MetaContact list.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactMoved(evt: MetaContactMovedEvent)

    /**
     * Indicates that a MetaContact has been removed from the MetaContact list.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactRemoved(evt: MetaContactEvent)

    /**
     * Indicates that a protocol specific `Contact` instance has been added to the list of
     * protocol specific buddies in this `MetaContact`
     *
     * @param evt a reference to the corresponding `ProtoContactEvent`
     */
    fun protoContactAdded(evt: ProtoContactEvent)

    /**
     * Indicates that one of the protocol specific `Contact` instances encapsulated by
     * this `MetaContact` has been modified in some way. The event added to the list of
     * protocol specific buddies in this `MetaContact`
     *
     * @param evt a reference to the corresponding `ProtoContactEvent`
     */
    fun protoContactModified(evt: ProtoContactEvent)

    /**
     * Indicates that a protocol specific `Contact` instance has been moved from within
     * one `MetaContact` to another.
     *
     * @param evt a reference to the `ProtoContactMovedEvent` instance.
     */
    fun protoContactMoved(evt: ProtoContactEvent)

    /**
     * Indicates that a protocol specific `Contact` instance has been removed from the
     * list of protocol specific buddies in this `MetaContact`
     *
     * @param evt a reference to the corresponding `ProtoContactEvent`
     */
    fun protoContactRemoved(evt: ProtoContactEvent)

    /**
     * Indicates that a protocol specific `Contact` instance has been renamed from within
     * list of protocol specific buddies in this `MetaContact`
     *
     * @param evt a reference to the corresponding `ProtoContactEvent`
     */
    fun protoContactRenamed(evt: ProtoContactEvent)
    //-------------------- events on groups. ----------------------------------
    /**
     * Indicates that a MetaContactGroup has been successfully added to the MetaContact list.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactGroupAdded(evt: MetaContactGroupEvent)

    /**
     * Indicates that a MetaContactGroup has been modified (e.g. a proto contact group was removed).
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactGroupModified(evt: MetaContactGroupEvent)

    /**
     * Indicates that a MetaContactGroup has been removed from the MetaContact list.
     *
     * @param evt the MetaContactListEvent containing the corresponding contact
     */
    fun metaContactGroupRemoved(evt: MetaContactGroupEvent)

    /**
     * Indicates that the order under which the child contacts were ordered
     * inside the source group has changed.
     *
     * @param evt the `MetaContactGroupEvent` containing details of this event.
     */
    fun childContactsReordered(evt: MetaContactGroupEvent)

    /**
     * Indicates that a new avatar is available for a `MetaContact`.
     *
     * @param evt the `MetaContactAvatarUpdateEvent` containing details of this event
     */
    fun metaContactAvatarUpdated(evt: MetaContactAvatarUpdateEvent)
}