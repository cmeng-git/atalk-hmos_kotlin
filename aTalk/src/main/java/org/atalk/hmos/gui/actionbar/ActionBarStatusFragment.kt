/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.actionbar

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import net.java.sip.communicator.service.globaldisplaydetails.GlobalDisplayDetailsService
import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalAvatarChangeEvent
import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalDisplayDetailsListener
import net.java.sip.communicator.service.globaldisplaydetails.event.GlobalDisplayNameChangeEvent
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.StatusUtil
import net.java.sip.communicator.util.account.AccountUtils
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.account.AndroidLoginRenderer
import org.atalk.hmos.gui.menu.GlobalStatusMenu
import org.atalk.hmos.gui.menu.GlobalStatusMenu.OnActionItemClickListener
import org.atalk.hmos.gui.menu.MainMenuActivity
import org.atalk.hmos.gui.util.event.EventListener
import org.atalk.hmos.gui.widgets.ActionMenuItem
import org.atalk.service.osgi.OSGiFragment

/**
 * Fragment when added to Activity will display global display details like avatar, display name
 * and status. External events will also trigger a change to the contents.
 * When status is clicked a popup menu is displayed allowing user to set global presence status.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class ActionBarStatusFragment : OSGiFragment(), EventListener<PresenceStatus?>, GlobalDisplayDetailsListener {
    /**
     * The global status menu.
     */
    private lateinit var globalStatusMenu: GlobalStatusMenu
    private lateinit var mContext: AppCompatActivity

    /**
     * {@inheritDoc}
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context as AppCompatActivity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayDetailsService = AndroidGUIActivator.globalDisplayDetailsService
        globalStatusMenu = createGlobalStatusMenu()
        val actionBarView = mContext.findViewById<View>(R.id.actionBarView)
        actionBarView?.setOnClickListener { v: View? ->
            globalStatusMenu.show(actionBarView)
            globalStatusMenu.setAnimStyle(GlobalStatusMenu.ANIM_REFLECT)
        }
    }

    override fun onResume() {
        super.onResume()
        loginRenderer = AndroidGUIActivator.loginRenderer
        loginRenderer?.addGlobalStatusListener(this)
        onChangeEvent(loginRenderer?.getGlobalStatus())
        displayDetailsService?.addGlobalDisplayDetailsListener(this)
        setGlobalAvatar(displayDetailsService?.getDisplayAvatar(null))
        setGlobalDisplayName(displayDetailsService?.getDisplayName(null)!!)
    }

    override fun onPause() {
        super.onPause()
        loginRenderer?.removeGlobalStatusListener(this)
        displayDetailsService?.removeGlobalDisplayDetailsListener(this)
    }

    /**
     * Creates the `GlobalStatusMenu`.
     *
     * @return the newly created `GlobalStatusMenu`
     */
    private fun createGlobalStatusMenu(): GlobalStatusMenu {
        val ffcItem = ActionMenuItem(FFC,
            resources.getString(R.string.service_gui_FFC_STATUS),
            ResourcesCompat.getDrawable(resources, R.drawable.global_ffc, null)!!)
        val onlineItem = ActionMenuItem(ONLINE,
            resources.getString(R.string.service_gui_ONLINE),
            ResourcesCompat.getDrawable(resources, R.drawable.global_online, null)!!)
        val offlineItem = ActionMenuItem(OFFLINE,
            resources.getString(R.string.service_gui_OFFLINE),
            ResourcesCompat.getDrawable(resources, R.drawable.global_offline, null)!!)
        val awayItem = ActionMenuItem(AWAY,
            resources.getString(R.string.service_gui_AWAY_STATUS),
            ResourcesCompat.getDrawable(resources, R.drawable.global_away, null)!!)
        val extendedAwayItem = ActionMenuItem(EXTENDED_AWAY,
            resources.getString(R.string.service_gui_EXTENDED_AWAY_STATUS),
            ResourcesCompat.getDrawable(resources, R.drawable.global_extended_away, null)!!)
        val dndItem = ActionMenuItem(DND,
            resources.getString(R.string.service_gui_DND_STATUS),
            ResourcesCompat.getDrawable(resources, R.drawable.global_dnd, null)!!)
        val globalStatusMenu = GlobalStatusMenu(mContext)
        globalStatusMenu.addActionItem(ffcItem)
        globalStatusMenu.addActionItem(onlineItem)
        globalStatusMenu.addActionItem(offlineItem)
        globalStatusMenu.addActionItem(awayItem)
        globalStatusMenu.addActionItem(extendedAwayItem)
        globalStatusMenu.addActionItem(dndItem)

        // Add all registered PPS users to the presence status menu
        val registeredProviders = AccountUtils.registeredProviders
        for (pps in registeredProviders) {
            val accountId = pps.accountID
            val userJid = accountId.accountJid
            val icon = ResourcesCompat.getDrawable(resources, R.drawable.jabber_status_online, null)!!
            val actionItem = ActionMenuItem(ACTION_ID++, userJid, icon)
            globalStatusMenu.addActionItem(actionItem, pps)
        }

        globalStatusMenu.setOnActionItemClickListener(object : OnActionItemClickListener {
            override fun onItemClick(source: GlobalStatusMenu?, pos: Int, actionId: Int) {
                if (actionId <= DND) publishGlobalStatus(actionId)
            }
        })

        globalStatusMenu.setOnDismissListener(object : GlobalStatusMenu.OnDismissListener {
            override fun onDismiss() {
                // TODO: Add a dismiss action.
            }
        })
        return globalStatusMenu
    }

    /**
     * Publishes global status on separate thread to prevent `NetworkOnMainThreadException`.
     *
     * @param newStatus new global status to set.
     */
    private fun publishGlobalStatus(newStatus: Int) {
        /*
         * Runs publish status on separate thread to prevent NetworkOnMainThreadException
         */
        Thread {
            val globalStatusService = AndroidGUIActivator.globalStatusService!!
            when (newStatus) {
                FFC -> globalStatusService.publishStatus(GlobalStatusEnum.FREE_FOR_CHAT)
                ONLINE -> globalStatusService.publishStatus(GlobalStatusEnum.ONLINE)
                OFFLINE -> globalStatusService.publishStatus(GlobalStatusEnum.OFFLINE)
                AWAY -> globalStatusService.publishStatus(GlobalStatusEnum.AWAY)
                EXTENDED_AWAY -> globalStatusService.publishStatus(GlobalStatusEnum.EXTENDED_AWAY)
                DND -> globalStatusService.publishStatus(GlobalStatusEnum.DO_NOT_DISTURB)
            }
        }.start()
    }

    override fun onChangeEvent(eventObject: PresenceStatus?) {
        if (eventObject == null) return

        runOnUiThread {
            val mStatus = eventObject.statusName
            ActionBarUtil.setSubtitle(mContext, mStatus)
            ActionBarUtil.setStatusIcon(mContext, StatusUtil.getStatusIcon(eventObject))
            val mOnOffLine = (mContext as MainMenuActivity).menuItemOnOffLine
            // Proceed only if mOnOffLine has been initialized
            if (mOnOffLine != null) {
                val isOffline = GlobalStatusEnum.OFFLINE_STATUS == mStatus
                val itemId = if (isOffline) R.string.service_gui_SIGN_IN else R.string.service_gui_SIGN_OUT
                mOnOffLine.setTitle(itemId)
            }
        }
    }

    /**
     * Indicates that the global avatar has been changed.
     */
    override fun globalDisplayAvatarChanged(evt: GlobalAvatarChangeEvent) {
        runOnUiThread { setGlobalAvatar(evt.getNewAvatar()) }
    }

    /**
     * Indicates that the global display name has been changed.
     */
    override fun globalDisplayNameChanged(evt: GlobalDisplayNameChangeEvent) {
        runOnUiThread { setGlobalDisplayName(evt.getNewDisplayName()) }
    }

    /**
     * Sets the global avatar in the action bar.
     *
     * @param avatar the byte array representing the avatar to set
     */
    private fun setGlobalAvatar(avatar: ByteArray?) {
        if (avatar != null && avatar.isNotEmpty()) {
            ActionBarUtil.setAvatar(mContext, avatar)
        }
        else {
            ActionBarUtil.setAvatar(mContext, R.drawable.ic_icon)
        }
    }

    /**
     * Sets the global display name in the action bar as 'Me' if multiple accounts are involved, otherwise UserJid.
     *
     * @param name the display name to set
     */
    private fun setGlobalDisplayName(name: String?) {
        var displayName = name
        val pProviders = AccountUtils.registeredProviders
        if (StringUtils.isEmpty(displayName) && pProviders.size == 1) {
            displayName = pProviders.iterator().next().accountID.mUserID
        }
        if (pProviders.size > 1) displayName = getString(R.string.service_gui_ACCOUNT_ME)
        ActionBarUtil.setTitle(mContext, displayName)
    }

    companion object {
        /**
         * The online status.
         */
        private const val ONLINE = 1

        /**
         * The offline status.
         */
        private const val OFFLINE = 2

        /**
         * The free for chat status.
         */
        private const val FFC = 3

        /**
         * The away status.
         */
        private const val AWAY = 4

        /**
         * The away status.
         */
        private const val EXTENDED_AWAY = 5

        /**
         * The do not disturb status.
         */
        private const val DND = 6
        private var ACTION_ID = DND + 1
        private var displayDetailsService: GlobalDisplayDetailsService? = null
        private var loginRenderer: AndroidLoginRenderer? = null
    }
}