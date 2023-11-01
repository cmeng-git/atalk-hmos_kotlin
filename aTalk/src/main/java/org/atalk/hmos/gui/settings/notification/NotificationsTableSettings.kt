/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.notification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.TableLayout
import android.widget.TableRow
import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationChangeListener
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.notification.PopupMessageNotificationAction
import net.java.sip.communicator.service.notification.SoundNotificationAction
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.UtilActivator
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.resources.ResourceManagementService

/**
 * Activity displays table of all notification events allowing user to change their settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class NotificationsTableSettings : OSGiActivity(), NotificationChangeListener {
    /**
     * The notification service
     */
    private lateinit var notificationService: NotificationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, NotificationService::class.java)!!
        notificationService.addNotificationChangeListener(this)
        buildTable()
    }

    /**
     * Builds the table of currently existing notification events.
     */
    private fun buildTable() {
        setContentView(R.layout.notifications_settings)
        val table = findViewById<TableLayout>(R.id.table_body)
        val inflater = layoutInflater
        val rms = UtilActivator.resources
        for (eventType in notificationService.registeredEvents) {
            val tableRow = inflater.inflate(R.layout.notification_row, table, false)
            ViewUtil.setCompoundChecked(tableRow, ENABLE_TAG, notificationService.isActive(eventType))

            // Popup
            val popupHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE)
            if (popupHandler != null) ViewUtil.setCompoundChecked(tableRow, POPUP_TAG, popupHandler.isEnabled)

            // Sound
            val soundHandler = notificationService.getEventNotificationAction(eventType,
                    NotificationAction.ACTION_SOUND) as SoundNotificationAction
            ViewUtil.setCompoundChecked(tableRow, SOUND_NOTIFY_TAG, soundHandler.isSoundNotificationEnabled)
            ViewUtil.setCompoundChecked(tableRow, SOUND_PLAYBACK_TAG, soundHandler.isSoundPlaybackEnabled)

            // Vibrate
            val vibrateHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE)
            if (vibrateHandler != null) ViewUtil.setCompoundChecked(tableRow, VIBRATE_TAG, vibrateHandler.isEnabled)

            // Description
            val desc = rms.getI18NString("plugin.notificationconfig.event.$eventType")
            ViewUtil.setTextViewValue(tableRow, DESCRIPTION_TAG, desc)

            // Event name as tag for the row
            tableRow.tag = eventType

            // Enable particular checkboxes
            ensureRowEnabled(tableRow.findViewWithTag<View>(ENABLE_TAG) as CompoundButton)

            // Add created row
            table.addView(tableRow)
        }
    }

    /**
     * Sets particular checkboxes enabled stated based on whole event enabled state and it's sub actions.
     *
     * @param enableColumnButton the button that enables whole event.
     */
    private fun ensureRowEnabled(enableColumnButton: CompoundButton) {
        val enable = enableColumnButton.isChecked
        val row = enableColumnButton.parent as TableRow
        // val eventType = row.tag as String

        // The popup
        // val popupHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE)
        ViewUtil.ensureEnabled(row, POPUP_TAG, enable)

        // The sound
        // val soundHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND)
        ViewUtil.ensureEnabled(row, SOUND_NOTIFY_TAG, enable)
        ViewUtil.ensureEnabled(row, SOUND_PLAYBACK_TAG, enable)

        // Vibrate action
        // val vibrateHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE)
        ViewUtil.ensureEnabled(row, VIBRATE_TAG, enable)

        // Description label
        ViewUtil.ensureEnabled(row, DESCRIPTION_TAG, enable)
    }

    /**
     * Fired when enable event toggle button is clicked
     *
     * @param v toggle button `View`
     */
    fun onEnableItemClicked(v: View) {
        val parent = v.parent as View
        val cb = v as CompoundButton
        val eventType = parent.tag as String
        notificationService.setActive(eventType, cb.isChecked)
        ensureRowEnabled(cb)
    }

    /**
     * Fired when popup checkbox is clicked
     *
     * @param v the popup checkbox
     */
    fun onPopupItemClicked(v: View) {
        val parent = v.parent as View
        val cb = v as CompoundButton
        val isPopup = cb.isChecked
        val eventType = parent.tag as String
        val popupAction = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE) as PopupMessageNotificationAction
        popupAction.isEnabled = isPopup
        notificationService.registerNotificationForEvent(eventType, popupAction)

        /*
         * if(isPopup) { notificationService.registerNotificationForEvent( eventType,
         * NotificationAction.ACTION_POPUP_MESSAGE, "", ""); } else {
         */
        // notificationService.removeEventNotificationAction(
        // eventType, NotificationAction.ACTION_POPUP_MESSAGE);
        // }
    }

    /**
     * Fired when sound notification checkbox is clicked
     *
     * @param v the sound notification checkbox
     */
    fun onSoundNotificationItemClicked(v: View) {
        val parent = v.parent as View
        val cb = v as CompoundButton
        val isSoundNotification = cb.isChecked
        val eventType = parent.tag as String
        val soundNotificationAction = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND) as SoundNotificationAction
        soundNotificationAction.isSoundNotificationEnabled = isSoundNotification
        notificationService.registerNotificationForEvent(eventType, soundNotificationAction)
    }

    /**
     * Fired when sound playback checkbox is clicked
     *
     * @param v sound playback checkbox
     */
    fun onSoundPlaybackItemClicked(v: View) {
        val parent = v.parent as View
        val cb = v as CompoundButton
        val isSoundPlayback = cb.isChecked
        val eventType = parent.tag as String
        val soundPlaybackAction = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND) as SoundNotificationAction
        soundPlaybackAction.isSoundPlaybackEnabled = isSoundPlayback
        notificationService.registerNotificationForEvent(eventType, soundPlaybackAction)
    }

    /**
     * Fired when vibrate checkbox is clicked
     *
     * @param v the vibrate checkbox
     */
    fun onVibrateItemClicked(v: View) {
        val parent = v.parent as View
        val cb = v as CompoundButton
        val isVibrate = cb.isChecked
        val eventType = parent.tag as String
        val vibrateAction = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE)!!
        vibrateAction.isEnabled = isVibrate
        notificationService.registerNotificationForEvent(eventType, vibrateAction)
    }

    /**
     * Rebuilds the whole table on UI thread
     */
    private fun rebuildTable() {
        runOnUiThread { buildTable() }
    }

    /**
     * {@inheritDoc}
     */
    override fun actionAdded(event: NotificationActionTypeEvent) {
        // It should not happen that often and will be much easier to rebuild the whole table from scratch
        rebuildTable()
    }

    /**
     * {@inheritDoc}
     */
    override fun actionRemoved(event: NotificationActionTypeEvent) {
        rebuildTable()
    }

    /**
     * {@inheritDoc}
     */
    override fun actionChanged(event: NotificationActionTypeEvent) {
        // rebuildTable();
    }

    /**
     * {@inheritDoc}
     */
    override fun eventTypeAdded(event: NotificationEventTypeEvent) {
        rebuildTable()
    }

    /**
     * {@inheritDoc}
     */
    override fun eventTypeRemoved(event: NotificationEventTypeEvent) {
        rebuildTable()
    }

    companion object {
        /**
         * Enable button's tag
         */
        private const val ENABLE_TAG = "enable"

        /**
         * Popup checkbox tag
         */
        private const val POPUP_TAG = "popup"

        /**
         * Notification sound checkbox tag
         */
        private const val SOUND_NOTIFY_TAG = "soundNotification"

        /**
         * Playback sound checkbox tag
         */
        private const val SOUND_PLAYBACK_TAG = "soundPlayback"

        /**
         * Vibrate checkbox tag
         */
        private const val VIBRATE_TAG = "vibrate"

        /**
         * Description label tag
         */
        private const val DESCRIPTION_TAG = "description"
    }
}