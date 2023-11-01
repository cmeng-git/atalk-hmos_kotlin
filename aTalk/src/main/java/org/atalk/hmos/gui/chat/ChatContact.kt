/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.chat

import android.graphics.drawable.Drawable
import org.atalk.hmos.gui.util.AndroidImageUtil.getScaledRoundedIcon

/**
 * The `ChatContact` is a wrapping class for the `Contact` and `ChatRoomMember` interface.
 *
 * @param <T> the type of the descriptor
 * @author Yana Stamcheva
 * @author Lubomir Marinov
</T> */
abstract class ChatContact<T>
/**
 * Initializes a new `ChatContact` instance with a specific descriptor.
 *
 * @param descriptor the descriptor to be adapted by the new instance
 */
protected constructor(
        /**
         * The descriptor being adapted by this instance.
         */
        val descriptor: T) {
    /**
     * Returns the avatar image corresponding to the source contact. In the case of multi user
     * chat contact returns null.
     *
     * @return the avatar image corresponding to the source contact. In the case of multi user
     * chat contact returns null
     */
    /**
     * The avatar image corresponding to the source contact in the form of an `ImageIcon`.
     */
    var avatar: Drawable? = null
        get() {
            val avatarBytes = getAvatarBytes()
            if (!this.avatarBytes.contentEquals(avatarBytes)) {
                this.avatarBytes = avatarBytes
                field = null
            }
            if (field == null && this.avatarBytes != null && this.avatarBytes!!.isNotEmpty())
                field = getScaledRoundedIcon(this.avatarBytes!!, AVATAR_ICON_WIDTH, AVATAR_ICON_HEIGHT)
            return field
        }
        private set

    /**
     * The avatar image corresponding to the source contact in the form of an array of bytes.
     */
    private var avatarBytes: ByteArray? = null

    /**
     * Returns the descriptor object corresponding to this chat contact. In the case of single chat this could
     * be the `MetaContact` and in the case of conference chat this could be the `ChatRoomMember`.
     *
     * @return the descriptor object corresponding to this chat contact.
     */
    /**
     * Returns `true` if this is the currently selected contact in the list of contacts
     * for the chat, otherwise returns `false`.
     *
     * @return `true` if this is the currently selected contact in the list of contacts
     * for the chat, otherwise returns `false`.
     */
    /**
     * Sets this isSelected property of this chat contact.
     *
     * selected `true` to indicate that this contact would be the selected contact in the
     * list of chat window contacts; otherwise, `false`
     */
    /**
     * If this instance is selected.
     */
    var isSelected = false

    /**
     * Determines whether a specific `Object` represents the same value as this
     * `ChatContact`.
     *
     * @param other the `Object` to be checked for value equality with this `ChatContact`
     * @return `true` if `obj` represents the same value as this
     * `ChatContact`; otherwise, `false`.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        /*
         * ChatContact is an adapter so two ChatContacts of the same runtime type with equal descriptors are equal.
         */
        if (!javaClass.isInstance(other)) return false
        val chatContact = other as ChatContact<T>
        return descriptor == chatContact.descriptor
    }

    /**
     * Gets the avatar image corresponding to the source contact in the form of an array of bytes.
     *
     * @return an array of bytes which represents the avatar image corresponding to the source contact
     */
    protected abstract fun getAvatarBytes(): ByteArray?

    /**
     * Returns the contact name.
     *
     * @return the contact name
     */
    abstract val name: String?

    /**
     * Gets the implementation-specific identifier which uniquely specifies this contact.
     *
     * @return an identifier which uniquely specifies this contact
     */
    abstract val uID: String?

    /**
     * Gets a hash code value for this object for the benefit of hashTables.
     *
     * @return a hash code value for this object
     */
    override fun hashCode(): Int {
        /*
         * ChatContact is an adapter so two ChatContacts of the same runtime type with equal descriptors are equal.
         */
        return descriptor.hashCode()
    }

    companion object {
        /**
         * The height of the avatar icon.
         */
        const val AVATAR_ICON_HEIGHT = 25

        /**
         * The width of the avatar icon.
         */
        const val AVATAR_ICON_WIDTH = 25
    }
}