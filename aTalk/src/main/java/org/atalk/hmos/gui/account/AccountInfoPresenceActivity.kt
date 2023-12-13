/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.hmos.gui.account

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment
import com.codetroopers.betterpickers.calendardatepicker.MonthAdapter.CalendarDay
import com.yalantis.ucrop.UCrop
import net.java.sip.communicator.service.protocol.OperationFailedException
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetServerStoredAccountInfo
import net.java.sip.communicator.service.protocol.PresenceStatus
import net.java.sip.communicator.service.protocol.ProtocolProviderService
import net.java.sip.communicator.service.protocol.ServerStoredDetails.AboutMeDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.AddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.BirthDateDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.CityDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.CountryDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.DisplayNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.EmailAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.FirstNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenderDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ImageDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.JobTitleDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.LastNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MiddleNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.MobilePhoneDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.NicknameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PhoneNumberDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.PostalCodeDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.ProvinceDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.URLDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkEmailAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkOrganizationNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.WorkPhoneDetail
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService
import net.java.sip.communicator.util.ServiceUtils
import net.java.sip.communicator.util.account.AccountUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.AndroidGUIActivator
import org.atalk.hmos.gui.account.AccountDeleteDialog.OnAccountRemovedListener
import org.atalk.hmos.gui.account.AccountDeleteDialog.create
import org.atalk.hmos.gui.account.settings.AccountPreferenceActivity
import org.atalk.hmos.gui.actionbar.ActionBarUtil
import org.atalk.hmos.gui.contactlist.ContactInfoActivity
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.hmos.gui.util.AndroidImageUtil
import org.atalk.hmos.gui.util.ViewUtil
import org.atalk.hmos.gui.util.event.EventListener
import org.atalk.service.osgi.OSGiActivity
import org.atalk.util.SoftKeyboard
import org.jivesoftware.smackx.avatar.AvatarManager
import org.osgi.framework.BundleContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.text.DateFormat
import java.text.ParseException
import java.util.*
import java.util.concurrent.ExecutionException

