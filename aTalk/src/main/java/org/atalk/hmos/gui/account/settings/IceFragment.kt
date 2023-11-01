/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.*
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import net.java.sip.communicator.service.protocol.AccountID
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.settings.util.SummaryMapper
import org.atalk.service.osgi.OSGiPreferenceFragment
import timber.log.Timber

/**
 * The preferences fragment implements for ICE settings.
 *
 * @author Eng Chong Meng
 */
open class IceFragment : OSGiPreferenceFragment(), OnSharedPreferenceChangeListener {
    protected var mActivity: AccountPreferenceActivity? = null
    lateinit var shPrefs: SharedPreferences

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private val summaryMapper = SummaryMapper()

    /**
     * {@inheritDoc}
     */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.ice_preferences, rootKey)
        setPrefTitle(R.string.service_gui_JBR_ICE_SUMMARY)
        val accountID = arguments!!.getString(AccountPreferenceFragment.EXTRA_ACCOUNT_ID)
        val account = AccountUtils.getAccountIDForUID(accountID!!)
        val pps = AccountUtils.getRegisteredProviderForAccount(account)
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account)
            return
        }
        mActivity = activity as AccountPreferenceActivity?
        jbrReg = JabberPreferenceFragment.jbrReg
        shPrefs = preferenceManager.sharedPreferences!!
        shPrefs.registerOnSharedPreferenceChangeListener(this)
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper)
        findPreference<Preference>(P_KEY_STUN_TURN_SERVERS)!!.setOnPreferenceClickListener { pref ->
            stunServerList
            true
        }
        (findPreference(P_KEY_JINGLE_NODES_LIST) as Preference?)!!.setOnPreferenceClickListener { pref ->
            jingleNodeList
            true
        }
        initPreferences()
    }

    /**
     * {@inheritDoc}
     */
    private fun initPreferences() {
        // ICE options
        val editor = shPrefs.edit()
        editor.putBoolean(P_KEY_ICE_ENABLED, jbrReg.isUseIce())
        editor.putBoolean(P_KEY_UPNP_ENABLED, jbrReg.isUseUPNP())
        editor.putBoolean(P_KEY_AUTO_DISCOVER_STUN, jbrReg.isAutoDiscoverStun())

        // Jingle Nodes
        editor.putBoolean(P_KEY_USE_JINGLE_NODES, jbrReg.isUseJingleNodes())
        editor.putBoolean(P_KEY_AUTO_RELAY_DISCOVERY, jbrReg.isAutoDiscoverJingleNodes())
        editor.apply()
    }

    /**
     * Starts [ServerListActivity] in order to edit STUN servers list
     */
    private val stunServerList: Unit
        get() {
            val intent = Intent(mActivity, ServerListActivity::class.java)
            intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, jbrReg)
            intent.putExtra(ServerListActivity.REQUEST_CODE_KEY, ServerListActivity.RCODE_STUN_TURN)
            getStunServes.launch(intent)
        }

    /**
     * Stores values changed by STUN nodes edit activities.
     */
    private var getStunServes = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data!!

            // Gets edited STUN servers list
            val serialized = data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY) as JabberAccountRegistration
            jbrReg.additionalStunServers.clear()
            jbrReg.additionalStunServers.addAll(serialized.additionalStunServers)
            AccountPreferenceFragment.uncommittedChanges = true
        }
    }

    /**
     * Start [ServerListActivity] in order to edit Jingle Nodes list
     */
    private val jingleNodeList: Unit
        get() {
            val intent = Intent(mActivity, ServerListActivity::class.java)
            intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, jbrReg)
            intent.putExtra(ServerListActivity.REQUEST_CODE_KEY, ServerListActivity.RCODE_JINGLE_NODES)
            getJingleNodes.launch(intent)
        }

    /**
     * Stores values changed by Jingle nodes edit activities.
     */
    private var getJingleNodes = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Gets edited Jingle Nodes list
            val data = result.data!!
            val serialized = data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY) as JabberAccountRegistration
            jbrReg.additionalJingleNodes.clear()
            jbrReg.additionalJingleNodes.addAll(serialized.additionalJingleNodes)
            AccountPreferenceFragment.uncommittedChanges = true
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onSharedPreferenceChanged(shPreferences: SharedPreferences, key: String) {
        // Check to ensure a valid key before proceed
        if ((findPreference(key) as Preference?) == null) return
        AccountPreferenceFragment.uncommittedChanges = true
        when (key) {
            P_KEY_ICE_ENABLED -> {
                jbrReg.setUseIce(shPrefs.getBoolean(P_KEY_ICE_ENABLED, true))
            }
            P_KEY_UPNP_ENABLED -> {
                jbrReg.setUseUPNP(shPrefs.getBoolean(P_KEY_UPNP_ENABLED, true))
            }
            P_KEY_AUTO_DISCOVER_STUN -> {
                jbrReg.setAutoDiscoverStun(shPrefs.getBoolean(P_KEY_AUTO_DISCOVER_STUN, true))
            }
            P_KEY_USE_JINGLE_NODES -> {
                jbrReg.setUseJingleNodes(shPrefs.getBoolean(P_KEY_USE_JINGLE_NODES, true))
            }
            P_KEY_AUTO_RELAY_DISCOVERY -> {
                jbrReg.setAutoDiscoverJingleNodes(shPrefs.getBoolean(P_KEY_AUTO_RELAY_DISCOVERY, true))
            }
        }
    }

    companion object {
        // ICE (General)
        private const val P_KEY_ICE_ENABLED = "pref_key_ice_enabled"
        private const val P_KEY_UPNP_ENABLED = "pref_key_upnp_enabled"
        private const val P_KEY_AUTO_DISCOVER_STUN = "pref_key_auto_discover_stun"
        private const val P_KEY_STUN_TURN_SERVERS = "pref_key_stun_turn_servers"

        // Jingle Nodes
        private const val P_KEY_USE_JINGLE_NODES = "pref_key_use_jingle_nodes"
        private const val P_KEY_AUTO_RELAY_DISCOVERY = "pref_key_auto_relay_discovery"
        private const val P_KEY_JINGLE_NODES_LIST = "pref_key_jingle_node_list"

        /*
         * A new instance of AccountID and is not the same as accountID.
         * Defined as static, otherwise it may get clear onActivityResult - on some android devices
         */
        private lateinit var jbrReg: JabberAccountRegistration
    }
}