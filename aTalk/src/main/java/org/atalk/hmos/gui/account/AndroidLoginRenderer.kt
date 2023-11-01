/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.graphics.drawable.Drawable
import net.java.sip.communicator.impl.muc.MUCActivator
import net.java.sip.communicator.service.muc.MUCService
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.SecurityAuthority
import net.java.sip.communicator.service.protocol.event.CallListener
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ProviderPresenceStatusListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.StatusUtil
import net.java.sip.communicator.util.account.AccountStatusUtils
import net.java.sip.communicator.util.account.LoginManager
import net.java.sip.communicator.util.account.LoginRenderer
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.authorization.AuthorizationHandlerImpl
import org.atalk.hmos.gui.call.AndroidCallListener
import org.atalk.hmos.gui.chat.ChatSessionManager
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.hmos.gui.util.AndroidUtils
import org.atalk.hmos.gui.util.event.EventListener
import org.atalk.hmos.gui.util.event.EventListenerList
import org.atalk.service.osgi.OSGiService
import java.beans.PropertyChangeEvent

/**
 * The `AndroidLoginRenderer` is the Android renderer for login events.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class AndroidLoginRenderer(defaultSecurityAuthority: SecurityAuthority) : LoginRenderer {
    /**
     * The `CallListener`.
     */
    private val androidCallListener: CallListener

    /**
     * The android implementation of the provider presence listener.
     */
    private val androidPresenceListener: ProviderPresenceStatusListener = UIProviderPresenceStatusListener()

    /**
     * The security authority used by this login renderer.
     */
    private val mSecurityAuthority: SecurityAuthority

    /**
     * Authorization handler instance.
     */
    private val authorizationHandler: AuthorizationHandlerImpl

    /**
     * Cached global status value
     */
    private var globalStatus: PresenceStatus? = null

    /**
     * List of global status listeners.
     */
    private val globalStatusListeners = EventListenerList<PresenceStatus?>()

    /**
     * Caches avatar image to track the changes
     */
    private var localAvatarRaw: ByteArray? = null

    /**
     * Local avatar drawable
     */
    private var localAvatar: Drawable? = null

    /**
     * Caches local status to track the changes
     */
    private var localStatusRaw: ByteArray? = null

    /**
     * Local status drawable
     */
    private var localStatusDrawable: Drawable? = null

    /**
     * Creates an instance of `AndroidLoginRenderer` by specifying the current `Context`.
     *
     * defaultSecurityAuthority the security authority that will be used by this login renderer
     */
    init {
        androidCallListener = AndroidCallListener()
        mSecurityAuthority = defaultSecurityAuthority
        authorizationHandler = AuthorizationHandlerImpl()
    }

    /**
     * Adds the user interface related to the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we add the user interface
     */
    override fun addProtocolProviderUI(protocolProvider: ProtocolProviderService) {
        protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)?.addCallListener(androidCallListener)
        protocolProvider.getOperationSet(OperationSetPresence::class.java)?.addProviderPresenceStatusListener(androidPresenceListener)
    }

    /**
     * Removes the user interface related to the given protocol provider.
     *
     * @param protocolProvider the protocol provider to remove
     */
    override fun removeProtocolProviderUI(protocolProvider: ProtocolProviderService) {
        protocolProvider.getOperationSet(OperationSetBasicTelephony::class.java)?.removeCallListener(androidCallListener)
        protocolProvider.getOperationSet(OperationSetPresence::class.java)?.removeProviderPresenceStatusListener(androidPresenceListener)

        // Removes all chat session for unregistered provider
        ChatSessionManager.removeAllChatsForProvider(protocolProvider)
    }

    /**
     * Starts the connecting user interface for the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we add the connecting user interface
     */
    override fun startConnectingUI(protocolProvider: ProtocolProviderService) {}

    /**
     * Stops the connecting user interface for the given protocol provider.
     *
     * @param protocolProvider the protocol provider for which we remove the connecting user interface
     */
    override fun stopConnectingUI(protocolProvider: ProtocolProviderService) {}

    /**
     * Indicates that the given protocol provider has been connected at the given time.
     *
     * @param protocolProvider the `ProtocolProviderService` corresponding to the connected account
     * @param date the date/time at which the account has connected
     */
    override fun protocolProviderConnected(protocolProvider: ProtocolProviderService, date: Long) {
        val presence = AccountStatusUtils.getProtocolPresenceOpSet(protocolProvider)
        val authorizationHandler1 = presence?.setAuthorizationHandler(authorizationHandler)
        val multiUserChat = MUCService.getMultiUserChatOpSet(protocolProvider)
        var mucService: MUCService? = null
        if (multiUserChat != null
                && MUCActivator.mucService.also { mucService = it } != null) {
            mucService!!.synchronizeOpSetWithLocalContactList(protocolProvider, multiUserChat)
        }
        updateGlobalStatus()
    }

    /**
     * Indicates that a protocol provider connection has failed.
     *
     * @param protocolProvider the `ProtocolProviderService`, which connection failed
     * @param loginManagerCallback the `LoginManager` implementation, which is managing the process
     */
    override fun protocolProviderConnectionFailed(protocolProvider: ProtocolProviderService,
            loginManagerCallback: LoginManager) {
        val accountID = protocolProvider.accountID
        DialogActivity.showConfirmDialog(aTalkApp.globalContext,
                R.string.service_gui_ERROR,
                R.string.service_gui_CONNECTION_FAILED_MSG,
                R.string.service_gui_RETRY,
                object : DialogActivity.DialogListener {
                    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
                        loginManagerCallback.login(protocolProvider)
                        return true
                    }

                    override fun onDialogCancelled(dialog: DialogActivity) {}
                }, accountID.mUserID, accountID.service)
    }

    /**
     * Returns the `SecurityAuthority` implementation related to this login renderer.
     *
     * @param protocolProvider the specific `ProtocolProviderService`, for which we're obtaining a security
     * authority
     * @return the `SecurityAuthority` implementation related to this login renderer
     */
    override fun getSecurityAuthorityImpl(protocolProvider: ProtocolProviderService): SecurityAuthority {
        return mSecurityAuthority
    }

    /**
     * Updates aTalk icon notification to reflect current global status.
     */
    fun updateaTalkIconNotification() {
        val status = if (getGlobalStatus()!!.isOnline) {
            // At least one provider is online
            aTalkApp.getResString(R.string.service_gui_ONLINE)
        } else {
            // There are no active providers, so we consider to be in the offline state
            aTalkApp.getResString(R.string.service_gui_OFFLINE)
        }
        val notificationID = OSGiService.generalNotificationId
        if (notificationID == -1) {
            return
        }
        AndroidUtils.updateGeneralNotification(aTalkApp.globalContext, notificationID,
                aTalkApp.getResString(R.string.APPLICATION_NAME), status, System.currentTimeMillis())
    }

    /**
     * Adds global status listener.
     *
     * @param l the listener to be add.
     */
    fun addGlobalStatusListener(l: EventListener<PresenceStatus?>) {
        globalStatusListeners.addEventListener(l)
    }

    /**
     * Removes global status listener.
     *
     * @param l the listener to remove.
     */
    fun removeGlobalStatusListener(l: EventListener<PresenceStatus?>) {
        globalStatusListeners.removeEventListener(l)
    }

    /**
     * Returns current global status.
     *
     * @return current global status.
     */
    fun getGlobalStatus(): PresenceStatus? {
        if (globalStatus == null) {
            val gss = AndroidGUIActivator.globalStatusService
            globalStatus = if (gss != null) gss.getGlobalPresenceStatus() else GlobalStatusEnum.OFFLINE
        }
        return globalStatus
    }

    /**
     * AuthorizationHandler instance used by this login renderer.
     */
    fun getAuthorizationHandler(): AuthorizationHandlerImpl {
        return authorizationHandler
    }

    /**
     * Listens for all providerStatusChanged and providerStatusMessageChanged events in order
     * to refresh the account status panel, when a status is changed.
     */
    private inner class UIProviderPresenceStatusListener : ProviderPresenceStatusListener {
        override fun providerStatusChanged(evt: ProviderPresenceStatusChangeEvent) {
            updateGlobalStatus()
        }

        override fun providerStatusMessageChanged(evt: PropertyChangeEvent) {}
    }

    /**
     * Indicates if the given `protocolProvider` related user interface is already rendered.
     *
     * @param protocolProvider the `ProtocolProviderService`, which related user interface we're looking for
     * @return `true` if the given `protocolProvider` related user interface is
     * already rendered
     */
    override fun containsProtocolProviderUI(protocolProvider: ProtocolProviderService): Boolean {
        return false
    }

    /**
     * Updates the global status by picking the most connected protocol provider status.
     */
    private fun updateGlobalStatus() {
        // Only if the GUI is active (bundle context will be null on shutdown)
        if (AndroidGUIActivator.bundleContext != null) {
            // Invalidate local status image
            localStatusRaw = null
            // Invalidate global status
            globalStatus = null
            globalStatusListeners.notifyEventListeners(getGlobalStatus())
        }
        updateaTalkIconNotification()
    }

    /**
     * Returns the local user avatar drawable.
     *
     * @return the local user avatar drawable.
     */
    fun getLocalAvatarDrawable(provider: ProtocolProviderService?): Drawable? {
        val displayDetailsService = AndroidGUIActivator.globalDisplayDetailsService
        val avatarImage = displayDetailsService?.getDisplayAvatar(provider)
        // Re-create drawable only if avatar has changed
        if (!avatarImage.contentEquals(localAvatarRaw)) {
            localAvatarRaw = avatarImage
            localAvatar = AndroidImageUtil.roundedDrawableFromBytes(avatarImage)
        }
        return localAvatar
    }

    /**
     * Returns the local user status drawable.
     *
     * @return the local user status drawable
     */
    @Synchronized
    fun getLocalStatusDrawable(): Drawable? {
        val statusImage = StatusUtil.getContactStatusIcon(getGlobalStatus())
        if (!statusImage.contentEquals(localStatusRaw)) {
            localStatusRaw = statusImage
            localStatusDrawable = if (localStatusRaw != null) AndroidImageUtil.drawableFromBytes(statusImage) else null
        }
        return localStatusDrawable
    }
}