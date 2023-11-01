/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.globaldisplaydetails

import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.RegistrationState
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.util.account.AccountStatusUtils
import net.java.sip.communicator.util.account.AccountUtils.registeredProviders
import net.java.sip.communicator.util.account.LoginManager
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import timber.log.Timber

/**
 * Global statuses service impl - The ActionBar status indicator acts both as global presence
 * status for all the registered accounts; as well as the menu input giving access to the outside
 * to change the status of all registered accounts.
 *
 *
 * (Not implemented in android) When implemented global status menu with list of all account
 * statuses, then change to individual protocol provider status is allowed.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class GlobalStatusServiceImpl : GlobalStatusService, RegistrationStateChangeListener {
    /**
     * Handles newly added providers.
     *
     * @param pps the protocolProviderService
     */
    fun handleProviderAdded(pps: ProtocolProviderService) {
        pps.addRegistrationStateChangeListener(this)
        if (pps.isRegistered) {
            handleProviderRegistered(pps, false)
        }
    }

    /**
     * Handles removed providers.
     *
     * @param pps the Protocol Service Provider.
     */
    fun handleProviderRemoved(pps: ProtocolProviderService) {
        pps.removeRegistrationStateChangeListener(this)
    }

    /**
     * Returns the global presence status.
     *
     * @return the current global presence status
     */
    override fun getGlobalPresenceStatus(): PresenceStatus {
        var status = PresenceStatus.OFFLINE
        val pProviders = registeredProviders
        // If we don't have registered providers we return offline status.
        if (pProviders.isEmpty()) return getPresenceStatus(status)
        var hasAvailableProvider = false
        for (protocolProvider in pProviders) {
            // We do not show hidden protocols in our status bar, so we do not care about their status here.
            if (!protocolProvider.accountID.isHidden && protocolProvider.isRegistered) {
                val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)
                if (presence == null) {
                    hasAvailableProvider = true
                } else {
                    val presenceStatus = presence.getPresenceStatus()!!.status
                    // Assign presenceStatus with new valid if > last status
                    if (presenceStatus > status) {
                        status = presenceStatus
                    }
                }
            }
        }
        // if we have at least one online provider and without OperationSetPresence feature
        if (status == PresenceStatus.OFFLINE && hasAvailableProvider) status = PresenceStatus.AVAILABLE_THRESHOLD
        return getPresenceStatus(status)
    }

    /**
     * Returns the `GlobalStatusEnum` corresponding to the given status. For the
     * status constants we use here are the values defined in the `PresenceStatus`,
     * but this is only for convenience.
     *
     * @param status the status to which the item should correspond
     * @return the `GlobalStatusEnum` corresponding to the given status
     */
    private fun getPresenceStatus(status: Int): PresenceStatus {
        return if (status < PresenceStatus.ONLINE_THRESHOLD) {
            GlobalStatusEnum.OFFLINE
        } else if (status < PresenceStatus.EXTENDED_AWAY_THRESHOLD) {
            GlobalStatusEnum.DO_NOT_DISTURB
        } else if (status < PresenceStatus.AWAY_THRESHOLD) {
            GlobalStatusEnum.EXTENDED_AWAY
        } else if (status < PresenceStatus.AVAILABLE_THRESHOLD) {
            GlobalStatusEnum.AWAY
        } else if (status < PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD) {
            GlobalStatusEnum.ONLINE
        } else if (status < PresenceStatus.MAX_STATUS_VALUE) {
            GlobalStatusEnum.FREE_FOR_CHAT
        } else {
            GlobalStatusEnum.OFFLINE
        }
    }

    /**
     * Returns the last status that was stored in the configuration for the given protocol provider.
     *
     * @param protocolProvider the protocol provider
     * @return the last status that was stored in the configuration for the given protocol provider
     */
    override fun getLastPresenceStatus(protocolProvider: ProtocolProviderService): PresenceStatus? {
        val lastStatus = getLastStatusString(protocolProvider)
        var status: PresenceStatus? = null
        if (lastStatus != null) {
            val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)
                    ?: return null

            // Check if there's such status in the supported presence status set.
            for (presenceStatus in presence.getSupportedStatusSet()) {
                if (presenceStatus!!.statusName == lastStatus) {
                    status = presenceStatus
                    break
                }
            }

            // If we haven't found the last status in the protocol provider supported status set,
            // we'll have a look for a corresponding global status and its protocol representation.
            if (status == null) {
                when (lastStatus) {
                    GlobalStatusEnum.ONLINE_STATUS -> status = getPresenceStatus(protocolProvider, PresenceStatus.AVAILABLE_THRESHOLD,
                            PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD)
                    GlobalStatusEnum.AWAY_STATUS -> status = getPresenceStatus(protocolProvider, PresenceStatus.AWAY_THRESHOLD,
                            PresenceStatus.AVAILABLE_THRESHOLD)
                    GlobalStatusEnum.EXTENDED_AWAY_STATUS -> status = getPresenceStatus(protocolProvider, PresenceStatus.EXTENDED_AWAY_THRESHOLD,
                            PresenceStatus.AWAY_THRESHOLD)
                    GlobalStatusEnum.DO_NOT_DISTURB_STATUS -> status = getPresenceStatus(protocolProvider, PresenceStatus.ONLINE_THRESHOLD,
                            PresenceStatus.EXTENDED_AWAY_THRESHOLD)
                    GlobalStatusEnum.FREE_FOR_CHAT_STATUS -> status = getPresenceStatus(protocolProvider, PresenceStatus.AVAILABLE_THRESHOLD,
                            PresenceStatus.MAX_STATUS_VALUE)
                    GlobalStatusEnum.OFFLINE_STATUS -> status = getPresenceStatus(protocolProvider, 0, PresenceStatus.ONLINE_THRESHOLD)
                }
            }
        }
        return status
    }

    /**
     * Returns the last contact status saved in the configuration.
     *
     * @param protocolProvider the protocol provider to which the status corresponds
     * @return the last contact status saved in the configuration.
     */
    override fun getLastStatusString(protocolProvider: ProtocolProviderService): String? {
        // find the last contact status saved in the configuration.
        var lastStatus: String? = null
        val accountUuid = protocolProvider.accountID.accountUuid
        if (StringUtils.isNotEmpty(accountUuid)) {
            val configService = GlobalDisplayDetailsActivator.configurationService
            lastStatus = configService!!.getString("$accountUuid.lastAccountStatus")
        }
        return lastStatus
    }

    /**
     * Publish present status for the given protocolProvider
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param status the status to publish.
     */
    fun publishStatus(protocolProvider: ProtocolProviderService?, status: PresenceStatus?) {
        publishStatusInternal(protocolProvider, status, false)
    }

    /**
     * Publish present status for the given protocolProvider
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param status the status to publish.
     * @param state whether the publish status is invoked after registrationStateChanged for a provider,
     * where the provider is expected to be REGISTERED, if not we do nothing
     * (means it has connection failed soon after firing registered).
     */
    override fun publishStatus(protocolProvider: ProtocolProviderService, status: PresenceStatus, state: Boolean) {
        publishStatusInternal(protocolProvider, status, state)
    }

    /**
     * Publish <present></present> status to the server; it takes appropriate action including login
     * and logout to change state if the actual pps status is not per the requested status.
     * #TODO cmeng: may be this should not be this class responsibility to do this.
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param status the status to publish.
     * @param dueToRegistrationStateChanged whether the publish status is invoked after registrationStateChanged
     * for a provider, where the provider is expected to be REGISTERED, if not we do nothing
     * (means it has connection failed soon after firing registered).
     */
    private fun publishStatusInternal(protocolProvider: ProtocolProviderService?, status: PresenceStatus?,
            dueToRegistrationStateChanged: Boolean) {
        val presence = protocolProvider!!.getOperationSet(OperationSetPresence::class.java)
        var loginManager: LoginManager? = null
        val uiService = GlobalDisplayDetailsActivator.uIService
        if (uiService != null) {
            loginManager = uiService.loginManager
        }
        val registrationState = protocolProvider.registrationState
        if (registrationState === RegistrationState.REGISTERED && presence != null && presence.getPresenceStatus() != status) {
            if (status!!.isOnline) {
                PublishPresenceStatusThread(protocolProvider, presence, status).start()
            } else {
                if (loginManager != null) loginManager.isManuallyDisconnected = true
                LoginManager.logoff(protocolProvider)
            }
        } else if (registrationState != RegistrationState.REGISTERED
                && registrationState != RegistrationState.REGISTERING
                && registrationState != RegistrationState.AUTHENTICATING
                && status!!.isOnline) {
            if (dueToRegistrationStateChanged) {
                // If provider fires registered, and while dispatching the registered event a fatal
                // error rise in the connection and the provider goes in connection_failed we can
                // end up here calling login and going over the same cycle over and over again
                Timber.w("Called publish status for provider in wrong state provider: %s " + protocolProvider
                        + " registrationState: %s status: %s", protocolProvider, registrationState, status)
                return
            } else {
                loginManager!!.login(protocolProvider)
            }
        } else if (!status!!.isOnline && !(registrationState === RegistrationState.UNREGISTERING)) {
            if (loginManager != null) loginManager.isManuallyDisconnected = true
            LoginManager.logoff(protocolProvider)
        }
        saveStatusInformation(protocolProvider, status.statusName)
    }

    /**
     * Publish present status. We search for the highest value in the given interval.
     *
     *
     * change the status.
     *
     * @param status account status indicator on action bar
     */
    override fun publishStatus(status: GlobalStatusEnum?) {
        val itemName = status!!.statusName
        val loginManager = GlobalDisplayDetailsActivator.uIService!!.loginManager
        val protocolProviders = registeredProviders
        for (protocolProvider in protocolProviders) {
            when (itemName) {
                GlobalStatusEnum.ONLINE_STATUS -> if (!protocolProvider.isRegistered) {
                    saveStatusInformation(protocolProvider, itemName)
                    loginManager!!.login(protocolProvider)
                } else {
                    val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)
                    if (presence == null) {
                        saveStatusInformation(protocolProvider, itemName)
                    } else {
                        for (statusx in presence.getSupportedStatusSet()) {
                            if (statusx!!.status < PresenceStatus.EAGER_TO_COMMUNICATE_THRESHOLD && statusx.status >= PresenceStatus.AVAILABLE_THRESHOLD) {
                                PublishPresenceStatusThread(protocolProvider, presence, statusx).start()
                                saveStatusInformation(protocolProvider, statusx.statusName)
                                break
                            }
                        }
                    }
                }
                GlobalStatusEnum.OFFLINE_STATUS -> if (protocolProvider.registrationState != RegistrationState.UNREGISTERED
                        && protocolProvider.registrationState != RegistrationState.UNREGISTERING) {
                    val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)
                    if (presence == null) {
                        saveStatusInformation(protocolProvider, itemName)
                        LoginManager.logoff(protocolProvider)
                    } else {
                        for (statusX in presence.getSupportedStatusSet()) {
                            if (statusX!!.status < PresenceStatus.ONLINE_THRESHOLD) {
                                saveStatusInformation(protocolProvider, statusX.statusName)
                                break
                            }
                        }
                        // Must use separate thread for account unRegistration. Otherwise
                        // StrictMode Exception from android-protocolProvider.unregister(true);
                        LoginManager.logoff(protocolProvider)
                    }
                }
                GlobalStatusEnum.FREE_FOR_CHAT_STATUS -> if (!protocolProvider.isRegistered) {
                    saveStatusInformation(protocolProvider, itemName)
                    loginManager!!.login(protocolProvider)
                } else  // we search for highest available status here
                    publishStatus(protocolProvider, PresenceStatus.AVAILABLE_THRESHOLD, PresenceStatus.MAX_STATUS_VALUE)
                GlobalStatusEnum.DO_NOT_DISTURB_STATUS -> if (!protocolProvider.isRegistered) {
                    saveStatusInformation(protocolProvider, itemName)
                    loginManager!!.login(protocolProvider)
                } else {
                    // status between online and away is DND
                    publishStatus(protocolProvider, PresenceStatus.ONLINE_THRESHOLD, PresenceStatus.EXTENDED_AWAY_THRESHOLD)
                }
                GlobalStatusEnum.AWAY_STATUS -> if (!protocolProvider.isRegistered) {
                    saveStatusInformation(protocolProvider, itemName)
                    loginManager!!.login(protocolProvider)
                } else {
                    // a status in the away interval
                    publishStatus(protocolProvider, PresenceStatus.AWAY_THRESHOLD, PresenceStatus.AVAILABLE_THRESHOLD)
                }
                GlobalStatusEnum.EXTENDED_AWAY_STATUS -> if (!protocolProvider.isRegistered) {
                    saveStatusInformation(protocolProvider, itemName)
                    loginManager!!.login(protocolProvider)
                } else {
                    // a status in the away interval
                    publishStatus(protocolProvider, PresenceStatus.EXTENDED_AWAY_THRESHOLD, PresenceStatus.AWAY_THRESHOLD)
                }
            }
        }
    }

    /**
     * Publish present status. We search for the highest value in the given interval.
     *
     * @param protocolProvider the protocol provider to which we change the status.
     * @param floorStatusValue the min status value.
     * @param ceilStatusValue the max status value.
     */
    private fun publishStatus(protocolProvider: ProtocolProviderService, floorStatusValue: Int,
            ceilStatusValue: Int) {
        if (protocolProvider.isRegistered) {
            val status = getPresenceStatus(protocolProvider, floorStatusValue, ceilStatusValue)
            if (status != null) {
                val presence = protocolProvider.getOperationSet(OperationSetPresence::class.java)
                PublishPresenceStatusThread(protocolProvider, presence, status).start()
                saveStatusInformation(protocolProvider, status.statusName)
            }
        }
    }

    private fun getPresenceStatus(protocolProvider: ProtocolProviderService?, floorStatusValue: Int,
            ceilStatusValue: Int): PresenceStatus? {
        val presence = protocolProvider!!.getOperationSet(OperationSetPresence::class.java)
                ?: return null
        var status: PresenceStatus? = null
        for (currentStatus in presence.getSupportedStatusSet()) {
            if (status == null && currentStatus!!.status < ceilStatusValue && currentStatus.status >= floorStatusValue) {
                status = currentStatus
            }
            if (status != null) {
                if (currentStatus!!.status < ceilStatusValue && currentStatus.status >= floorStatusValue && currentStatus.status > status.status) {
                    status = currentStatus
                }
            }
        }
        return status
    }

    /**
     * Saves the last status for all accounts. This information is used on logging. Each time user
     * logs in he's logged with the same status as he was the last time before closing the
     * application.
     *
     * @param protocolProvider the protocol provider to save status information for
     * @param statusName the name of the status to save
     */
    private fun saveStatusInformation(protocolProvider: ProtocolProviderService?, statusName: String) {
        val configService = GlobalDisplayDetailsActivator.configurationService
        val accountUuid = protocolProvider!!.accountID.accountUuid
        if (StringUtils.isNotEmpty(accountUuid)) {
            configService!!.setProperty("$accountUuid.lastAccountStatus", statusName)
        }
    }

    /**
     * Waits for providers to register and then checks for its last status saved if any and
     * used it to restore its status.
     *
     * @param evt a `RegistrationStateChangeEvent` which describes the
     */
    override fun registrationStateChanged(evt: RegistrationStateChangeEvent) {
        if (evt.getNewState() == RegistrationState.REGISTERED) handleProviderRegistered(evt.getProvider(), true)
    }

    /**
     * Handles registered providers. If provider has a stored last status publish that status,
     * otherwise we just publish that they are Online/Available/
     *
     * @param pps the provider
     */
    private fun handleProviderRegistered(pps: ProtocolProviderService, dueToRegistrationStateChanged: Boolean) {
        var status = getLastPresenceStatus(pps)
        if (status == null) {
            // lets publish just online
            status = AccountStatusUtils.getOnlineStatus(pps)
        }
        if (status != null && status.status >= PresenceStatus.ONLINE_THRESHOLD) {
            publishStatusInternal(pps, status, dueToRegistrationStateChanged)
        }
    }

    /**
     * Publishes the given status to the given presence operation set.
     */
    private inner class PublishPresenceStatusThread
    /**
     * Publishes the given `status` through the given `presence` operation set.
     *
     * @param presence the operation set through which we publish the status
     * @param status the status to publish
     */
    (private val protocolProvider: ProtocolProviderService?,
            private val presence: OperationSetPresence?, private val status: PresenceStatus?) : Thread() {
        override fun run() {
            try {
                presence!!.publishPresenceStatus(status, "")
            } catch (e1: IllegalArgumentException) {
                Timber.e(e1, "Error - changing status")
            } catch (e1: IllegalStateException) {
                Timber.e(e1, "Error - changing status")
            } catch (e1: OperationFailedException) {
                if (e1.getErrorCode() == OperationFailedException.GENERAL_ERROR) {
                    val msgText = aTalkApp.getResString(R.string.service_gui_STATUS_CHANGE_GENERAL_ERROR,
                            protocolProvider!!.accountID.mUserID, protocolProvider.accountID.service)
                    GlobalDisplayDetailsActivator.alertUIService!!.showAlertDialog(
                            aTalkApp.getResString(R.string.service_gui_GENERAL_ERROR), msgText, e1)
                } else if (e1.getErrorCode() == OperationFailedException.NETWORK_FAILURE) {
                    val msgText = aTalkApp.getResString(R.string.service_gui_STATUS_CHANGE_NETWORK_FAILURE,
                            protocolProvider!!.accountID.mUserID, protocolProvider.accountID.service)
                    GlobalDisplayDetailsActivator.alertUIService!!.showAlertDialog(msgText,
                            aTalkApp.getResString(R.string.service_gui_NETWORK_FAILURE), e1)
                } else if (e1.getErrorCode() == OperationFailedException.PROVIDER_NOT_REGISTERED) {
                    val msgText = aTalkApp.getResString(R.string.service_gui_STATUS_CHANGE_NETWORK_FAILURE,
                            protocolProvider!!.accountID.mUserID, protocolProvider.accountID.service)
                    GlobalDisplayDetailsActivator.alertUIService!!.showAlertDialog(
                            aTalkApp.getResString(R.string.service_gui_NETWORK_FAILURE), msgText, e1)
                }
                Timber.e(e1, "Error - changing status")
            }
        }
    }
}