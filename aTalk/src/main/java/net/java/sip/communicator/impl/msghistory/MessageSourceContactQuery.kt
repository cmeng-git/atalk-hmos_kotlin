/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.msghistory

import net.java.sip.communicator.service.contactsource.AsyncContactQuery
import net.java.sip.communicator.service.contactsource.SourceContact
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.PresenceStatus
import java.util.*
import java.util.regex.Pattern

/**
 * The query which creates source contacts and uses the values stored in
 * `MessageSourceService`.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class MessageSourceContactQuery
/**
 * Constructs.
 *
 * @param messageSourceService
 */
internal constructor(messageSourceService: MessageSourceService?) : AsyncContactQuery<MessageSourceService?>(messageSourceService!!,
        Pattern.compile("", Pattern.CASE_INSENSITIVE or Pattern.LITERAL), false) {
    /**
     * Creates `MessageSourceContact` for all currently cached recent messages in the
     * `MessageSourceService`.
     */
    public override fun run() {
        (contactSource as MessageSourceService).updateRecentMessages()
    }

    /**
     * Updates capabilities from `EventObject` for the found `MessageSourceContact`
     * equals to the `Object` supplied. Note that Object may not be
     * `MessageSourceContact`, but  its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * used to search for `MessageSourceContact`
     * @param eventObj
     * the values used for the update
     */
    fun updateCapabilities(srcObj: Any, eventObj: EventObject?) {
        for (msc in queryResults) {
            if (srcObj == msc && msc is MessageSourceContact) {
                msc.initDetails(eventObj)
                break
            }
        }
    }

    /**
     * Updates capabilities from `Contact` for the found `MessageSourceContact`
     * equals to the `Object` supplied. Note that Object may not be
     * `MessageSourceContact`, but its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * used to search for `MessageSourceContact`
     * @param contact
     * the values used for the update
     */
    fun updateCapabilities(srcObj: Any, contact: Contact?) {
        for (msc in queryResults) {
            if (srcObj == msc && msc is MessageSourceContact) {
                msc.initDetails(false, contact)
                break
            }
        }
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this `ContactQuery`
     * that a `SourceContact` has been changed. Note that Object may not be
     * `MessageSourceContact`, but its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * the `Object` representing a recent message which has been changed and
     * corresponding `SourceContact` which the registered
     * `ContactQueryListener`s are to be notified about
     */
    fun updateContact(srcObj: Any, eventObject: EventObject?) {
        for (msc in queryResults) {
            if (srcObj == msc && msc is MessageSourceContact) {
                msc.update(eventObject)
                super.fireContactChanged(msc)
                break
            }
        }
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this `ContactQuery`
     * that a `SourceContact` has been changed. Note that Object may not be
     * `MessageSourceContact`, but its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * the `Object` representing a recent message which has been changed and
     * corresponding `SourceContact` which the registered
     * `ContactQueryListener`s are to be notified about
     */
    fun fireContactChanged(srcObj: Any) {
        for (msc in queryResults) {
            if (srcObj == msc && msc is MessageSourceContact) {
                super.fireContactChanged(msc)
                break
            }
        }
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this `ContactQuery`
     * that a `SourceContact` has been changed. Note that Object may not be
     * `MessageSourceContact`, but its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * the `Object` representing a recent message which has been changed and
     * corresponding `SourceContact` which the registered
     * `ContactQueryListener`s are to be notified about
     */
    fun updateContactStatus(srcObj: Any, status: PresenceStatus) {
        for (msc in queryResults) {
            if (srcObj == msc && msc is MessageSourceContact) {
                msc.setStatus(status)
                super.fireContactChanged(msc)
                break
            }
        }
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this `ContactQuery`
     * that a `SourceContact` has been changed. Note that Object may not be
     * `MessageSourceContact`, but its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * the `Object` representing a recent message which has been changed and
     * corresponding `SourceContact` which the registered
     * `ContactQueryListener`s are to be notified about
     */
    fun updateContactDisplayName(srcObj: Any, newName: String?) {
        for (msc in queryResults) {
            if (srcObj == msc && msc is MessageSourceContact) {
                msc.displayName = newName
                super.fireContactChanged(msc)
                break
            }
        }
    }

    /**
     * Notifies the `ContactQueryListener`s registered with this `ContactQuery`
     * that a `SourceContact` has been removed. Note that Object may not be
     * `MessageSourceContact`, but its equals method can return true for message source
     * contact instances.
     *
     * @param srcObj
     * representing the message and its corresponding `SourceContact` which has been
     * removed and which the registered `ContactQueryListener`s are to be notified about
     */
    fun fireContactRemoved(srcObj: Any?) {
        for (msc in queryResults) {
            if (srcObj == msc) {
                super.fireContactRemoved(msc)
                break
            }
        }
    }

    /**
     * Adds a specific `SourceContact` to the list of `SourceContact`s to be
     * returned by this `ContactQuery` in response to [.getQueryResults].
     *
     * @param sourceContact
     * the `SourceContact` to be added to the `queryResults` of this
     * `ContactQuery`
     * @return `true` if the `queryResults` of this `ContactQuery` has
     * changed in response to the call
     */
    public override fun addQueryResult(sourceContact: SourceContact): Boolean {
        return super.addQueryResult(sourceContact, false)
    }
}