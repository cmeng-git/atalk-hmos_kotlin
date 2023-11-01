/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.sysactivity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import net.java.sip.communicator.service.sysactivity.event.SystemActivityEvent
import org.atalk.hmos.aTalkApp

/**
 * Listens for broadcasts from ConnectivityManager to get notified for network changes.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class ConnectivityManagerListenerImpl : BroadcastReceiver(), SystemActivityManager {
    /**
     * Whether the underlying implementation is currently connected and working.
     *
     * @return whether we are connected and working.
     */
    /**
     * Whether we are working.
     */
    override var isConnected = false
        private set

    /**
     * Starts
     */
    override fun start() {
        val context = aTalkApp.globalContext
        context.registerReceiver(this, IntentFilter(CONNECTIVITY_CHANGE_ACTION))
        isConnected = true
    }

    /**
     * Stops.
     */
    override fun stop() {
        val context = aTalkApp.globalContext
        context.unregisterReceiver(this)
        isConnected = false
    }

    /**
     * Receiving broadcast for network change.
     *
     * @param context the context.
     * @param intent the intent for the broadcast.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CONNECTIVITY_CHANGE_ACTION) {
            val evt = SystemActivityEvent(
                    SysActivityActivator.systemActivityService, SystemActivityEvent.EVENT_NETWORK_CHANGE)
            SysActivityActivator.systemActivityService!!.fireSystemActivityEvent(evt)
        }
    }

    companion object {
        /**
         * The action name we will receive broadcasts for to get informed
         * for connectivity changes.
         */
        private const val CONNECTIVITY_CHANGE_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"

        /**
         * The only instance of this impl.
         */
        private var connectivityManagerListenerImpl: ConnectivityManagerListenerImpl? = null

        /**
         * Gets the instance of `ConnectivityManagerListenerImpl`.
         *
         * @return the ConnectivityManagerListenerImpl.
         */
        val instance: ConnectivityManagerListenerImpl?
            get() {
                if (connectivityManagerListenerImpl == null) connectivityManagerListenerImpl = ConnectivityManagerListenerImpl()
                return connectivityManagerListenerImpl
            }
    }
}