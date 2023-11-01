/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber

import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.ContactGroup
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresencePermissions
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import java.util.*

/**
 * Implements group edit permissions.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class OperationSetPersistentPresencePermissionsJabberImpl internal constructor(
        /**
         * The parent provider.
         */
        private val provider: ProtocolProviderServiceJabberImpl) : OperationSetPersistentPresencePermissions {
    /**
     * List of group names to be considered as readonly.
     */
    private val readonlyGroups: MutableList<String?> = ArrayList()

    init {
        val readOnlyGroupsStr = provider.accountID.getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_READ_ONLY_GROUPS)
        if (readOnlyGroupsStr != null) {
        val tokenizer = StringTokenizer(readOnlyGroupsStr, ",")
        while (tokenizer.hasMoreTokens()) {
            readonlyGroups.add(tokenizer.nextToken().trim { it <= ' ' })

        }   }
    }

    /**
     * Is the whole contact list for the current provider readonly.
     *
     * @return `true` if the whole contact list is readonly, otherwise `false`.
     */
    override fun isReadOnly(): Boolean {
        if (readonlyGroups.contains(ALL_GROUPS_STR)) return true
        val groupsList: MutableList<String?> = ArrayList()
        groupsList.add(ROOT_GROUP_STR)
        val groupsIter = provider
                .getOperationSet(OperationSetPersistentPresence::class.java)!!.getServerStoredContactListRoot()!!.subgroups()
        while (groupsIter!!.hasNext()) {
            groupsList.add(groupsIter.next()!!.getGroupName())
        }
        if (groupsList.size > readonlyGroups.size) return false
        groupsList.removeAll(readonlyGroups)
        return groupsList.size <= 0
    }

    /**
     * Checks whether the `contact` can be edited, removed, moved. If the parent group is readonly.
     *
     * @param contact the contact to check.
     * @return `true` if the contact is readonly, otherwise `false`.
     */
    override fun isReadOnly(contact: Contact?): Boolean {
        return isReadOnly(contact!!.parentContactGroup)
    }

    /**
     * Checks whether the `group` is readonly.
     *
     * @param group the group to check.
     * @return `true` if the group is readonly, otherwise `false`.
     */
    override fun isReadOnly(group: ContactGroup?): Boolean {
        if (isReadOnly()) return true
        return if (group is RootContactGroupJabberImpl) readonlyGroups.contains(ROOT_GROUP_STR) else readonlyGroups.contains(group!!.getGroupName())
    }

    companion object {
        /**
         * Will indicate everything is readonly.
         */
        private const val ALL_GROUPS_STR = "all"

        /**
         * Can be added to mark root group as readonly.
         */
        private const val ROOT_GROUP_STR = "root"
    }
}