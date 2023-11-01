/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.notification

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.TextView
import net.java.sip.communicator.service.notification.NotificationChangeListener
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.resources.ResourceManagementService
import java.util.*

/**
 * The `Activity` lists all notification events. When user selects one of them the details screen is opened.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class NotificationSettings : OSGiActivity() {
    /**
     * Notifications adapter.
     */
    private lateinit var adapter: NotificationsAdapter

    /**
     * Notification service instance.
     */
    private lateinit var notificationService: NotificationService

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, NotificationService::class.java)!!
        setContentView(R.layout.list_layout)
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        // Refresh the list each time is displayed
        adapter = NotificationsAdapter()
        val listView = findViewById<ListView>(R.id.list)
        listView.adapter = adapter
        // And start listening for updates
        notificationService.addNotificationChangeListener(adapter)
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        super.onPause()
        // Do not listen for changes when paused
        notificationService.removeNotificationChangeListener(adapter)
    }

    /**
     * Adapter lists all notification events.
     */
    internal inner class NotificationsAdapter : BaseAdapter(), NotificationChangeListener {
        /**
         * List of event types
         */
        private val events = ArrayList<String>()

        /**
         * Map of events => eventType : eventName in ascending order by eventName
         */
        private val sortedEvents = TreeMap<String, String>()

        /**
         * Creates new instance of `NotificationsAdapter`;
         * Values are sorted in ascending order by eventNames for user easy reference.
         */
        init {
            val rms = UtilActivator.resources
            val unSortedMap = HashMap<String, String>()
            for (event in notificationService.registeredEvents) {
                unSortedMap[rms.getI18NString(NOTICE_PREFIX + event)!!] = event!!
            }

            // sort and save copies in sortedEvents and events
            val sortedMap = TreeMap(unSortedMap)
            for ((key, value) in sortedMap) {
                sortedEvents[value] = key
                events.add(value)
            }
        }

        override fun getCount(): Int {
            return events.size
        }

        /**
         * {@inheritDoc}
         */
        override fun getItem(position: Int): Any {
            return sortedEvents[events[position]]!!
        }

        /**
         * {@inheritDoc}
         */
        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        /**
         * {@inheritDoc}
         */
        override fun getView(position: Int, cView: View?, parent: ViewGroup): View {
            //if (rowView == null) cmeng would not update properly the status on enter/return
            val rowView = layoutInflater.inflate(R.layout.notification_item, parent, false)
            val eventType = events[position]
            rowView.setOnClickListener {
                val details = NotificationDetails.getIntent(this@NotificationSettings, eventType)
                startActivity(details)
            }
            val textView = rowView.findViewById<TextView>(R.id.descriptor)
            textView.text = getItem(position) as String
            val enableBtn = rowView.findViewById<CompoundButton>(R.id.enable)
            enableBtn.isChecked = notificationService.isActive(eventType)
            enableBtn.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean -> notificationService.setActive(eventType, isChecked) }
            return rowView
        }

        /**
         * {@inheritDoc}
         */
        override fun eventTypeAdded(event: NotificationEventTypeEvent) {
            runOnUiThread {
                events.add(event.getEventType()!!)
                notifyDataSetChanged()
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun eventTypeRemoved(event: NotificationEventTypeEvent) {
            runOnUiThread {
                events.remove(event.getEventType())
                notifyDataSetChanged()
            }
        }

        /**
         * {@inheritDoc}
         */
        override fun actionAdded(event: NotificationActionTypeEvent) {}

        /**
         * {@inheritDoc}
         */
        override fun actionRemoved(event: NotificationActionTypeEvent) {}

        /**
         * {@inheritDoc}
         */
        override fun actionChanged(event: NotificationActionTypeEvent) {}
    }

    companion object {
        const val NOTICE_PREFIX = "plugin.notificationconfig.event."
    }
}