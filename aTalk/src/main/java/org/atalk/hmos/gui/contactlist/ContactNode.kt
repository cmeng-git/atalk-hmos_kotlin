/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.atalk.hmos.gui.contactlist

import net.java.sip.communicator.service.gui.ContactListNode
import net.java.sip.communicator.service.gui.UIContact

/**
 * The `ContactNode` is a `ContactListNode` corresponding to a given `UIContact`.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
// public class ContactNode extends DefaultMutableTreeNode implements ContactListNode
class ContactNode(contact: UIContactImpl) : ContactListNode {
    /**
     * The `UIContact` corresponding to this contact node.
     */
    private val contact: UIContact
    /**
     * Returns `true` if this contact node has unread received messages waiting, otherwise returns `false`
     * .
     *
     * @return `true` if this contact node has unread received messages waiting, otherwise returns `false`
     */
    /**
     * Sets this contact node as active, which indicates it has unread received messages waiting.
     *
     * @param isActive
     * indicates if this contact is active
     */
    /**
     * Indicates if this node is currently active. Has unread messages waiting.
     */
    var isActive = false

    /**
     * Creates a `ContactNode` by specifying the corresponding `contact`.
     *
     * @param contact
     * the `UIContactImpl` corresponding to this node
     */
    init {
        // super(contact);
        this.contact = contact
    }
    /**
     * Returns the corresponding `UIContactImpl`.
     *
     * @return the corresponding `UIContactImpl`
     */
    // public UIContactImpl getContactDescriptor()
    // {
    // return (UIContactImpl) getUserObject();
    // }
    /**
     * Returns the index of this contact node in its parent group.
     *
     * @return the index of this contact node in its parent group
     */
    override val sourceIndex: Int
        get() = contact.sourceIndex
}