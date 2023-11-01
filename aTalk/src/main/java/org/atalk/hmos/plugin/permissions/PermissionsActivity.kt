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
package org.atalk.hmos.plugin.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.*
import com.karumi.dexter.listener.multi.CompositeMultiplePermissionsListener
import com.karumi.dexter.listener.multi.DialogOnAnyDeniedMultiplePermissionsListener
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.multi.SnackbarOnAnyDeniedMultiplePermissionsListener
import com.karumi.dexter.listener.single.CompositePermissionListener
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener
import com.karumi.dexter.listener.single.PermissionListener
import org.atalk.hmos.BaseActivity
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.Splash
import org.atalk.service.SystemEventReceiver
import timber.log.Timber
import java.util.*

/**
 * Sample activity showing the permission request process with Dexter.
 */
class PermissionsActivity : BaseActivity() {
    @BindView(R.id.audio_permission_feedback)
    lateinit var audioPermissionFeedbackView: TextView

    @BindView(R.id.camera_permission_feedback)
    lateinit var cameraPermissionFeedbackView: TextView

    @BindView(R.id.contacts_permission_feedback)
    lateinit var contactsPermissionFeedbackView: TextView

    @BindView(R.id.location_permission_feedback)
    lateinit var locationPermissionFeedbackView: TextView

    @BindView(R.id.notifications_permission_feedback)
    lateinit var notificationsPermissionFeedbackView: TextView

    @BindView(R.id.phone_permission_feedback)
    lateinit var phonePermissionFeedbackView: TextView

    @BindView(R.id.storage_permission_feedback)
    lateinit var storagePermissionFeedbackView: TextView

    @BindView(R.id.storage_permission_button)
    lateinit var buttonStorage: Button

    @BindView(R.id.notifications_permission_button)
    lateinit var buttonNotifications: Button

    @BindView(android.R.id.content)
    lateinit var mContentView: View

