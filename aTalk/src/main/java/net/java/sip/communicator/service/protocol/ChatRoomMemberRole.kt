/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import java.lang.NullPointerException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Indicates roles that a chat room member detains in its containing chat room.
 *
 * @author Emil Ivov
 * @author Valentin Martinet
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
enum class ChatRoomMemberRole(roleName: String?, resource: String, roleIndex: Int) : Comparable<ChatRoomMemberRole> {
    /**
     * A role implying the full set of chat room permissions
     */
    OWNER("Owner", "service.gui.chat.role.OWNER", 70),

    /**
     * A role implying administrative permissions.
     */
    ADMINISTRATOR("Administrator", "service.gui.chat.role.ADMINISTRATOR", 60),

    /**
     * A role implying moderator permissions.
     */
    MODERATOR("Moderator", "service.gui.chat.role.MODERATOR", 50),

    /**
     * A role implying standard participant permissions.
     */
    MEMBER("Member", "service.gui.chat.role.MEMBER", 40),

    /**
     * A role implying standard participant permissions.
     */
    GUEST("Guest", "service.gui.chat.role.GUEST", 30),

    /**
     * A role implying standard participant permissions without the right to send messages/speak.
     */
    SILENT_MEMBER("SilentMember", "service.gui.chat.role.SILENT_MEMBER", 20),

    /**
     * A role implying an explicit ban for the user to join the room.
     */
    OUTCAST("Outcast", "service.gui.chat.role.OUTCAST", 10);
    /**
     * Returns the name of this role.
     *
     * @return the name of this role.
     */
    /**
     * the name of this role.
     */
    val roleName: String
    /**
     * Returns a role index that can be used to allow ordering of roles by other modules (like the
     * UI) that would not necessarily "know" all possible roles. Higher values of the role index
     * indicate roles with more permissions and lower values pertain to more restrictive roles.
     *
     * @return an `int` that when compared to role indexes of other roles can provide an
     * ordering for the different role instances.
     */
    /**
     * The index of a role is used to allow ordering of roles by other modules (like the UI) that
     * would not necessarily "know" all possible roles. Higher values of the role index indicate
     * roles with more permissions and lower values pertain to more restrictive roles.
     */
    private val roleIndex: Int
    /**
     * Returns a localized (i18n) name role name.
     *
     * @return a i18n version of this role name.
     */
    /**
     * Resource name for localization.
     */
    private val localizedRoleName: String

    /**
     * Creates a role with the specified `roleName`. The constructor is protected in case
     * protocol implementations need to add extra roles (this should only be done when absolutely
     * necessary in order to assert smooth interoperability with the user interface).
     *
     * roleName the name of this role.
     * resource the resource name to localize the enum.
     * roleIndex  an int that would allow to compare this role to others according to the set of
     * permissions that it implies.
     *
     * @throws NullPointerException if roleName is null.
     */
    init {
        if (roleName == null) throw NullPointerException("Role Name can't be null.")
        this.roleName = roleName
        localizedRoleName = resource
        this.roleIndex = roleIndex
    }

    companion object {
        private var ENUM_MAP: Map<String, ChatRoomMemberRole>? = null

        init {
            val map = ConcurrentHashMap<String, ChatRoomMemberRole>()
            for (roleName in values()) {
                map[roleName.roleName] = roleName
            }
            ENUM_MAP = Collections.unmodifiableMap(map)
        }

        /**
         * Get the ChatRoomMemberRole for a given roleName
         * @param roleName
         * @return ChatRoomMemberRole
         */
        fun fromString(roleName: String): ChatRoomMemberRole? {
            return ENUM_MAP!![roleName]
        }
    }
}