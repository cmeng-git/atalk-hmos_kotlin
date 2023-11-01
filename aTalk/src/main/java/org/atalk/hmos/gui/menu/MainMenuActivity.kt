/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.gui.menu

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import net.java.sip.communicator.service.protocol.OperationSetVideoBridge
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum
import net.java.sip.communicator.util.ConfigurationUtils
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.account.AccountsListActivity
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.hmos.gui.call.telephony.TelephonyFragment
import org.atalk.hmos.gui.chat.conference.ConferenceCallInviteDialog
import org.atalk.hmos.gui.chatroomslist.ChatRoomBookmarksDialog
import org.atalk.hmos.gui.chatroomslist.ChatRoomCreateDialog
import org.atalk.hmos.gui.contactlist.AddContactActivity
import org.atalk.hmos.gui.contactlist.ContactListFragment
import org.atalk.hmos.gui.contactlist.model.MetaContactListAdapter
import org.atalk.hmos.gui.settings.SettingsActivity
import org.atalk.hmos.plugin.geolocation.GeoLocationActivity
import org.atalk.hmos.plugin.geolocation.GeoLocationBase
import org.atalk.hmos.plugin.textspeech.TTSActivity
import org.osgi.framework.ServiceEvent
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceReference

/**
 * The main options menu. Every `Activity` that desires to have the general options menu
 * shown have to extend this class.
 *
 * The `MainMenuActivity` is an `OSGiActivity`.
 *
 * @author Eng Chong Meng
 */
@SuppressLint("Registered")
open class MainMenuActivity : ExitMenuActivity(), ServiceListener, ContactPresenceStatusListener {
    /**
     * Common options menu items.
     */
    private lateinit var mShowHideOffline: MenuItem
    var menuItemOnOffLine: MenuItem? = null
    protected var mTelephony: TelephonyFragment? = null