/**
 * Activity allows user to set presence status, status message, change the user avatar
 * and all the vCard-temp information for the [.Account].
 *
 * The main panel that allows users to view and edit their account information.
 * Different instances of this class are created for every registered
 * `ProtocolProviderService`.
 * Currently, supported account details are first/middle/last names, nickname,
 * street/city/region/country address, postal code, birth date, gender,
 * organization name, job title, about me, home/work email, home/work phone.
 *
 *
 * The [.mAccount] is retrieved from the [Intent] extra by it's AccountID.getAccountUniqueID
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
open class AccountInfoPresenceActivity : OSGiActivity(), EventListener<AccountEvent?>, DialogActivity.DialogListener, SoftKeyboard.SoftKeyboardChanged, CalendarDatePickerDialogFragment.OnDateSetListener {
    /**
     * Calender Date Picker parameters
     */
    private var calendarDatePicker: CalendarDatePickerDialogFragment? = null

    /**
     * The account's [OperationSetPresence] used to perform presence operations
     */
    private var accountPresence: OperationSetPresence? = null

    /**
     * The instance of [Account] used for operations on the account
     */
    private lateinit var mAccount: Account

    /**
     * Flag indicates if there were any uncommitted changes that shall be applied on exit
     */
    private var hasChanges = false

    /**
     * Flag indicates if there were any uncommitted status changes that shall be applied on exit
     */
    private var hasStatusChanges = false

    /**
     * Mapping between all supported by this plugin `ServerStoredDetails` and their
     * respective `EditText` that are used for modifying the details.
     */
    private val detailToTextField = HashMap<Class<out GenericDetail>, EditText?>()

    /**
     * The `ProtocolProviderService` that this panel is associated with.
     */
    var protocolProvider: ProtocolProviderService? = null

    /**
     * The operation set giving access to the server stored account details.
     */
    private var accountInfoOpSet: OperationSetServerStoredAccountInfo? = null

    /*
     * imageUrlField contains the link to the image or a command to remove avatar
     */
    private lateinit var imageUrlField: EditText
    private lateinit var urlField: EditText
    private lateinit var aboutMeArea: EditText
    private lateinit var ageField: EditText
    private lateinit var birthDateField: EditText
    private lateinit var mApplyButton: Button
    private lateinit var editTextWatcher: EditTextWatcher
    private var displayNameDetail: DisplayNameDetail? = null
    private var firstNameDetail: FirstNameDetail? = null
    private var middleNameDetail: MiddleNameDetail? = null
    private var lastNameDetail: LastNameDetail? = null
    private var nicknameDetail: NicknameDetail? = null
    private var urlDetail: URLDetail? = null
    private var streetAddressDetail: AddressDetail? = null
    private var cityDetail: CityDetail? = null
    private var regionDetail: ProvinceDetail? = null
    private var postalCodeDetail: PostalCodeDetail? = null
    private var countryDetail: CountryDetail? = null
    private var phoneDetail: PhoneNumberDetail? = null
    private var workPhoneDetail: WorkPhoneDetail? = null
    private var mobilePhoneDetail: MobilePhoneDetail? = null
    private var emailDetail: EmailAddressDetail? = null
    private var workEmailDetail: WorkEmailAddressDetail? = null
    private var organizationDetail: WorkOrganizationNameDetail? = null
    private var jobTitleDetail: JobTitleDetail? = null
    private var aboutMeDetail: AboutMeDetail? = null
    private var genderDetail: GenderDetail? = null
    private var birthDateDetail: BirthDateDetail? = null
    private lateinit var avatarView: ImageView
    private lateinit var avatarDetail: ImageDetail
    private lateinit var dateFormat: DateFormat
    private lateinit var mGetContent: ActivityResultLauncher<String>

    /**
     * Container for apply and cancel buttons auto- hide when field text entry is active
     */
    private lateinit var mButtonContainer: View
    private lateinit var mCalenderButton: ImageView

    private var softKeyboard: SoftKeyboard? = null
    private var progressDialog: ProgressDialog? = null
    private var isRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the main layout
        setContentView(R.layout.account_info_presence_status)
        mButtonContainer = findViewById(R.id.button_Container)
        mGetContent = getAvatarContent()
        avatarView = findViewById(R.id.accountAvatar)
        registerForContextMenu(avatarView)
        avatarView.setOnClickListener { v: View? -> openContextMenu(avatarView) }

        // Get account ID from intent extras and find account for given account ID
        val accountIDStr = intent.getStringExtra(INTENT_ACCOUNT_ID)
        val accountID = AccountUtils.getAccountIDForUID(accountIDStr!!)
        if (accountID == null) {
            Timber.e("No account found for: %s", accountIDStr)
            finish()
            return
        }
        mAccount = Account(accountID, AndroidGUIActivator.bundleContext!!, this)
        mAccount.addAccountEventListener(this as EventListener<AccountEvent>)
        protocolProvider = mAccount.protocolProvider
        editTextWatcher = EditTextWatcher()
        initPresenceStatus()
        initSoftKeyboard()
        accountInfoOpSet = protocolProvider!!.getOperationSet(OperationSetServerStoredAccountInfo::class.java)
        if (accountInfoOpSet != null) {
            initSummaryPanel()

            // May still be in logging if user enters preference edit immediately after account is enabled
            if (!protocolProvider!!.isRegistered) {
                try {
                    Thread.sleep(3000)
                } catch (e: InterruptedException) {
                    Timber.e("Account Registration State wait error: %s", protocolProvider!!.registrationState)
                }
                Timber.d("Account Registration State: %s", protocolProvider!!.registrationState)
            }
            isRegistered = protocolProvider!!.isRegistered
            if (!isRegistered) {
                setTextEditState(false)
                Toast.makeText(this, R.string.plugin_accountinfo_NO_REGISTERED_MESSAGE, Toast.LENGTH_LONG).show()
            } else {
                loadDetails()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ActionBarUtil.setTitle(this, getString(R.string.plugin_accountinfo_TITLE))
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext?) {
        super.stop(bundleContext)
        if (progressDialog != null && progressDialog!!.isShowing) progressDialog!!.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (softKeyboard != null) {
            softKeyboard!!.unRegisterSoftKeyboardCallback()
            softKeyboard = null
        }
    }

    override fun onBackPressed() {
        if (!hasChanges && !hasStatusChanges) {
            super.onBackPressed()
        } else {
            checkUnsavedChanges()
        }
    }

    /**
     * Create and initialize the view with actual values
     */
    private fun initPresenceStatus() {
        accountPresence = mAccount.getPresenceOpSet()

        // Check for presence support
        if (accountPresence == null) {
            Toast.makeText(this, getString(R.string.service_gui_PRESENCE_NOT_SUPPORTED,
                    mAccount.getAccountName()), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Account properties
        ActionBarUtil.setSubtitle(this, mAccount.getAccountName())

        // Create spinner with status list
        val statusSpinner = findViewById<Spinner>(R.id.presenceStatusSpinner)

        // Create list adapter
        val presenceStatuses = accountPresence!!.getSupportedStatusSet()
        val statusAdapter = StatusListAdapter(this, R.layout.account_presence_status_row, presenceStatuses)
        statusSpinner.adapter = statusAdapter

        // Selects current status
        val presenceStatus = accountPresence!!.getPresenceStatus()
        ActionBarUtil.setStatusIcon(this, presenceStatus!!.statusIcon)
        statusSpinner.setSelection(statusAdapter.getPosition(presenceStatus), false)
        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                hasStatusChanges = true
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }

        // Sets current status message
        val statusMessageEdit = findViewById<EditText>(R.id.statusMessage)
        statusMessageEdit.setText(accountPresence!!.getCurrentStatusMessage())

        // Watch the text for any changes
        statusMessageEdit.addTextChangedListener(editTextWatcher)
    }

    /**
     * Initialized the main panel that contains all `ServerStoredDetails` and update
     * mapping between supported `ServerStoredDetails` and their respective
     * `EditText` that are used for modifying the details.
     */
    private fun initSummaryPanel() {
        imageUrlField = findViewById(R.id.ai_ImageUrl)
        detailToTextField[ImageDetail::class.java] = imageUrlField
        val displayNameField = findViewById<EditText>(R.id.ai_DisplayNameField)
        val displayNameContainer = findViewById<View>(R.id.ai_DisplayName_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(DisplayNameDetail::class.java)) {
            displayNameContainer.visibility = View.VISIBLE
            detailToTextField[DisplayNameDetail::class.java] = displayNameField
        }
        val firstNameField = findViewById<EditText>(R.id.ai_FirstNameField)
        detailToTextField[FirstNameDetail::class.java] = firstNameField
        val middleNameField = findViewById<EditText>(R.id.ai_MiddleNameField)
        detailToTextField[MiddleNameDetail::class.java] = middleNameField
        val lastNameField = findViewById<EditText>(R.id.ai_LastNameField)
        detailToTextField[LastNameDetail::class.java] = lastNameField
        val nicknameField = findViewById<EditText>(R.id.ai_NickNameField)
        val nickNameContainer = findViewById<View>(R.id.ai_NickName_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(NicknameDetail::class.java)) {
            nickNameContainer.visibility = View.VISIBLE
            detailToTextField[NicknameDetail::class.java] = nicknameField
        }
        urlField = findViewById(R.id.ai_URLField)
        val urlContainer = findViewById<View>(R.id.ai_URL_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(URLDetail::class.java)) {
            urlContainer.visibility = View.VISIBLE
            detailToTextField[URLDetail::class.java] = urlField
        }
        val genderField = findViewById<EditText>(R.id.ai_GenderField)
        val genderContainer = findViewById<View>(R.id.ai_Gender_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(GenderDetail::class.java)) {
            genderContainer.visibility = View.VISIBLE
            detailToTextField[GenderDetail::class.java] = genderField
        }
        birthDateField = findViewById(R.id.ai_BirthDateField)
        detailToTextField[BirthDateDetail::class.java] = birthDateField
        birthDateField.isEnabled = false
        ageField = findViewById(R.id.ai_AgeField)
        ageField.isEnabled = false
        val streetAddressField = findViewById<EditText>(R.id.ai_StreetAddressField)
        val streetAddressContainer = findViewById<View>(R.id.ai_StreetAddress_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(AddressDetail::class.java)) {
            streetAddressContainer.visibility = View.VISIBLE
            detailToTextField[AddressDetail::class.java] = streetAddressField
        }
        val cityField = findViewById<EditText>(R.id.ai_CityField)
        val cityContainer = findViewById<View>(R.id.ai_City_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(CityDetail::class.java)) {
            cityContainer.visibility = View.VISIBLE
            detailToTextField[CityDetail::class.java] = cityField
        }
        val regionField = findViewById<EditText>(R.id.ai_RegionField)
        val regionContainer = findViewById<View>(R.id.ai_Region_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(ProvinceDetail::class.java)) {
            regionContainer.visibility = View.VISIBLE
            detailToTextField[ProvinceDetail::class.java] = regionField
        }
        val postalCodeField = findViewById<EditText>(R.id.ai_PostalCodeField)
        val postalCodeContainer = findViewById<View>(R.id.ai_PostalCode_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(PostalCodeDetail::class.java)) {
            postalCodeContainer.visibility = View.VISIBLE
            detailToTextField[PostalCodeDetail::class.java] = postalCodeField
        }
        val countryField = findViewById<EditText>(R.id.ai_CountryField)
        val countryContainer = findViewById<View>(R.id.ai_Country_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(CountryDetail::class.java)) {
            countryContainer.visibility = View.VISIBLE
            detailToTextField[CountryDetail::class.java] = countryField
        }
        val emailField = findViewById<EditText>(R.id.ai_EMailField)
        detailToTextField[EmailAddressDetail::class.java] = emailField
        val workEmailField = findViewById<EditText>(R.id.ai_WorkEmailField)
        val workEmailContainer = findViewById<View>(R.id.ai_WorkEmail_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(WorkEmailAddressDetail::class.java)) {
            workEmailContainer.visibility = View.VISIBLE
            detailToTextField[WorkEmailAddressDetail::class.java] = workEmailField
        }
        val phoneField = findViewById<EditText>(R.id.ai_PhoneField)
        detailToTextField[PhoneNumberDetail::class.java] = phoneField
        val workPhoneField = findViewById<EditText>(R.id.ai_WorkPhoneField)
        val workPhoneContainer = findViewById<View>(R.id.ai_WorkPhone_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(WorkPhoneDetail::class.java)) {
            workPhoneContainer.visibility = View.VISIBLE
            detailToTextField[WorkPhoneDetail::class.java] = workPhoneField
        }
        val mobilePhoneField = findViewById<EditText>(R.id.ai_MobilePhoneField)
        val mobileContainer = findViewById<View>(R.id.ai_MobilePhone_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(MobilePhoneDetail::class.java)) {
            mobileContainer.visibility = View.VISIBLE
            detailToTextField[MobilePhoneDetail::class.java] = mobilePhoneField
        }
        val organizationField = findViewById<EditText>(R.id.ai_OrganizationNameField)
        val organizationNameContainer = findViewById<View>(R.id.ai_OrganizationName_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(WorkOrganizationNameDetail::class.java)) {
            organizationNameContainer.visibility = View.VISIBLE
            detailToTextField[WorkOrganizationNameDetail::class.java] = organizationField
        }
        val jobTitleField = findViewById<EditText>(R.id.ai_JobTitleField)
        val jobDetailContainer = findViewById<View>(R.id.ai_JobTitle_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(JobTitleDetail::class.java)) {
            jobDetailContainer.visibility = View.VISIBLE
            detailToTextField[JobTitleDetail::class.java] = jobTitleField
        }
        aboutMeArea = findViewById(R.id.ai_AboutMeField)
        val aboutMeContainer = findViewById<View>(R.id.ai_AboutMe_Container)
        if (accountInfoOpSet!!.isDetailClassSupported(AboutMeDetail::class.java)) {
            aboutMeContainer.visibility = View.VISIBLE
            detailToTextField[AboutMeDetail::class.java] = aboutMeArea

            // aboutMeArea.setEnabled(false) cause auto-launch of softKeyboard creating problem
            val filterArray = arrayOfNulls<InputFilter>(1)
            filterArray[0] = InputFilter.LengthFilter(ContactInfoActivity.ABOUT_ME_MAX_CHARACTERS)
            aboutMeArea.filters = filterArray
            aboutMeArea.setBackgroundResource(R.drawable.alpha_blue_01)
        }

        // Setup and initialize birthday calendar basic parameters
        dateFormat = DateFormat.getDateInstance()
        val today = Calendar.getInstance()
        val thisYear = today[Calendar.YEAR]
        val thisMonth = today[Calendar.MONTH]
        val thisDay = today[Calendar.DAY_OF_MONTH]
        val TODAY = CalendarDay(thisYear, thisMonth, thisDay)
        calendarDatePicker = CalendarDatePickerDialogFragment()
                .setOnDateSetListener(this@AccountInfoPresenceActivity)
                .setFirstDayOfWeek(Calendar.MONDAY)
                .setDateRange(DEFAULT_START_DATE, TODAY)
                .setDoneText("Done")
                .setCancelText("Cancel")
                .setThemeDark()
        mCalenderButton = findViewById(R.id.datePicker)
        mCalenderButton.isEnabled = false
        mCalenderButton.setOnClickListener { v: View? -> calendarDatePicker!!.show(supportFragmentManager, FRAG_TAG_DATE_PICKER) }
        mApplyButton = findViewById(R.id.button_Apply)
        mApplyButton.setOnClickListener { v: View? -> if (hasChanges || hasStatusChanges) launchApplyProgressDialog() else finish() }
        val mCancelButton = findViewById<Button>(R.id.button_Cancel)
        mCancelButton.setOnClickListener { v: View? -> checkUnsavedChanges() }
    }

    /**
     * check for any unsaved changes and alert user
     */
    private fun checkUnsavedChanges() {
        if (hasChanges) {
            DialogActivity.showConfirmDialog(this,
                    R.string.service_gui_UNSAVED_CHANGES_TITLE,
                    R.string.service_gui_UNSAVED_CHANGES,
                    R.string.service_gui_SAVE, this)
        } else {
            finish()
        }
    }

    /**
     * Fired when user clicks the dialog's confirm button.
     *
     * @param dialog source `DialogActivity`.
     */
    override fun onConfirmClicked(dialog: DialogActivity): Boolean {
        return mApplyButton.performClick()
    }

    /**
     * Fired when user dismisses the dialog.
     *
     * @param dialog source `DialogActivity`
     */
    override fun onDialogCancelled(dialog: DialogActivity) {
        finish()
    }

    // CalendarDatePickerDialogFragment callback
    override fun onDateSet(dialog: CalendarDatePickerDialogFragment?, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val mDate = Calendar.getInstance()
        var age = mDate[Calendar.YEAR] - year
        if (mDate[Calendar.MONTH] < monthOfYear) age--
        if (mDate[Calendar.MONTH] == monthOfYear && mDate[Calendar.DAY_OF_MONTH] < dayOfMonth) age--
        val ageDetail = age.toString()
        ageField.setText(ageDetail)
        mDate[year, monthOfYear] = dayOfMonth
        birthDateField.setText(dateFormat.format(mDate.time))

        // internal program call is with dialog == null
        hasChanges = dialog != null
    }

    private fun setTextEditState(editState: Boolean) {
        var isEditable: Boolean
        for (editable in detailToTextField.keys) {
            val field = detailToTextField[editable]
            isEditable = editState && accountInfoOpSet!!.isDetailClassEditable(editable)
            if (editable == BirthDateDetail::class.java) mCalenderButton.isEnabled = isEditable else if (editable == ImageDetail::class.java) avatarView.isEnabled = isEditable else if (field != null) {
                field.isEnabled = isEditable
                if (isEditable) field.addTextChangedListener(editTextWatcher)
            }
        }
    }

    /**
     * Loads all `ServerStoredDetails` which are currently supported by
     * this plugin. Note that some `OperationSetServerStoredAccountInfo`
     * implementations may support details that are not supported by this plugin.
     * In this case they will not be loaded.
     */
    private fun loadDetails() {
        if (accountInfoOpSet != null) {
            DetailsLoadWorker().execute()
        }
    }

    /**
     * Loads details in separate thread.
     */
    private open inner class DetailsLoadWorker : AsyncTask<Void?, Void?, Iterator<GenericDetail?>?>() {
        override fun onPreExecute() {}

        override fun doInBackground(vararg params: Void?): MutableIterator<GenericDetail?>? {
            return accountInfoOpSet!!.getAllAvailableDetails()
        }

        /**
         * Called on the event dispatching thread (not on the worker thread)
         * after the `construct` method has returned.
         */
        override fun onPostExecute(result: Iterator<GenericDetail?>?) {
            var allDetails: Iterator<GenericDetail?>? = null
            try {
                allDetails = get()
            } catch (e: InterruptedException) {
                Timber.w("Exception in loading account details: %s", e.message)
            } catch (e: ExecutionException) {
                Timber.w("Exception in loading account details: %s", e.message)
            }
            if (allDetails != null) {
                while (allDetails.hasNext()) {
                    val detail = allDetails.next()!!
                    loadDetail(detail)
                }

                // Setup textFields' editable state and addTextChangedListener if enabled
                var isEditable: Boolean
                for (editable in detailToTextField.keys) {
                    val field = detailToTextField[editable]
                    isEditable = accountInfoOpSet!!.isDetailClassEditable(editable)
                    if (editable == BirthDateDetail::class.java) mCalenderButton.isEnabled = isEditable else if (editable == ImageDetail::class.java) avatarView.isEnabled = isEditable else {
                        if (field != null) {
                            field.isEnabled = isEditable
                            if (isEditable) field.addTextChangedListener(editTextWatcher)
                        }
                    }
                }
            }
            // get user avatar via XEP-0084
            getUserAvatarData()
        }
    }

    /**
     * Loads a single `GenericDetail` obtained from the
     * `OperationSetServerStoredAccountInfo` into this plugin.
     *
     * If VcardTemp contains <photo></photo>, it will be converted to XEP-0084 avatarData &
     * avatarMetadata, and remove it from VCardTemp.
     *
     * @param detail the loaded detail for extraction.
     */
    private fun loadDetail(detail: GenericDetail) {
        if (detail.javaClass == AboutMeDetail::class.java) {
            aboutMeDetail = detail as AboutMeDetail
            aboutMeArea.setText(detail.getDetailValue() as String)
            return
        }
        if (detail is BirthDateDetail) {
            birthDateDetail = detail
            val objBirthDate = birthDateDetail!!.getDetailValue()

            // default to today if birthDate is null
            when {
                objBirthDate is Calendar -> {
                    val bYear = objBirthDate[Calendar.YEAR]
                    val bMonth = objBirthDate[Calendar.MONTH]
                    val bDay = objBirthDate[Calendar.DAY_OF_MONTH]
                    // Preset calendarDatePicker date
                    calendarDatePicker!!.setPreselectedDate(bYear, bMonth, bDay)

                    // Update BirthDate and Age
                    onDateSet(null, bYear, bMonth, bDay)
                }
                objBirthDate != null -> {
                    birthDateField.setText(objBirthDate as String)
                }
            }
            return
        }

        val field = detailToTextField[detail.javaClass]
        if (field != null) {
            when (detail) {
                is ImageDetail -> {
                    avatarDetail = detail
                    val avatarImage = avatarDetail.getBytes()
                    val bitmap = BitmapFactory.decodeByteArray(avatarImage, 0, avatarImage.size)
                    avatarView.setImageBitmap(bitmap)
                }
                is URLDetail -> {
                    urlDetail = detail
                    urlField.setText(urlDetail!!.getURL().toString())
                }
                else -> {
                    val obj = detail.getDetailValue()
                    when {
                        obj is String -> field.setText(obj)
                        obj != null -> field.setText(obj.toString())
                    }
                    when (detail.javaClass) {
                        DisplayNameDetail::class.java -> displayNameDetail = detail as DisplayNameDetail
                        FirstNameDetail::class.java -> firstNameDetail = detail as FirstNameDetail
                        MiddleNameDetail::class.java -> middleNameDetail = detail as MiddleNameDetail
                        LastNameDetail::class.java -> lastNameDetail = detail as LastNameDetail
                        NicknameDetail::class.java -> nicknameDetail = detail as NicknameDetail
                        GenderDetail::class.java -> genderDetail = detail as GenderDetail
                        AddressDetail::class.java -> streetAddressDetail = detail as AddressDetail
                        CityDetail::class.java -> cityDetail = detail as CityDetail
                        ProvinceDetail::class.java -> regionDetail = detail as ProvinceDetail
                        PostalCodeDetail::class.java -> postalCodeDetail = detail as PostalCodeDetail
                        CountryDetail::class.java -> countryDetail = detail as CountryDetail
                        PhoneNumberDetail::class.java -> phoneDetail = detail as PhoneNumberDetail
                        WorkPhoneDetail::class.java -> workPhoneDetail = detail as WorkPhoneDetail
                        MobilePhoneDetail::class.java -> mobilePhoneDetail = detail as MobilePhoneDetail
                        EmailAddressDetail::class.java -> emailDetail = detail as EmailAddressDetail
                        WorkEmailAddressDetail::class.java -> workEmailDetail = detail as WorkEmailAddressDetail
                        WorkOrganizationNameDetail::class.java -> organizationDetail = detail as WorkOrganizationNameDetail
                        JobTitleDetail::class.java -> jobTitleDetail = detail as JobTitleDetail
                        AboutMeDetail::class.java -> aboutMeDetail = detail as AboutMeDetail
                    }
                }
            }
        }
    }

    /**
     * Retrieve avatar via XEP-0084 and override vCard <photo></photo> content if avatarImage not null
     */
    private fun getUserAvatarData() {
        val avatarImage = AvatarManager.getAvatarImageByJid(mAccount.getJid()!!.asBareJid())
        if (avatarImage != null && avatarImage.isNotEmpty()) {
            val bitmap = BitmapFactory.decodeByteArray(avatarImage, 0, avatarImage.size)
            avatarView.setImageBitmap(bitmap)
        } else {
            avatarView.setImageResource(R.drawable.person_photo)
        }
    }

    /**
     * Attempts to upload all `ServerStoredDetails` on the server using
     * `OperationSetServerStoredAccountInfo`
     */
    private fun submitChangesAction() {
        if (!isRegistered || !hasChanges) return
        if (accountInfoOpSet!!.isDetailClassSupported(ImageDetail::class.java)) {
            val sCommand = ViewUtil.toString(imageUrlField)
            if (sCommand != null) {
                val newDetail: ImageDetail

                /*
                 * command to remove avatar photo from vCardTemp. XEP-0084 support will always
                 * init imageUrlField = AVATAR_ICON_REMOVE
                 */
                if (AVATAR_ICON_REMOVE == sCommand) {
                    newDetail = ImageDetail("avatar", ByteArray(0))
                    changeDetail(avatarDetail, newDetail)
                } else {
                    try {
                        val imageUri = Uri.parse(sCommand)
                        val bmp = AndroidImageUtil.scaledBitmapFromContentUri(this,
                                imageUri, AVATAR_PREFERRED_SIZE, AVATAR_PREFERRED_SIZE)

                        // Convert to bytes if not null
                        if (bmp != null) {
                            val rawImage = AndroidImageUtil.convertToBytes(bmp, 100)
                            newDetail = ImageDetail("avatar", rawImage)
                            changeDetail(avatarDetail, newDetail)
                        } else showAvatarChangeError()
                    } catch (e: IOException) {
                        Timber.e(e, "%s", e.message)
                        showAvatarChangeError()
                    }
                }
            }
        }
        if (accountInfoOpSet!!.isDetailClassSupported(DisplayNameDetail::class.java)) {
            val text = getText(DisplayNameDetail::class.java)
            var newDetail: DisplayNameDetail? = null
            if (text != null) newDetail = DisplayNameDetail(text)
            if (displayNameDetail != null || newDetail != null) changeDetail(displayNameDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(FirstNameDetail::class.java)) {
            val text = getText(FirstNameDetail::class.java)
            var newDetail: FirstNameDetail? = null
            if (text != null) newDetail = FirstNameDetail(text)
            if (firstNameDetail != null || newDetail != null) changeDetail(firstNameDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(MiddleNameDetail::class.java)) {
            val text = getText(MiddleNameDetail::class.java)
            var newDetail: MiddleNameDetail? = null
            if (text != null) newDetail = MiddleNameDetail(text)
            if (middleNameDetail != null || newDetail != null) changeDetail(middleNameDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(LastNameDetail::class.java)) {
            val text = getText(LastNameDetail::class.java)
            var newDetail: LastNameDetail? = null
            if (text != null) newDetail = LastNameDetail(text)
            if (lastNameDetail != null || newDetail != null) changeDetail(lastNameDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(NicknameDetail::class.java)) {
            val text = getText(NicknameDetail::class.java)
            var newDetail: NicknameDetail? = null
            if (text != null) newDetail = NicknameDetail(text)
            if (nicknameDetail != null || newDetail != null) changeDetail(nicknameDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(URLDetail::class.java)) {
            val text = getText(URLDetail::class.java)
            val url: URL
            var newDetail: URLDetail? = null
            if (text != null) {
                try {
                    url = URL(text)
                    newDetail = URLDetail("URL", url)
                } catch (e1: MalformedURLException) {
                    Timber.d("URL field has malformed URL save as text instead.")
                    newDetail = URLDetail("URL", text)
                }
            }
            if (urlDetail != null || newDetail != null) changeDetail(urlDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(GenderDetail::class.java)) {
            val text = getText(GenderDetail::class.java)
            var newDetail: GenderDetail? = null
            if (text != null) newDetail = GenderDetail(text)
            if (genderDetail != null || newDetail != null) changeDetail(genderDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(BirthDateDetail::class.java)) {
            val text = ViewUtil.toString(birthDateField)
            var newDetail: BirthDateDetail? = null
            if (text != null) {
                val birthDate = Calendar.getInstance()
                try {
                    val mDate = dateFormat.parse(text)
                    birthDate.time = mDate!!
                    newDetail = BirthDateDetail(birthDate)
                } catch (e: ParseException) {
                    // Save as String value
                    newDetail = BirthDateDetail(text)
                }
            }
            if (birthDateDetail != null || newDetail != null) changeDetail(birthDateDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(AddressDetail::class.java)) {
            val text = getText(AddressDetail::class.java)
            var newDetail: AddressDetail? = null
            if (text != null) newDetail = AddressDetail(text)
            if (streetAddressDetail != null || newDetail != null) changeDetail(streetAddressDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(CityDetail::class.java)) {
            val text = getText(CityDetail::class.java)
            var newDetail: CityDetail? = null
            if (text != null) newDetail = CityDetail(text)
            if (cityDetail != null || newDetail != null) changeDetail(cityDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(ProvinceDetail::class.java)) {
            val text = getText(ProvinceDetail::class.java)
            var newDetail: ProvinceDetail? = null
            if (text != null) newDetail = ProvinceDetail(text)
            if (regionDetail != null || newDetail != null) changeDetail(regionDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(PostalCodeDetail::class.java)) {
            val text = getText(PostalCodeDetail::class.java)
            var newDetail: PostalCodeDetail? = null
            if (text != null) newDetail = PostalCodeDetail(text)
            if (postalCodeDetail != null || newDetail != null) changeDetail(postalCodeDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(CountryDetail::class.java)) {
            val text = getText(CountryDetail::class.java)
            var newDetail: CountryDetail? = null
            if (text != null) newDetail = CountryDetail(text)
            if (countryDetail != null || newDetail != null) changeDetail(countryDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(EmailAddressDetail::class.java)) {
            val text = getText(EmailAddressDetail::class.java)
            var newDetail: EmailAddressDetail? = null
            if (text != null) newDetail = EmailAddressDetail(text)
            if (emailDetail != null || newDetail != null) changeDetail(emailDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(WorkEmailAddressDetail::class.java)) {
            val text = getText(WorkEmailAddressDetail::class.java)
            var newDetail: WorkEmailAddressDetail? = null
            if (text != null) newDetail = WorkEmailAddressDetail(text)
            if (workEmailDetail != null || newDetail != null) changeDetail(workEmailDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(PhoneNumberDetail::class.java)) {
            val text = getText(PhoneNumberDetail::class.java)
            var newDetail: PhoneNumberDetail? = null
            if (text != null) newDetail = PhoneNumberDetail(text)
            if (phoneDetail != null || newDetail != null) changeDetail(phoneDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(WorkPhoneDetail::class.java)) {
            val text = getText(WorkPhoneDetail::class.java)
            var newDetail: WorkPhoneDetail? = null
            if (text != null) newDetail = WorkPhoneDetail(text)
            if (workPhoneDetail != null || newDetail != null) changeDetail(workPhoneDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(MobilePhoneDetail::class.java)) {
            val text = getText(MobilePhoneDetail::class.java)
            var newDetail: MobilePhoneDetail? = null
            if (text != null) newDetail = MobilePhoneDetail(text)
            if (mobilePhoneDetail != null || newDetail != null) changeDetail(mobilePhoneDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(WorkOrganizationNameDetail::class.java)) {
            val text = getText(WorkOrganizationNameDetail::class.java)
            var newDetail: WorkOrganizationNameDetail? = null
            if (text != null) newDetail = WorkOrganizationNameDetail(text)
            if (organizationDetail != null || newDetail != null) changeDetail(organizationDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(JobTitleDetail::class.java)) {
            val text = getText(JobTitleDetail::class.java)
            var newDetail: JobTitleDetail? = null
            if (text != null) newDetail = JobTitleDetail(text)
            if (jobTitleDetail != null || newDetail != null) changeDetail(jobTitleDetail, newDetail)
        }
        if (accountInfoOpSet!!.isDetailClassSupported(AboutMeDetail::class.java)) {
            val text = ViewUtil.toString(aboutMeArea)
            var newDetail: AboutMeDetail? = null
            if (text != null) newDetail = AboutMeDetail(text)
            if (aboutMeDetail != null || newDetail != null) changeDetail(aboutMeDetail, newDetail)
        }
        try {
            //mainScrollPane.getVerticalScrollBar().setValue(0)
            accountInfoOpSet!!.save()
        } catch (e1: OperationFailedException) {
            showAvatarChangeError()
        }
    }

    /**
     * get the class's editText string value or null (length == 0)
     *
     * @param className Class Name
     * @return String or null if string length == 0
     */
    private fun getText(className: Class<out GenericDetail>): String? {
        val editText = detailToTextField[className]
        return ViewUtil.toString(editText)
    }

    /**
     * A helper method to decide whether to add new
     * `ServerStoredDetails` or to replace an old one.
     *
     * @param oldDetail the detail to be replaced.
     * @param newDetail the replacement.
     */
    private fun changeDetail(oldDetail: GenericDetail?, newDetail: GenericDetail?) {
        try {
            if (newDetail == null) {
                accountInfoOpSet!!.removeDetail(oldDetail)
            } else if (oldDetail == null) {
                accountInfoOpSet!!.addDetail(newDetail)
            } else {
                accountInfoOpSet!!.replaceDetail(oldDetail, newDetail)
            }
        } catch (e1: ArrayIndexOutOfBoundsException) {
            Timber.d("Failed to update account details.%s %s", mAccount.getAccountName(), e1.message)
        } catch (e1: OperationFailedException) {
            Timber.d("Failed to update account details.%s %s", mAccount.getAccountName(), e1.message)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.presence_status_menu, menu)
        return true
    }

    /**
     * {@inheritDoc}
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.remove -> {
                create(this, mAccount, object : OnAccountRemovedListener {
                    override fun onAccountRemoved(account: Account) {
                        // Prevent from submitting status
                        hasStatusChanges = false
                        hasChanges = false
                        finish()
                    }
                })
                return true
            }
            R.id.account_settings -> {
                val preferences = AccountPreferenceActivity.getIntent(this, mAccount.getAccountID())
                startActivity(preferences)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v.id == R.id.accountAvatar) {
            menuInflater.inflate(R.menu.avatar_menu, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.avatar_ChoosePicture -> {
                onAvatarClicked(avatarView)
                true
            }
            R.id.avatar_RemovePicture -> {
                imageUrlField.setText(AVATAR_ICON_REMOVE)
                avatarView.setImageResource(R.drawable.person_photo)
                hasChanges = true
                true
            }
            R.id.avatar_Cancel -> true
            else -> super.onContextItemSelected(item)
        }
    }

    /**
     * Method mapped to the avatar image clicked event. It starts the select image [Intent]
     *
     * @param avatarView the [View] that has been clicked
     */
    private fun onAvatarClicked(avatarView: View) {
        if (mAccount.getAvatarOpSet() == null) {
            Timber.w("Avatar operation set is not supported by %s", mAccount.getAccountName())
            showAvatarChangeError()
            return
        }
        mGetContent.launch("image/*")
    }

    /**
     * A contract specifying that an activity can be called with an input of type I
     * and produce an output of type O
     *
     * @return an instant of ActivityResultLauncher<String>
     * @see ActivityResultCaller
    </String> */
    private fun getAvatarContent(): ActivityResultLauncher<String> {
        return registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                Timber.e("No image data selected for avatar!")
                showAvatarChangeError()
            } else {
                val fileName = "cropImage"
                val tmpFile = File(this.cacheDir, fileName)
                val destinationUri = Uri.fromFile(tmpFile)
                UCrop.of(uri, destinationUri)
                        .withAspectRatio(1f, 1f)
                        .withMaxResultSize(CROP_MAX_SIZE, CROP_MAX_SIZE)
                        .start(this)
            }
        }
    }

    /**
     * Method handles callbacks from external [Intent] that retrieve avatar image
     *
     * @param requestCode the request code
     * @param resultCode the result code
     * @param data the source [Intent] that returns the result
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            UCrop.REQUEST_CROP -> {
                val resultUri: Uri? = UCrop.getOutput(data!!)
                if (resultUri != null) {
                    try {
                        val bmp = AndroidImageUtil.scaledBitmapFromContentUri(this, resultUri,
                                AVATAR_PREFERRED_SIZE, AVATAR_PREFERRED_SIZE)
                        if (bmp == null) {
                            Timber.e("Failed to obtain bitmap from: %s", data)
                            showAvatarChangeError()
                        } else {
                            avatarView.setImageBitmap(bmp)
                            imageUrlField.setText(resultUri.toString())
                            hasChanges = true
                        }
                    } catch (e: IOException) {
                        Timber.e(e, "%s", e.message)
                        showAvatarChangeError()
                    }
                }
            }
            UCrop.RESULT_ERROR -> {
                val cropError: Throwable? = UCrop.getError(data!!)
                var errMsg = "Image crop error: "
                if (cropError != null) errMsg += cropError.message
                Timber.e("%s", errMsg)
                showAvatarChangeError()
            }
        }
    }

    private fun showAvatarChangeError() {
        DialogActivity.showDialog(this,
                R.string.service_gui_ERROR, R.string.service_gui_AVATAR_SET_ERROR, mAccount.getAccountName())
    }

    /**
     * Method starts a new Thread and publishes the status
     *
     * @param status [PresenceStatus] to be set
     * @param text the status message
     */
    private fun publishStatus(status: PresenceStatus, text: String) {
        Thread {
            try {
                // Try to publish selected status
                Timber.d("Publishing status %s msg: %s", status, text)
                val pps = mAccount.protocolProvider
                // cmeng: set state to false to force it to execute offline->online
                ServiceUtils.getService(AndroidGUIActivator.bundleContext, GlobalStatusService::class.java)?.publishStatus(pps!!, status, false)
                if (pps!!.isRegistered) accountPresence!!.publishPresenceStatus(status, text)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }.start()
    }

    /**
     * Fired when the [.mAccount] has changed and the UI need to be updated
     *
     * @param eventObject the instance that has been changed
     * cmeng: may not be required anymore with new implementation
     */
    override fun onChangeEvent(eventObject: AccountEvent?) {
        if (eventObject!!.eventType != AccountEvent.AVATAR_CHANGE) {
            return
        }

        runOnUiThread {
            val account = eventObject.source
            avatarView.setImageDrawable(account.avatarIcon)
        }
    }

    /**
     * Checks if there are any uncommitted changes and applies them eventually
     */
    private fun commitStatusChanges() {
        if (hasStatusChanges) {
            val statusSpinner = findViewById<Spinner>(R.id.presenceStatusSpinner)
            val selectedStatus = statusSpinner.selectedItem as PresenceStatus
            val statusMessageText = ViewUtil.toString(findViewById(R.id.statusMessage))
            if (selectedStatus.status == PresenceStatus.OFFLINE && hasChanges) {
                // abort all account info changes if user goes offline
                hasChanges = false
                if (progressDialog != null) {
                    progressDialog!!.setMessage(getString(R.string.plugin_accountinfo_DISCARD_CHANGE))
                }
            }
            // Publish status in new thread
            publishStatus(selectedStatus, statusMessageText!!)
        }
    }

    /**
     * Progressing dialog while applying changes to account info/status
     * Auto cancel the dialog at end of applying cycle
     */
    private fun launchApplyProgressDialog() {
        progressDialog = ProgressDialog.show(this, getString(R.string.service_gui_WAITING),
                getString(R.string.service_gui_APPLY_CHANGES), true, true)
        Thread {
            try {
                commitStatusChanges()
                submitChangesAction()
                // too fast to be viewed user at times - so pause for 2.0 seconds
                Thread.sleep(2000)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            progressDialog!!.dismiss()
            finish()
        }.start()
    }

    /*
     * cmeng 20191118 - manipulate android softKeyboard may cause problem in >= android-9 (API-28)
     * all view Dimensions are incorrectly init when soffKeyboard is auto launched.
     * aboutMeArea.setEnabled(false) cause softKeyboard to auto-launch
     *
     * SoftKeyboard event handler to show/hide view buttons to give more space for fields' text entry.
     * # init to handle when softKeyboard is hided/shown
     */
    private fun initSoftKeyboard() {
        val mainLayout = findViewById<LinearLayout>(R.id.accountInfo_layout)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        /*  Instantiate and pass a callback */
        softKeyboard = SoftKeyboard(mainLayout, imm)
        softKeyboard!!.setSoftKeyboardCallback(this)
    }

    // Events to show or hide buttons for bigger view space for text entry
    override fun onSoftKeyboardHide() {
        Handler(Looper.getMainLooper()).post { mButtonContainer.visibility = View.VISIBLE }
    }

    override fun onSoftKeyboardShow() {
        Handler(Looper.getMainLooper()).post { mButtonContainer.visibility = View.GONE }
    }

    private inner class EditTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            // Ignore
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            hasChanges = true
        }
    }

    companion object {
        private const val FRAG_TAG_DATE_PICKER = "fragment_date_picker_name"
        private val DEFAULT_START_DATE = CalendarDay(1900, Calendar.JANUARY, 1)
        private const val AVATAR_ICON_REMOVE = "Remove Picture"

        // avatar default image size
        private const val AVATAR_PREFERRED_SIZE = 64
        private const val CROP_MAX_SIZE = 108

        /**
         * Intent's extra's key for account ID property of this activity
         */
        const val INTENT_ACCOUNT_ID = "account_id"
    }
}