    private var allPermissionsListener: MultiplePermissionsListener? = null
    private var dialogMultiplePermissionsListener: MultiplePermissionsListener? = null
    private var cameraPermissionListener: PermissionListener? = null
    private var contactsPermissionListener: PermissionListener? = null
    private var locationPermissionListener: PermissionListener? = null
    private var audioPermissionListener: PermissionListener? = null
    private var phonePermissionListener: PermissionListener? = null
    private var storagePermissionListener: PermissionListener? = null
    private var notificationsPermissionListener: PermissionListener? = null
    private var errorListener: PermissionRequestErrorListener? = null
    private var mBatteryOptimization: ActivityResultLauncher<Void?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Always request permission on first apk launch for android.M
        if (aTalkApp.permissionFirstRequest) {

            // see if we should show the splash screen and wait for it to complete before continue
            if (Splash.isFirstRun) {
                val intent = Intent(this, Splash::class.java)
                startActivity(intent)
            }
            setContentView(R.layout.permissions_activity)

            Timber.i("Launching dynamic permission request for aTalk.")
            aTalkApp.permissionFirstRequest = false

            // Request user to add aTalk to BatteryOptimization whitelist
            // Otherwise, aTalk will be put to sleep on system doze-standby
            mBatteryOptimization = requestBatteryOptimization()
            val showBatteryOptimizationDialog = openBatteryOptimizationDialogIfNeeded()
            ButterKnife.bind(this)
            createPermissionListeners()
            val permissionRequest = packagePermissionsStatus
            permissionsStatusUpdate()
            if (!permissionRequest && !showBatteryOptimizationDialog) {
                startLauncher()
            }

            // POST_NOTIFICATIONS is only valid for API-33 (TIRAMISU)
            if (permissionList.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationsPermissionFeedbackView.visibility = View.VISIBLE
                buttonNotifications.visibility = View.VISIBLE
            } else {
                notificationsPermissionFeedbackView.visibility = View.GONE
                buttonNotifications.visibility = View.GONE
            }

            // handler for WRITE_EXTERNAL_STORAGE pending android API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                storagePermissionFeedbackView.setText(R.string.permission_granted_feedback)
                buttonStorage.isEnabled = false
            } else {
                buttonStorage.isEnabled = true
            }
        } else {
            startLauncher()
        }
    }

    private fun startLauncher() {
        val activityClass = aTalkApp.homeScreenActivityClass
        val i = Intent(this, activityClass)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.putExtra(SystemEventReceiver.AUTO_START_ONBOOT, false)
        startActivity(i)
        finish()
    }

    @OnClick(R.id.button_done)
    fun onDoneButtonClicked() {
        startLauncher()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        startLauncher()
    }

    fun onAllPermissionsCheck() {
        Dexter.withContext(this)
                .withPermissions(permissionList)
                .withListener(allPermissionsListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.all_permissions_button)
    fun onAllPermissionsButtonClicked() {
        grantedPermissionResponses.clear()
        deniedPermissionResponses.clear()
        Dexter.withContext(this)
                .withPermissions(permissionList)
                .withListener(dialogMultiplePermissionsListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.audio_permission_button)
    fun onAudioPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.RECORD_AUDIO)
                .withListener(audioPermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.camera_permission_button)
    fun onCameraPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.CAMERA)
                .withListener(cameraPermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.contacts_permission_button)
    fun onContactsPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_CONTACTS)
                .withListener(contactsPermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.location_permission_button)
    fun onLocationPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(locationPermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @OnClick(R.id.notifications_permission_button)
    fun onNotificationsPermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.POST_NOTIFICATIONS)
                .withListener(notificationsPermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.phone_permission_button)
    fun onPhonePermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_PHONE_STATE)
                .withListener(phonePermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.storage_permission_button)
    fun onStoragePermissionButtonClicked() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(storagePermissionListener)
                .withErrorListener(errorListener)
                .check()
    }

    @OnClick(R.id.app_info_permissions_button)
    fun onInfoButtonClicked() {
        val myAppSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + this.packageName))
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(myAppSettings)
    }

    fun showPermissionRationale(token: PermissionToken) {
        AlertDialog.Builder(this).setTitle(R.string.permission_rationale_title)
                .setMessage(R.string.permission_rationale_message)
                .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    token.cancelPermissionRequest()
                }
                .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    token.continuePermissionRequest()
                }
                .setOnDismissListener { dialog: DialogInterface? -> token.cancelPermissionRequest() }
                .show()
    }

    /**
     * Retrieve the package current default permissions status on create;
     * only if both the arrays are empty. Non-empty -> orientation change
     */
    private val packagePermissionsStatus: Boolean
        get() {
            if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses.isEmpty()) {
                val pm = packageManager
                try {
                    val packageInfo = pm.getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)

                    // Get Permissions
                    val requestedPermissions = packageInfo.requestedPermissions
                    if (requestedPermissions != null) {
                        for (requestedPermission in requestedPermissions) {
                            if (getFeedbackViewForPermission(requestedPermission) == null) continue

                            val pr = PermissionRequest(requestedPermission)
                            // denied
                            if (ActivityCompat.shouldShowRequestPermissionRationale(this, requestedPermission)) {
                                deniedPermissionResponses.add(PermissionDeniedResponse(pr, false))
                            } else {
                                // allowed
                                if (ActivityCompat.checkSelfPermission(this,
                                                requestedPermission) == PackageManager.PERMISSION_GRANTED) {
                                    grantedPermissionResponses.add(PermissionGrantedResponse(pr))
                                } else {
                                    deniedPermissionResponses.add(PermissionDeniedResponse(pr, true))
                                }
                            }
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }
            }
            // Proceed to request user for permissions if not all are permanently denied
            for (response in deniedPermissionResponses) {
                if (!response.isPermanentlyDenied) return true
            }
            /*
              * It seems that some android devices have init all requested permissions to permanently denied states
              * i.e. incorrect return value for: ActivityCompat.shouldShowRequestPermissionRationale == false
              * Must prompt user if < 3 permission has been granted to aTalk - will not work in almost cases;
              *
              * Do not disturb user, if he has chosen partially granted the permissions.
              */
            return grantedPermissionResponses.size < 3
        }

    /**
     * Update the permissions status with the default application permissions on entry
     */
    private fun permissionsStatusUpdate() {
        // if (grantedPermissionResponses.isEmpty() && deniedPermissionResponses
        for (response in grantedPermissionResponses) {
            showPermissionGranted(response.permissionName)
        }
        for (response in deniedPermissionResponses) {
            showPermissionDenied(response.permissionName, response.isPermanentlyDenied)
        }
    }

    /**
     * Update the granted permissions for the package
     *
     * @param permission permission view to be updated
     */
    fun showPermissionGranted(permission: String) {
        val feedbackView = getFeedbackViewForPermission(permission)
        if (feedbackView != null) {
            feedbackView.setText(R.string.permission_granted_feedback)
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_granted))
        }
    }

    /**
     * Update the denied permissions for the package
     *
     * @param permission permission view to be updated
     */
    fun showPermissionDenied(permission: String, isPermanentlyDenied: Boolean) {
        val feedbackView = getFeedbackViewForPermission(permission)
        if (feedbackView != null) {
            feedbackView.setText(if (isPermanentlyDenied) R.string.permission_permanently_denied_feedback else R.string.permission_denied_feedback)
            feedbackView.setTextColor(ContextCompat.getColor(this, R.color.permission_denied))
        }
    }

    /**
     * Initialize all the permission listener required actions
     */
    private fun createPermissionListeners() {
        var dialogOnDeniedPermissionListener: PermissionListener?
        val feedbackViewPermissionListener = AppPermissionListener(this)
        val feedbackViewMultiplePermissionListener = MultiplePermissionListener(this)
        allPermissionsListener = CompositeMultiplePermissionsListener(feedbackViewMultiplePermissionListener,
                SnackbarOnAnyDeniedMultiplePermissionsListener.Builder
                        .with(mContentView, R.string.all_permissions_denied_feedback)
                        .withOpenSettingsButton(R.string.permission_rationale_settings_button_text)
                        .build())

        val dialogOnAnyDeniedPermissionListener = DialogOnAnyDeniedMultiplePermissionsListener.Builder
                .withContext(this)
                .withTitle(R.string.all_permission_denied_dialog_title)
                .withMessage(R.string.all_permissions_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        dialogMultiplePermissionsListener = CompositeMultiplePermissionsListener(
                feedbackViewMultiplePermissionListener, dialogOnAnyDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.audio_permission_denied_dialog_title)
                .withMessage(R.string.audio_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        audioPermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.camera_permission_denied_dialog_title)
                .withMessage(R.string.camera_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        cameraPermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.contacts_permission_denied_dialog_title)
                .withMessage(R.string.contacts_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        contactsPermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.location_permission_denied_dialog_title)
                .withMessage(R.string.location_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        locationPermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.notifications_permission_denied_dialog_title)
                .withMessage(R.string.notifications_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        notificationsPermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.phone_permission_denied_dialog_title)
                .withMessage(R.string.phone_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        phonePermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        dialogOnDeniedPermissionListener = DialogOnDeniedPermissionListener.Builder
                .withContext(this)
                .withTitle(R.string.storage_permission_denied_dialog_title)
                .withMessage(R.string.storage_permission_denied_feedback)
                .withButtonText(android.R.string.ok)
                .withIcon(R.drawable.ic_icon)
                .build()
        storagePermissionListener = CompositePermissionListener(feedbackViewPermissionListener,
                dialogOnDeniedPermissionListener)

        errorListener = PermissionsErrorListener()
    }

    /**
     * Get the view of the permission for update, null if view does not exist
     *
     * @param name permission name
     *
     * @return the textView for the request permission
     */
    private fun getFeedbackViewForPermission(name: String): TextView? {
        val feedbackView = when (name) {
            Manifest.permission.ACCESS_FINE_LOCATION -> locationPermissionFeedbackView
            Manifest.permission.CAMERA -> cameraPermissionFeedbackView
            Manifest.permission.POST_NOTIFICATIONS -> notificationsPermissionFeedbackView
            Manifest.permission.READ_CONTACTS -> contactsPermissionFeedbackView
            Manifest.permission.READ_PHONE_STATE -> phonePermissionFeedbackView
            Manifest.permission.RECORD_AUDIO -> audioPermissionFeedbackView
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> storagePermissionFeedbackView
            else -> null
        }
        return feedbackView
    }

    /**********************************************************************************************
     * Android Battery Usage Optimization Request; Will only be called if >= Build.VERSION_CODES.M
     */
    private fun openBatteryOptimizationDialogIfNeeded(): Boolean {
        // Will always request for battery optimization disable for aTalk on every aTalk new launch, if not disabled
        return if (isOptimizingBattery) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.battery_optimizations)
            builder.setMessage(R.string.battery_optimizations_dialog)

            builder.setPositiveButton(R.string.next) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                mBatteryOptimization!!.launch(null)
            }

            val dialog = builder.create()
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
            true
        } else {
            false
        }
    }

    private val isOptimizingBattery: Boolean
        get() {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            return !pm.isIgnoringBatteryOptimizations(packageName)
        }

    /**
     * GetBatteryOptimization class ActivityResultContract implementation.
     */
    @SuppressLint("BatteryLife")
    inner class GetBatteryOptimization : ActivityResultContract<Void?, Boolean>() {
        override fun createIntent(context: Context, input: Void?): Intent {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            val uri = Uri.parse("package:$packageName")
            intent.data = uri
            return intent
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
            return resultCode == RESULT_OK
        }
    }

    /**
     * Return success == true if disable battery optimization for aTalk is allowed
     */
    private fun requestBatteryOptimization(): ActivityResultLauncher<Void?> {
        return registerForActivityResult(GetBatteryOptimization()) { success: Boolean? ->
            if (!success!!) {
                aTalkApp.showToastMessage(R.string.battery_optimization_on)
            }
        }
    }

    companion object {
        var grantedPermissionResponses = mutableListOf<PermissionGrantedResponse>()
        var deniedPermissionResponses = mutableListOf<PermissionDeniedResponse>()
        private var permissionList = mutableListOf<String>()

        init {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionList.add(Manifest.permission.CAMERA)
            permissionList.add(Manifest.permission.READ_CONTACTS)
            permissionList.add(Manifest.permission.READ_PHONE_STATE)
            permissionList.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionList.add(Manifest.permission.POST_NOTIFICATIONS)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}