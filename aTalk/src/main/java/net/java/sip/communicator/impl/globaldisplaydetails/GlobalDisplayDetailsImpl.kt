/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.globaldisplaydetails

import android.text.TextUtils
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalAvatarChangeEvent
import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalDisplayDetailsListener
import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalDisplayNameChangeEvent
import net.java.sip.communicator.service.protocol.AccountInfoUtils.getDisplayName
import net.java.sip.communicator.service.protocol.AccountInfoUtils.getFirstName
import net.java.sip.communicator.service.protocol.AccountInfoUtils.getImage
import net.java.sip.communicator.service.protocol.AccountInfoUtils.getLastName
import net.java.sip.communicator.service.protocol.OperationSetAvatar
import net.java.sip.communicator.service.protocol.OperationSetServerStoredAccountInfo
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.ServerStoredDetails.DisplayNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail
import net.java.sip.communicator.service.protocol.event.AvatarEvent
import net.java.sip.communicator.service.protocol.event.AvatarListener
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeEvent
import net.java.sip.communicator.service.protocol.event.ServerStoredDetailsChangeListener
import net.java.sip.communicator.util.account.AccountUtils.registeredProviders
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.gui.call.CallUIUtils
import org.jivesoftware.smackx.avatar.AvatarManager
import java.util.*

