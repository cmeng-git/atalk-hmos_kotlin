/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.settings.notification

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import net.java.sip.communicator.plugin.notificationwiring.SoundProperties
import net.java.sip.communicator.service.notification.NotificationAction
import net.java.sip.communicator.service.notification.NotificationChangeListener
import net.java.sip.communicator.service.notification.NotificationService
import net.java.sip.communicator.service.notification.SoundNotificationAction
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent
import net.java.sip.communicator.util.ServiceUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.actionbar.ActionBarToggleFragment
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.impl.androidresources.AndroidResourceServiceImpl
import org.atalk.service.osgi.OSGiActivity
import org.atalk.service.resources.ResourceManagementService

/**
 * The screen that displays notification event details. It allows user to enable/disable the whole
 * event as well as adjust particular notification handlers like popups, sound or vibration.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class NotificationDetails : OSGiActivity(), NotificationChangeListener, ActionBarToggleFragment.ActionBarToggleModel {
    /**
     * The event type string that identifies the event
     */
    private var eventType: String? = null

    /**
     * Notification service instance
     */
    private lateinit var notificationService: NotificationService

    /**
     * Resource service instance
     */
    private lateinit var rms: ResourceManagementService

    /**
     * The description `View`
     */
    private lateinit var description: TextView

    /**
     * Popup handler checkbox `View`
     */
    private lateinit var popup: CompoundButton

    /**
     * Sound notification handler checkbox `View`
     */
    private lateinit var soundNotification: CompoundButton

    /**
     * Sound playback handler checkbox `View`
     */
    private lateinit var soundPlayback: CompoundButton

    /**
     * Vibrate handler checkbox `View`
     */
    private lateinit var vibrate: CompoundButton

    // Sound Descriptor variable
    private lateinit var mSoundDescriptor: Button

    private var eventTitle: String? = null
    private var ringToneTitle: String? = null
    private var soundDefaultUri: Uri? = null
    private var soundDescriptorUri: Uri? = null
    private var ringTone: Ringtone? = null
    private var soundHandler: SoundNotificationAction? = null

    /**
     * {@inheritDoc}
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventType = intent.getStringExtra(EVENT_TYPE_EXTRA)
        requireNotNull(eventType)
        notificationService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, NotificationService::class.java)!!
        rms = ServiceUtils.getService(AndroidGUIActivator.bundleContext, ResourceManagementService::class.java)!!
        setContentView(R.layout.notification_details)
        description = findViewById(R.id.description)
        popup = findViewById(R.id.popup)
        soundNotification = findViewById(R.id.soundNotification)
        soundPlayback = findViewById(R.id.soundPlayback)
        vibrate = findViewById(R.id.vibrate)
        val mPickRingTone = pickRingTone()
        mSoundDescriptor = findViewById(R.id.sound_descriptor)
        this.mSoundDescriptor.setOnClickListener {
            // set RingTone picker to show only the relevant notification or ringtone
            if (soundHandler!!.loopInterval < 0) mPickRingTone.launch(RingtoneManager.TYPE_NOTIFICATION) else mPickRingTone.launch(RingtoneManager.TYPE_RINGTONE)
        }

        // ActionBarUtil.setTitle(this, aTalkApp.getStringResourceByName(NotificationSettings.N_PREFIX + eventType));
        eventTitle = rms.getI18NString(NotificationSettings.NOTICE_PREFIX + eventType)
        ActionBarUtil.setTitle(this, eventTitle)

        // The SoundNotification init
        initSoundNotification()
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(ActionBarToggleFragment.newInstance(""),
                    "action_bar_toggle").commit()
        }
    }

    /**
     * Initialize all the sound Notification parameters on entry
     */
    private fun initSoundNotification() {
        soundHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND) as SoundNotificationAction
        if (soundHandler != null) {
            val soundFile = "android.resource://" + packageName + "/" + SoundProperties.getSoundDescriptor(eventType)
            soundDefaultUri = Uri.parse(soundFile)
            val descriptor = soundHandler!!.descriptor!!
            if (descriptor.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
                soundDescriptorUri = soundDefaultUri
                ringToneTitle = eventTitle
            } else {
                soundDescriptorUri = Uri.parse(descriptor)
                ringToneTitle = RingtoneManager.getRingtone(this, soundDescriptorUri).getTitle(this)
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onResume() {
        super.onResume()
        updateDisplay()
        notificationService.addNotificationChangeListener(this)
    }

    /**
     * {@inheritDoc}
     */
    override fun onPause() {
        super.onPause()
        notificationService.removeNotificationChangeListener(this)
        if (ringTone != null) {
            ringTone!!.stop()
            ringTone = null
        }
    }

    /**
     * {@inheritDoc}
     */
    private fun updateDisplay() {
        val enable = notificationService.isActive(eventType)

        // Description
        // description.setText(aTalkApp.getStringResourceByName(NotificationSettings.N_PREFIX + eventType + "_description"));
        description.text = rms.getI18NString(NotificationSettings.NOTICE_PREFIX + eventType + ".description")
        description.isEnabled = enable

        // The popup
        val popupHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE)
        popup.isEnabled = enable && popupHandler != null
        if (popupHandler != null) popup.isChecked = popupHandler.isEnabled
        soundNotification.isEnabled = enable && (soundHandler != null)
        soundPlayback.isEnabled = enable && (soundHandler != null)

        // if soundHandler is null then hide the sound file selection else init its attributes
        if (soundHandler != null) {
            soundNotification.isChecked = soundHandler!!.isSoundNotificationEnabled
            soundPlayback.isChecked = soundHandler!!.isSoundPlaybackEnabled
            mSoundDescriptor.text = ringToneTitle
        } else {
            findViewById<LinearLayout>(R.id.soundAttributes).visibility = View.GONE
        }

        // Vibrate action
        val vibrateHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE)
        vibrate.isEnabled = enable && (vibrateHandler != null)
        if (vibrateHandler != null) vibrate.isChecked = vibrateHandler.isEnabled
    }

    /**
     * Fired when popup checkbox is clicked.
     *
     * @param v popup checkbox `View`
     */
    fun onPopupClicked(v: View) {
        val enabled = (v as CompoundButton).isChecked
        val action = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE)!!
        action.isEnabled = enabled
        notificationService.registerNotificationForEvent(eventType, action)
    }

    /**
     * Fired when sound notification checkbox is clicked.
     *
     * @param v sound notification checkbox `View`
     */
    fun onSoundNotificationClicked(v: View) {
        val enabled = (v as CompoundButton).isChecked
        soundHandler!!.isSoundNotificationEnabled = enabled
        notificationService.registerNotificationForEvent(eventType, soundHandler)
    }

    /**
     * Fired when sound playback checkbox is clicked.
     *
     * @param v sound playback checkbox `View`
     */
    fun onSoundPlaybackClicked(v: View) {
        val enabled = (v as CompoundButton).isChecked
        soundHandler!!.isSoundPlaybackEnabled = enabled
        notificationService.registerNotificationForEvent(eventType, soundHandler)
    }

    /**
     * Fired when vibrate notification checkbox is clicked.
     *
     * @param v vibrate notification checkbox `View`
     */
    fun onVibrateClicked(v: View) {
        val enabled = (v as CompoundButton).isChecked
        val action = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE)!!
        action.isEnabled = enabled
        notificationService.registerNotificationForEvent(eventType, action)
    }

    /**
     * Toggle play mode for the ringtone when user clicks the play/pause button;
     *
     * @param v playback view
     */
    fun onPlayBackClicked(v: View?) {
        if (ringTone == null) {
            ringTone = RingtoneManager.getRingtone(this, soundDescriptorUri)
        }
        if (ringTone!!.isPlaying) {
            ringTone!!.stop()
            ringTone = null
        } else ringTone!!.play()
    }

    /**
     * PIckRingtone class ActivityResultContract implementation, with ringtoneType of either:
     * 1. RingtoneManager.TYPE_NOTIFICATION
     * 2. RingtoneManager.TYPE_RINGTONE
     */
    inner class PickRingtone : ActivityResultContract<Int, Uri?>() {
        override fun createIntent(context: Context, input: Int): Intent {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, eventTitle)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, input)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, soundDefaultUri)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundDescriptorUri)
            return intent
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode != Activity.RESULT_OK || intent == null) {
                null
            } else intent.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
    }

    /**
     * Opens a FileChooserDialog to let the user pick attachments
     */
    private fun pickRingTone(): ActivityResultLauncher<Int?> {
        return registerForActivityResult(PickRingtone()) { toneUri ->
            var ringToneUri = toneUri
            if (ringToneUri == null) {
                ringToneUri = soundDefaultUri
            }
            updateSoundNotification(ringToneUri!!)
        }
    }


    /**
     * Update the display and setup the SoundNotification with the newly user selected ringTone
     *
     * @param ringToneUri user selected ringtone Uri
     */
    private fun updateSoundNotification(ringToneUri: Uri) {
        val soundDescriptor: String
        if (soundDefaultUri == ringToneUri) {
            ringToneTitle = eventTitle
            soundDescriptor = SoundProperties.getSoundDescriptor(eventType)!!
        } else {
            ringTone = RingtoneManager.getRingtone(this, ringToneUri)
            ringToneTitle = ringTone!!.getTitle(this)
            soundDescriptor = ringToneUri.toString()
        }
        soundDescriptorUri = ringToneUri
        soundHandler!!.descriptor = soundDescriptor
        notificationService.registerNotificationForEvent(eventType, soundHandler)
    }

    /**
     * {@inheritDoc}
     */
    override fun actionAdded(event: NotificationActionTypeEvent) {
        handleActionEvent(event)
    }

    /**
     * {@inheritDoc}
     */
    override fun actionRemoved(event: NotificationActionTypeEvent) {
        handleActionEvent(event)
    }

    /**
     * {@inheritDoc}
     */
    override fun actionChanged(event: NotificationActionTypeEvent) {
        handleActionEvent(event)
    }

    /**
     * Handles add/changed/removed notification action events by refreshing the display if the event
     * is related with the one currently displayed.
     *
     * @param event the event object
     */
    private fun handleActionEvent(event: NotificationActionTypeEvent) {
        if (event.getEventType() == eventType) {
            runOnUiThread { updateDisplay() }
        }
    }

    /**
     * {@inheritDoc} Not interested in type added event.
     */
    override fun eventTypeAdded(event: NotificationEventTypeEvent) {}

    /**
     * {@inheritDoc}
     *
     * If removed event is the one currently displayed, closes the `Activity`.
     */
    override fun eventTypeRemoved(event: NotificationEventTypeEvent) {
        if (event.getEventType() != eventType) return

        // Event no longer exists
        runOnUiThread(this::finish)
    }
    /**
     * {@inheritDoc}
     */
    /**
     * {@inheritDoc}
     */
    override var isChecked: Boolean
        get() = notificationService.isActive(eventType)
        set(isChecked) {
            notificationService.setActive(eventType, isChecked)
            updateDisplay()
        }

    companion object {
        /**
         * Event type extra key
         */
        private const val EVENT_TYPE_EXTRA = "event_type"

        /**
         * Gets the `Intent` for starting `NotificationDetails` `Activity`.
         *
         * @param ctx the context
         * @param eventType name of the event that will be displayed by `NotificationDetails`.
         * @return the `Intent` for starting `NotificationDetails` `Activity`.
         */
        fun getIntent(ctx: Context?, eventType: String?): Intent {
            val intent = Intent(ctx, NotificationDetails::class.java)
            intent.putExtra(EVENT_TYPE_EXTRA, eventType)
            return intent
        }
    }
}