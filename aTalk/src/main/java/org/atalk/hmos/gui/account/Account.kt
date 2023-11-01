/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.content.Context
import android.graphics.drawable.Drawable
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.OperationSet
import net.java.sip.communicator.service.protocol.OperationSetAvatar
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolIcon
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.AvatarEvent
import net.java.sip.communicator.service.protocol.event.AvatarListener
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.UtilActivator
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.gui.util.AccountUtil
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.hmos.gui.util.event.EventListener
import org.atalk.hmos.gui.util.event.EventListenerList
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.jxmpp.jid.Jid
import org.jxmpp.jid.impl.JidCreate
import org.jxmpp.stringprep.XmppStringprepException
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import timber.log.Timber
import java.beans.PropertyChangeEvent
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Class exposes account information for specified [AccountID] in a form that can be easily
 * used for building GUI. It tracks changes of [PresenceStatus], [RegistrationState]
 * and avatar changes and  passes them as an [AccountEvent] to registered
 * [EventListener]s.<br></br>
 * It also provides default values for fields that may be currently unavailable from
 * corresponding [OperationSet] or [ProtocolProviderService].
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class Account(accountID: AccountID, context: BundleContext, activityContext: Context) : ProviderPresenceStatusListener, RegistrationStateChangeListener, ServiceListener, AvatarListener {
    /**
     * The encapsulated [AccountID]
     */
    private val mAccountID: AccountID

    /**
     * The [BundleContext] of parent OSGiActivity
     */
    private val bundleContext: BundleContext

    /**
     * The [Context] of parent [android.app.Activity]
     */
    private val activityContext: Context

    /**
     * List of [EventListener]s that listen for [AccountEvent]s.
     */
    private val listeners = EventListenerList<AccountEvent>()

    /**
     * The [Drawable] representing protocol's image
     */
    private val protocolIcon: Drawable?

    /**
     * The [ProtocolProviderService] if is currently available
     */
    var protocolProvider: ProtocolProviderService? = null
        /**
         * Sets the currently active [ProtocolProviderService] for encapsulated [.mAccountID].
         */
        set(protocolProvider) {
            if (field != null && protocolProvider != null) {
                if (field == protocolProvider) {
                    return
                }

                Timber.w("This account have already registered provider - will update")
                // Unregister old
                field = null

                // Register new
                field = protocolProvider
            }

            when {
                protocolProvider != null -> {
                    protocolProvider.addRegistrationStateChangeListener(this)

                    val presenceOpSet = protocolProvider.getOperationSet(OperationSetPresence::class.java)
                    if (presenceOpSet == null) {
                        Timber.w("%s does not support presence operations", protocolProvider.protocolDisplayName)
                    } else {
                        presenceOpSet.addProviderPresenceStatusListener(this)
                    }
                    protocolProvider.getOperationSet(OperationSetAvatar::class.java)?.addAvatarListener(this)
                    Timber.d("Registered listeners for %s", protocolProvider)
                }

                field != null -> {
                    // Unregister listeners
                    field!!.removeRegistrationStateChangeListener(this)

                    field!!.getOperationSet(OperationSetPresence::class.java)?.removeProviderPresenceStatusListener(this)
                    field!!.getOperationSet(OperationSetAvatar::class.java)?.removeAvatarListener(this)
                }
            }
            field = protocolProvider
        }

    /**
     * Creates new instance of Account
     *
     * accountID the AccountID that will be encapsulated by this class
     * context the BundleContext of parent OSGiActivity
     * activityContext the Context of parent android.app.Activity
     */
    init {
        mAccountID = accountID
        protocolProvider = AccountUtils.getRegisteredProviderForAccount(accountID)
        bundleContext = context
        bundleContext.addServiceListener(this)
        this.activityContext = activityContext
        protocolIcon = initProtocolIcon()
    }

    /**
     * Tries to retrieve the protocol's icon
     *
     * @return protocol's icon
     */
    private fun initProtocolIcon(): Drawable? {
        var blob: ByteArray? = null
        if (protocolProvider != null) blob = protocolProvider!!.protocolIcon.getIcon(ProtocolIcon.ICON_SIZE_32x32)
        if (blob != null) return AndroidImageUtil.drawableFromBytes(blob)
        val iconPath = mAccountID.getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_ICON_PATH)
        if (iconPath != null) {
            blob = loadIcon(iconPath)
            if (blob != null) return AndroidImageUtil.drawableFromBytes(blob)
        }
        return null
    }

    /**
     * Tries to get the [OperationSetPresence] for encapsulated [AccountID]
     *
     * @return the [OperationSetPresence] if the protocol is active and supports it or
     * `null` otherwise
     */
    fun getPresenceOpSet(): OperationSetPresence? {
        return if (protocolProvider == null) null else protocolProvider!!.getOperationSet(OperationSetPresence::class.java)
    }

    /**
     * Tries to get the [OperationSetAvatar] if the protocol supports it and is currently
     * active
     *
     * @return the [OperationSetAvatar] for encapsulated [AccountID] if it's supported
     * and active or
     * `null` otherwise
     */
    fun getAvatarOpSet(): OperationSetAvatar? {
        return if (protocolProvider == null) null else protocolProvider!!.getOperationSet(OperationSetAvatar::class.java)
    }

    /**
     * Tracks the de/registration of [ProtocolProviderService] for encapsulated
     * [AccountID]
     *
     * @param event the [ServiceEvent]
     */
    override fun serviceChanged(event: ServiceEvent) {
        // if the event is caused by a bundle being stopped, we don't want to know
        if (event.serviceReference.bundle.state == Bundle.STOPPING) {
            return
        }

        // we don't care if the source service is not a protocol provider
        val sourceService = bundleContext.getService(event.serviceReference) as? ProtocolProviderService
                ?: return

        // we don't care if the source service is not a protocol provider
        if (sourceService.accountID != mAccountID) {
            // Only interested for this account
            return
        }
        if (event.type == ServiceEvent.REGISTERED) {
            this.protocolProvider = sourceService
        } else if (event.type == ServiceEvent.UNREGISTERING) {
            this.protocolProvider = null
        }
    }

    /**
     * Unregisters from all services and clears [.listeners]
     */
    fun destroy() {
        protocolProvider = null
        bundleContext.removeServiceListener(this)
        listeners.clear()
    }

    /**
     * Adds [EventListener] that will be listening for changed that occurred to this
     * [Account]. In particular these are the registration status, presence status and
     * avatar events.
     *
     * @param listener the [EventListener] that listens for changes on this [Account] object
     */
    fun addAccountEventListener(listener: EventListener<AccountEvent>) {
        Timber.log(TimberLog.FINER, "Added change listener %s", listener)
        listeners.addEventListener(listener)
    }

    /**
     * Removes the given `listener` from observers list
     *
     * @param listener the [EventListener] that doesn't want to be notified about the changes to this
     * [Account] anymore
     */
    fun removeAccountEventListener(listener: EventListener<AccountEvent>) {
        Timber.log(TimberLog.FINER, "Removed change listener %s", listener)
        listeners.removeEventListener(listener)
    }

    override fun providerStatusChanged(evt: ProviderPresenceStatusChangeEvent) {
        Timber.log(TimberLog.FINER, "Provider status notification")
        listeners.notifyEventListeners(AccountEvent(this, AccountEvent.PRESENCE_STATUS_CHANGE))
    }

    override fun providerStatusMessageChanged(evt: PropertyChangeEvent) {
        Timber.log(TimberLog.FINER, "Provider status msg notification")
        listeners.notifyEventListeners(AccountEvent(this, AccountEvent.STATUS_MSG_CHANGE))
    }

    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        Timber.log(TimberLog.FINER, "Provider registration notification")
        listeners.notifyEventListeners(AccountEvent(this, AccountEvent.REGISTRATION_CHANGE))
    }

    override fun avatarChanged(event: AvatarEvent) {
        Timber.log(TimberLog.FINER, "Avatar changed notification")
        updateAvatar(event.getNewAvatar())
        listeners.notifyEventListeners(AccountEvent(this, AccountEvent.AVATAR_CHANGE))
    }

    /**
     * Returns the display name
     *
     * @return the display name of this [Account]
     */
    fun getAccountName(): String? {
        return mAccountID.displayName
    }

    /**
     * Returns the current presence status name of this [Account]
     *
     * @return current presence status name
     */
    fun getStatusName(): String {
        val presence = getPresenceOpSet()
        return presence?.getPresenceStatus()?.statusName ?: GlobalStatusEnum.OFFLINE_STATUS
    }

    /**
     * Returns the [Drawable] protocol icon
     *
     * @return the protocol's icon valid for this [Account]
     */
    fun getProtocolIcon(): Drawable? {
        return protocolIcon
    }

    /**
     * Returns the current [PresenceStatus] icon
     *
     * @return the icon describing actual [PresenceStatus] of this [Account]
     */
    fun getStatusIcon(): Drawable? {
        val presence = getPresenceOpSet()
        if (presence != null) {
            val statusBlob = presence.getPresenceStatus()!!.statusIcon
            if (statusBlob != null) return AndroidImageUtil.drawableFromBytes(statusBlob)
        }
        return AccountUtil.getDefaultPresenceIcon(activityContext, mAccountID.protocolName)
    }

    /**
     * Returns `true` if this [Account] is enabled
     *
     * @return `true` if this [Account] is enabled
     */
    fun isEnabled(): Boolean {
        return mAccountID.isEnabled
    }

    /**
     * Returns encapsulated [AccountID]
     *
     * @return the [AccountID] encapsulated by this instance of [Account]
     */
    fun getAccountID(): AccountID {
        return mAccountID
    }

    /**
     * Returns the user id (Jid) associated with this account e.g. abc123@example.org.
     *
     * @return A String identifying the user inside this particular service.
     */
    fun getUserID(): String {
        return mAccountID.mUserID!!
    }

    fun getJid(): Jid? {
        var jid: Jid? = null
        try {
            jid = JidCreate.from(mAccountID.mUserID)
        } catch (e: XmppStringprepException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        return jid
    }

    /**
     * Current avatar image
     */
    var avatarIcon: Drawable? = null
        get() {
            if (field == null) {
                var avatarBlob: ByteArray? = null
                try {
                    val avatarOpSet = getAvatarOpSet()
                    if (avatarOpSet != null) {
                        avatarBlob = avatarOpSet.getAvatar()
                    }
                } catch (exc: IllegalStateException) {
                    Timber.e("Error retrieving avatar: %s", exc.message)
                }
                updateAvatar(avatarBlob)
            }
            return field
        }

    /**
     * Sets the avatar icon. If `newAvatar` is specified as `null` the default one
     * is set
     *
     * @param newAvatar an array of bytes with raw avatar image data
     */
    private fun updateAvatar(newAvatar: ByteArray?) {
        avatarIcon = if (newAvatar == null) {
            AccountUtil.getDefaultAvatarIcon(activityContext)
        } else {
            AndroidImageUtil.drawableFromBytes(newAvatar)
        }
    }

    companion object {
        /**
         * Unique identifier for the account
         */
        const val UUID = "uuid"

        /**
         * Loads an image from a given image path.
         *
         * @param imagePath The identifier of the image.
         * @return The image for the given identifier.
         */
        fun loadIcon(imagePath: String): ByteArray? {
            val resources = UtilActivator.resources
            var icon: ByteArray? = null

            val inputStream = resources.getImageInputStreamForPath(imagePath)
                    ?: return null
            try {
                val bout = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var read: Int
                while (-1 != inputStream.read(buffer).also { read = it }) {
                    bout.write(buffer, 0, read)
                }
                icon = bout.toByteArray()
            } catch (ioex: IOException) {
                Timber.e(ioex, "Failed to load protocol icon: %s", imagePath)
            }
            return icon
        }
    }
}