/**
 * The `GlobalDisplayNameImpl` offers generic access to a global display name for the local user.
 *
 *
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
class GlobalDisplayDetailsImpl : GlobalDisplayDetailsService, RegistrationStateChangeListener, ServerStoredDetailsChangeListener, AvatarListener {
    /**
     * The display details listeners list.
     */
    private val displayDetailsListeners = ArrayList<GlobalDisplayDetailsListener?>()

    /**
     * The current first name.
     */
    private var currentFirstName: String? = null

    /**
     * The current last name.
     */
    private var currentLastName: String? = null

    /**
     * The current display name.
     */
    private var currentDisplayName: String? = null

    /**
     * The provisioned display name.
     */
    private val provisionedDisplayName = GlobalDisplayDetailsActivator.configurationService!!.getString(GLOBAL_DISPLAY_NAME_PROP, null)

    /**
     * The global display name.
     */
    private var globalDisplayName: String? = null

    /**
     * Creates an instance of `GlobalDisplayDetailsImpl`.
     */
    init {
        for (protocolProviderService in registeredProviders) {
            protocolProviderService.addRegistrationStateChangeListener(this)
        }
    }

    /**
     * Returns default display name for the given provider or the user defined display name.
     *
     * @param pps the given protocol provider service
     *
     * @return default display name.
     */
    override fun getDisplayName(pps: ProtocolProviderService?): String? {
        // assume first registered provider if null;
        var mPPS = pps
        if (mPPS == null) {
            val providers = registeredProviders
            if (providers == null || providers.isEmpty()) return ""
            mPPS = (providers as List<ProtocolProviderService?>)[0]
        }

        // proceed only if account has registered
        // cmeng (20200327) - vcard contains no fullName field so skip as it always returns null;
//        if (pps.isRegistered) {
//            final OperationSetServerStoredAccountInfo accountInfoOpSet
//                    = pps.getOperationSet(OperationSetServerStoredAccountInfo.class);
//            if (accountInfoOpSet != null) {
//                String displayName = AccountInfoUtils.getDisplayName(accountInfoOpSet);
//                if (StringUtils.isNotEmpty(displayName)) {
//                    return displayName;
//                }
//            }
//        }
        return mPPS!!.accountID.mUserID
    }

    /**
     * Returns the global avatar for the specified user.
     * Retrieve avatar via XEP-0084 and override vCard <photo></photo> content if avatarImage not null
     *
     * @return a byte array containing the global avatar for the local user
     */
    override fun getDisplayAvatar(pps: ProtocolProviderService?): ByteArray? {
        // assume first registered provider if null;
        var mPPS = pps
        if (mPPS == null) {
            val providers = registeredProviders
            if (providers.isEmpty()) return null
            mPPS = (providers as List<ProtocolProviderService?>)[0]
        }
        val userJid = mPPS!!.accountID.bareJid
        return AvatarManager.getAvatarImageByJid(userJid)
    }

    /**
     * Adds the given `GlobalDisplayDetailsListener` to listen for change events concerning
     * the global display details.
     *
     * @param l the `GlobalDisplayDetailsListener` to add
     */
    override fun addGlobalDisplayDetailsListener(l: GlobalDisplayDetailsListener?) {
        synchronized(displayDetailsListeners) { if (!displayDetailsListeners.contains(l)) displayDetailsListeners.add(l) }
    }

    /**
     * Removes the given `GlobalDisplayDetailsListener` listening for change events
     * concerning the global display details.
     *
     * @param l the `GlobalDisplayDetailsListener` to remove
     */
    override fun removeGlobalDisplayDetailsListener(l: GlobalDisplayDetailsListener?) {
        synchronized(displayDetailsListeners) { displayDetailsListeners.remove(l) }
    }

    /**
     * Updates account information when a protocol provider is registered.
     *
     * @param evt the `RegistrationStateChangeEvent` that notified us of the change
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        val protocolProvider = evt.getProvider()
        if (evt.getNewState() == RegistrationState.REGISTERED) {
            /*
             * Check the support for OperationSetServerStoredAccountInfo prior to starting the
             * Thread because only a couple of the protocols currently support it and thus
             * starting a Thread that is not going to do anything useful can be prevented.
             */
            val accountInfoOpSet = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
            if (accountInfoOpSet != null) {
                /*
                 * FIXME Starting a separate Thread for each ProtocolProviderService is
                 * uncontrollable because the application is multi-protocol and having multiple
                 * accounts is expected so one is likely to end up with a multitude of Threads.
                 * Besides, it not very  when retrieving the first and last name is to stop
                 * so one ProtocolProviderService being able to supply both the first and the
                 * last name may be overwritten by a ProtocolProviderService which is able to
                 * provide just one of them.
                 */
                UpdateAccountInfo(protocolProvider, accountInfoOpSet, false).start()
            }
            val avatarOpSet = protocolProvider.getOperationSet(OperationSetAvatar::class.java)
            avatarOpSet?.addAvatarListener(this)
            val serverStoredAccountInfo = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
            serverStoredAccountInfo?.addServerStoredDetailsChangeListener(this)
        } else if (evt.getNewState() == RegistrationState.UNREGISTERING
                || evt.getNewState() == RegistrationState.CONNECTION_FAILED) {
            val avatarOpSet = protocolProvider.getOperationSet(OperationSetAvatar::class.java)
            avatarOpSet?.removeAvatarListener(this)
            val serverStoredAccountInfo = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
            serverStoredAccountInfo?.removeServerStoredDetailsChangeListener(this)
        }
    }

    /**
     * Called whenever a new avatar is defined for one of the protocols that we have subscribed
     * for.
     *
     * @param event the event containing the new image
     */
    override fun avatarChanged(event: AvatarEvent) {
        globalAvatar = event.getNewAvatar()
        // If there is no avatar image set, then displays the default one.
        if (globalAvatar == null) {
            globalAvatar = GlobalDisplayDetailsActivator.resources!!.getImageInBytes(CallUIUtils.DEFAULT_PERSONAL_PHOTO)
        }

        // AvatarCacheUtils.cacheAvatar(event.getSourceProvider(), globalAvatar);
        val userId = event.getSourceProvider().accountID.bareJid
        AvatarManager.addAvatarImage(userId, globalAvatar, false)
        fireGlobalAvatarEvent(globalAvatar)
    }

    /**
     * Registers a ServerStoredDetailsChangeListener with the operation sets of the providers, if
     * a provider change its name we use it in the UI.
     *
     * @param evt the `ServerStoredDetailsChangeEvent` the event for name change.
     */
    override fun serverStoredDetailsChanged(evt: ServerStoredDetailsChangeEvent?) {
        if (StringUtils.isNotEmpty(provisionedDisplayName)) return
        if (evt!!.getEventID() == ServerStoredDetailsChangeEvent.DETAIL_ADDED || evt.getEventID() == ServerStoredDetailsChangeEvent.DETAIL_REPLACED
                && (evt.getNewValue() is DisplayNameDetail
                        || evt.getNewValue() is ImageDetail)) {
            val protocolProvider = evt.getProvider()
            val accountInfoOpSet = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
            UpdateAccountInfo(evt.getProvider(), accountInfoOpSet, true).start()
        }
    }

    /**
     * Queries the operations sets to obtain names and display info. Queries are done in separate thread.
     */
    private inner class UpdateAccountInfo
    /**
     * Constructs with provider and opSet to use.
     *
     * @param protocolProvider the provider.
     * @param accountInfoOpSet the opSet.
     * @param isUpdate indicates if the display name and avatar should be updated from this provider even
     * if they already have values.
     */
    (
            /**
             * The protocol provider.
             */
            private val protocolProvider: ProtocolProviderService,
            /**
             * The account info operation set to query.
             */
            private val accountInfoOpSet: OperationSetServerStoredAccountInfo?,
            /**
             * Indicates if the display name and avatar should be updated from this provider even if
             * they already have values.
             */
            private val isUpdate: Boolean) : Thread() {

        override fun run() {
            // globalAvatar = AvatarCacheUtils.getCachedAvatar(protocolProvider);
            val userId = protocolProvider.accountID.bareJid
            globalAvatar = AvatarManager.getAvatarImageByJid(userId)
            if (isUpdate || globalAvatar == null) {
                val accountImage = getImage(accountInfoOpSet!!)
                if (accountImage != null && accountImage.isNotEmpty()) {
                    globalAvatar = accountImage
                    // AvatarCacheUtils.cacheAvatar(protocolProvider, globalAvatar);
                    AvatarManager.addAvatarImage(userId, globalAvatar, false)
                } else {
                    globalAvatar = ByteArray(0)
                }
                fireGlobalAvatarEvent(globalAvatar)
            }
            if (!isUpdate || (!TextUtils.isEmpty(provisionedDisplayName)
                            && StringUtils.isNotEmpty(globalDisplayName))) return
            if (currentFirstName == null) {
                val firstName = getFirstName(accountInfoOpSet!!)
                if (StringUtils.isNotEmpty(firstName)) {
                    currentFirstName = firstName
                }
            }
            if (currentLastName == null) {
                val lastName = getLastName(accountInfoOpSet!!)
                if (StringUtils.isNotEmpty(lastName)) {
                    currentLastName = lastName
                }
            }
            if (currentFirstName == null && currentLastName == null) {
                val displayName = getDisplayName(accountInfoOpSet!!)
                if (displayName != null) currentDisplayName = displayName
            }
            setGlobalDisplayName()
        }

        /**
         * Called on the event dispatching thread (not on the worker thread) after the
         * `construct` method has returned.
         */
        private fun setGlobalDisplayName() {
            var accountName: String? = null
            if (StringUtils.isNotEmpty(currentFirstName)) {
                accountName = currentFirstName
            }
            if (StringUtils.isNotEmpty(currentLastName)) {
                /*
                 * If accountName is null, don't use += because it will make the accountName start
                 * with the string "null".
                 */
                if (StringUtils.isEmpty(accountName)) accountName = currentLastName else accountName += " $currentLastName"
            }
            if (currentFirstName == null && currentLastName == null) {
                if (currentDisplayName != null) accountName = currentDisplayName
            }
            globalDisplayName = accountName
            if (StringUtils.isNotEmpty(globalDisplayName)) {
                fireGlobalDisplayNameEvent(globalDisplayName)
            }
        }
    }

    /**
     * Notifies all interested listeners of a global display details change.
     *
     * @param displayName the new display name
     */
    private fun fireGlobalDisplayNameEvent(displayName: String?) {
        var listeners: List<GlobalDisplayDetailsListener?>
        synchronized(displayDetailsListeners) { listeners = Collections.unmodifiableList(displayDetailsListeners) }
        val listIter = listeners.iterator()
        while (listIter.hasNext()) {
            listIter.next()!!.globalDisplayNameChanged(
                    GlobalDisplayNameChangeEvent(this, displayName!!))
        }
    }

    /**
     * Notifies all interested listeners of a global display details change.
     *
     * @param avatar the new avatar
     */
    private fun fireGlobalAvatarEvent(avatar: ByteArray?) {
        var listeners: List<GlobalDisplayDetailsListener?>
        synchronized(displayDetailsListeners) { listeners = Collections.unmodifiableList(displayDetailsListeners) }
        for (listener in listeners) {
            listener!!.globalDisplayAvatarChanged(
                    GlobalAvatarChangeEvent(this, avatar!!))
        }
    }

    companion object {
        /**
         * Property to disable auto displayName update.
         */
        private const val GLOBAL_DISPLAY_NAME_PROP = "gui.presence.GLOBAL_DISPLAY_NAME"

        /**
         * The global avatar.
         */
        private var globalAvatar: ByteArray? = null
    }
}