    /**
     * Video bridge conference call menu. In the case of more than one account.
     */
    private val videoBridgeMenuItem: MenuItem? = null
    private var menuVbItem: VideoBridgeProviderMenuItem? = null
    var mContext: Context? = null
    /*
     * The {@link CallConference} instance depicted by this <code>CallPanel</code>.
     */
    // private final CallConference callConference = null;
    // private ProtocolProviderService preselectedProvider = null;
    // private List<ProtocolProviderService> videoBridgeProviders = null;
    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mContext = this
    }

    override fun onResume() {
        super.onResume()
        if (AndroidGUIActivator.bundleContext != null) {
            AndroidGUIActivator.bundleContext!!.addServiceListener(this)
            if (menuVbItem == null) {
                initVideoBridge()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // FFR v3.0.5: NullPointerException; may have stop() in AndroidGUIActivator
        if (AndroidGUIActivator.bundleContext != null) AndroidGUIActivator.bundleContext!!.removeServiceListener(this)
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)

        // Get the SearchView and set the search theme
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.search)
        val searchView = searchItem.actionView as SearchView?
        searchView!!.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        val textView = searchView.findViewById<TextView>(R.id.search_src_text)
        textView.setTextColor(resources.getColor(R.color.white, null))
        textView.setHintTextColor(resources.getColor(R.color.white, null))
        textView.setHint(R.string.service_gui_ENTER_NAME_OR_NUMBER)

        // cmeng: 20191220 <= disable videoBridge until implementation
        // this.videoBridgeMenuItem = menu.findItem(R.id.create_videobridge);
        /* Need this on first start up */
        // initVideoBridge();
        // videoBridgeMenuItem.setEnabled(true);
        mShowHideOffline = menu.findItem(R.id.show_hide_offline)
        var itemId = if (ConfigurationUtils.isShowOffline()) {
            R.string.service_gui_CONTACTS_OFFLINE_HIDE
        }
        else {
            R.string.service_gui_CONTACTS_OFFLINE_SHOW
        }

        mShowHideOffline.setTitle(itemId)
        menuItemOnOffLine = menu.findItem(R.id.sign_in_off)
        itemId = if (GlobalStatusEnum.OFFLINE_STATUS == ActionBarUtil.getStatus(this)) R.string.service_gui_SIGN_IN else R.string.service_gui_SIGN_OUT
        menuItemOnOffLine!!.setTitle(itemId)

        // Adds exit option from super class
        super.onCreateOptionsMenu(menu)
        return true
    }

    /**
     * Put initVideoBridge as separate task as it takes time to filtered server advertised
     * features/info (long list)
     * TODO: cmeng: Need more works for multiple accounts where not all servers support videoBridge
     */
    private fun initVideoBridgeTask() {
        val enableMenu: Boolean
        if (menuVbItem == null) menuVbItem = VideoBridgeProviderMenuItem()
        val videoBridgeProviders = videoBridgeProviders
        val videoBridgeProviderCount = videoBridgeProviders.size
        if (videoBridgeProviderCount >= 1) {
            enableMenu = true
            if (videoBridgeProviderCount == 1) {
                menuVbItem!!.setPreselectedProvider(videoBridgeProviders[0])
            } else {
                menuVbItem!!.setPreselectedProvider(null)
                menuVbItem!!.setVideoBridgeProviders(videoBridgeProviders)
            }
        } else enableMenu = false

        // runOnUiThread to update view
        this.runOnUiThread {

            // videoBridgeMenuItem is always enabled - allow user to re-trigger if earlier init failed
            videoBridgeMenuItem!!.isEnabled = true
            if (enableMenu) {
                videoBridgeMenuItem.icon!!.alpha = 255
            } else {
                videoBridgeMenuItem.icon!!.alpha = 80
                menuVbItem = null
            }
        }
    }

    /**
     * Progressing dialog to inform user while fetching xmpp server advertised features.
     * May takes time as some servers have many features & slow response.
     * Auto cancel after menu is displayed - end of fetching cycle
     */
    private fun initVideoBridge() {
        if (disableMediaServiceOnFault || videoBridgeMenuItem == null) return
        val progressDialog = if (!done) {
            ProgressDialog.show(this@MainMenuActivity,
                    getString(R.string.service_gui_WAITING),
                    getString(R.string.service_gui_SERVER_INFO_FETCH), true, true)
        } else {
            null
        }
        Thread {
            try {
                initVideoBridgeTask()
                Thread.sleep(100)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            if (progressDialog != null && progressDialog.isShowing) {
                done = true
                progressDialog.dismiss()
            }
        }.start()
    }

    /**
     * Invoked when an options item has been selected.
     *
     * @param item the item that has been selected
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.search -> {}
            R.id.add_chat_room -> {
                val chatRoomCreateDialog = ChatRoomCreateDialog(this)
                chatRoomCreateDialog.show()
            }
            R.id.create_videobridge -> if (menuVbItem == null) {
                initVideoBridge()
            }
            else menuVbItem!!.actionPerformed()
            R.id.show_location -> {
                val intent = Intent(this, GeoLocationActivity::class.java)
                intent.putExtra(GeoLocationBase.SHARE_ALLOW, false)
                startActivity(intent)
            }
            R.id.telephony -> {
                mTelephony = TelephonyFragment()
                supportFragmentManager.beginTransaction()
                        .replace(android.R.id.content, mTelephony!!, TelephonyFragment.TELEPHONY_TAG).commit()
            }
            R.id.muc_bookmarks -> {
                val chatRoomBookmarksDialog = ChatRoomBookmarksDialog(this)
                chatRoomBookmarksDialog.show()
            }
            R.id.add_contact -> startActivity(AddContactActivity::class.java)
            R.id.main_settings -> startActivity(SettingsActivity::class.java)
            R.id.account_settings -> startActivity(AccountsListActivity::class.java)
            R.id.tts_settings -> {
                val ttsIntent = Intent(this, TTSActivity::class.java)
                startActivity(ttsIntent)
            }
            R.id.show_hide_offline -> {
                val isShowOffline = !ConfigurationUtils.isShowOffline() // toggle
                MetaContactListAdapter.presenceFilter.setShowOffline(isShowOffline)
                val clf = aTalk.getFragment(aTalk.CL_FRAGMENT)
                if (clf is ContactListFragment) {
                    val contactListAdapter = clf.getContactListAdapter()
                    contactListAdapter.filterData("")
                }

                val itemId = if (isShowOffline) {
                    R.string.service_gui_CONTACTS_OFFLINE_HIDE
                }
                else {
                    R.string.service_gui_CONTACTS_OFFLINE_SHOW
                }
                mShowHideOffline.setTitle(itemId)
            }
            R.id.notification_setting -> openNotificationSettings()
            R.id.sign_in_off -> {
                // Toggle current account presence status
                val isOffline = GlobalStatusEnum.OFFLINE_STATUS == ActionBarUtil.getStatus(this)
                val globalStatusService = AndroidGUIActivator.globalStatusService
                if (isOffline) globalStatusService!!.publishStatus(GlobalStatusEnum.ONLINE)
                else globalStatusService!!.publishStatus(GlobalStatusEnum.OFFLINE)
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    //========================================================
    /**
     * The `VideoBridgeProviderMenuItem` for each protocol provider.
     */
    private inner class VideoBridgeProviderMenuItem
    /**
     * Creates an instance of `VideoBridgeProviderMenuItem`
     *
     * // @param preselectedProvider the `ProtocolProviderService` that provides the video bridge
     */
    {
        private var preselectedProvider: ProtocolProviderService? = null
        private var videoBridgeProviders: List<ProtocolProviderService>? = null

        /**
         * Opens a conference invite dialog when this menu is selected.
         */
        fun actionPerformed() {
            var inviteDialog: ConferenceCallInviteDialog? = null
            if (preselectedProvider != null) inviteDialog = ConferenceCallInviteDialog(mContext, preselectedProvider, true) else if (videoBridgeProviders != null) inviteDialog = ConferenceCallInviteDialog(mContext, videoBridgeProviders, true)
            inviteDialog?.show()
        }

        fun setPreselectedProvider(protocolProvider: ProtocolProviderService?) {
            preselectedProvider = protocolProvider
        }

        fun setVideoBridgeProviders(videoBridgeProviders: List<ProtocolProviderService>?) {
            this.videoBridgeProviders = videoBridgeProviders
        }
    }// Check if the video bridge is actually active before adding it to the list of active providers.

    /**
     * Returns a list of all available video bridge providers.
     *
     * @return a list of all available video bridge providers
     */
    private val videoBridgeProviders: List<ProtocolProviderService>
        get() {
            val activeBridgeProviders = ArrayList<ProtocolProviderService>()
            for (videoBridgeProvider in AccountUtils.getRegisteredProviders(OperationSetVideoBridge::class.java)) {
                val videoBridgeOpSet = videoBridgeProvider.getOperationSet(OperationSetVideoBridge::class.java) as OperationSetVideoBridge

                // Check if the video bridge is actually active before adding it to the list of active providers.
                if (videoBridgeOpSet.isActive()) activeBridgeProviders.add(videoBridgeProvider)
            }
            return activeBridgeProviders
        }

    /**
     * Implements the `ServiceListener` method. Verifies whether the passed event concerns
     * a `ProtocolProviderService` and adds the corresponding UI controls in the menu.
     *
     * @param event The `ServiceEvent` object.
     */
    override fun serviceChanged(event: ServiceEvent) {
        val serviceRef = event.serviceReference

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.bundle.state == org.osgi.framework.Bundle.STOPPING) {
            return
        }

        // we don't care if the source service is not a protocol provider
        val service = AndroidGUIActivator.bundleContext!!.getService<Any>(serviceRef as ServiceReference<Any>) as? ProtocolProviderService
                ?: return
        when (event.type) {
            ServiceEvent.REGISTERED, ServiceEvent.UNREGISTERING -> if (videoBridgeMenuItem != null) {
                uiHandler.post { initVideoBridge() }
            }
        }
    }

    override fun contactPresenceStatusChanged(evt: ContactPresenceStatusChangeEvent) {
        // cmeng - how to add the listener onResume - multiple protocol providers???
        uiHandler.post {
            val sourceContact = evt.getSourceContact()
            initVideoBridge()
        }
    }

    companion object {
        private var done = false
        var disableMediaServiceOnFault = false
    }
}