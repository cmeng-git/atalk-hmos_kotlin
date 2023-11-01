package org.atalk.hmos.gui.contactlist

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import net.java.sip.communicator.service.protocol.Contact
import net.java.sip.communicator.service.protocol.OperationSetPresence
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo.DetailsResponseListener
import net.java.sip.communicator.service.protocol.ServerStoredDetails.AboutMeDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.AddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.BinaryDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.BirthDateDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.CityDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.CountryDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.DisplayNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.EmailAddressDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.FirstNameDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenderDetail
import net.java.sip.communicator.service.protocol.ServerStoredDetails.GenericDetail
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
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.gui.aTalk
import org.atalk.hmos.gui.actionbar.ActionBarUtil.setStatusIcon
import org.atalk.hmos.gui.actionbar.ActionBarUtil.setTitle
import org.atalk.hmos.gui.util.AndroidImageUtil.bitmapFromBytes
import org.atalk.service.osgi.OSGiActivity
import timber.log.Timber
import java.text.DateFormat
import java.util.*

/**
 * Activity allows user to view presence status, status message, the avatar and the full
 * vCard-temp information for the [.mContact].
 *
 *
 * The main panel that allows users to view their account information. Different instances of
 * this class are created for every registered `ProtocolProviderService`.
 * Currently, supported account details are first/middle/last names, nickname,
 * street/city/region/country address, postal code, birth date, gender, organization name, job
 * title, about me, home/work email, home/work phone.
 *
 *
 *
 *
 * The [.mContact] is retrieved from the [Intent] by direct access to
 * @link ContactListFragment#getClickedContact()
 *
 * @author Eng Chong Meng
 */
class ContactInfoActivity : OSGiActivity(), DetailsResponseListener {
    /**
     * Mapping between all supported by this plugin `ServerStoredDetails` and their
     * respective `TextView` that are used for modifying the details.
     */
    private val detailToTextField = HashMap<Class<out GenericDetail>, TextView?>()
    private var urlField: TextView? = null
    private var ageField: TextView? = null
    private var birthDateField: TextView? = null

    /**
     * The currently selected contact we are displaying information about.
     */
    private var mContact: Contact? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_info)

        // Get contact ID from intent extras - but cannot link to mContact
        val contactId = intent.getStringExtra(INTENT_CONTACT_ID)
        val clf = aTalk.getFragment(aTalk.CL_FRAGMENT)
        if (clf is ContactListFragment) {
            val metaContact = clf.getClickedContact()
            if (metaContact == null) {
                Timber.e("Requested contact info not found: %s", contactId)
                finish()
            } else {
                mContact = metaContact.getDefaultContact()
                val pps = mContact!!.protocolProvider
                /*
                 * The operation set giving access to the server stored contact details.
                 */
                val contactInfoOpSet = pps.getOperationSet(OperationSetServerStoredContactInfo::class.java)
                if (contactInfoOpSet != null && pps.isRegistered) {
                    initPresenceStatus()
                    initSummaryPanel()

                    // Always retrieve new contact vCard-temp info from server. Otherwise contact
                    // info changes after account login will not be reflected in the display info.
                    contactInfoOpSet.requestAllDetailsForContact(mContact, this)
                }
            }
        }
    }

    /**
     * Create and initialize the view with actual values
     */
    private fun initPresenceStatus() {
        val title = mContact!!.displayName
        setTitle(this, title)

        // Setup the contact presence status
        val presenceStatus = mContact!!.presenceStatus
        if (presenceStatus != null) {
            setStatusIcon(this, presenceStatus.statusIcon)
            val statusNameView = findViewById<TextView>(R.id.presenceStatusName)
            val statusIconView = findViewById<ImageView>(R.id.presenceStatusIcon)

            // Set status icon
            val presenceIcon = bitmapFromBytes(presenceStatus.statusIcon)
            statusIconView.setImageBitmap(presenceIcon)

            // Set status name
            val statusName = presenceStatus.statusName
            statusNameView.text = statusName

            // Add users status message if it exists
            val statusMessage = findViewById<TextView>(R.id.statusMessage)
            val pps = mContact!!.protocolProvider
            val contactPresence = pps.getOperationSet(OperationSetPresence::class.java)
            val statusMsg = contactPresence!!.getCurrentStatusMessage()
            // String statusMsg = mContact.getStatusMessage();
            if (StringUtils.isNotBlank(statusMsg)) {
                statusMessage.text = statusMsg
            }
        }
    }

    /**
     * Creates a panel that displays the following contact details:
     *
     *
     * Currently, supported contact details are first/middle/last names, nickname,
     * street/city/region/country address, postal code, birth date, gender,
     * organization name, job title, about me, home/work email, home/work phone.
     */
    private fun initSummaryPanel() {
        // Display name details.
        val displayNameField = findViewById<TextView>(R.id.ci_DisplayNameField)
        detailToTextField[DisplayNameDetail::class.java] = displayNameField

        // First name details.
        val firstNameField = findViewById<TextView>(R.id.ci_FirstNameField)
        detailToTextField[FirstNameDetail::class.java] = firstNameField

        // Middle name details.
        val middleNameField = findViewById<TextView>(R.id.ci_MiddleNameField)
        detailToTextField[MiddleNameDetail::class.java] = middleNameField

        // Last name details.
        val lastNameField = findViewById<TextView>(R.id.ci_LastNameField)
        detailToTextField[LastNameDetail::class.java] = lastNameField
        val nicknameField = findViewById<TextView>(R.id.ci_NickNameField)
        detailToTextField[NicknameDetail::class.java] = nicknameField
        urlField = findViewById(R.id.ci_URLField)
        detailToTextField[URLDetail::class.java] = urlField

        // Gender details.
        val genderField = findViewById<TextView>(R.id.ci_GenderField)
        detailToTextField[GenderDetail::class.java] = genderField

        // Birthday and Age details.
        ageField = findViewById(R.id.ci_AgeField)
        birthDateField = findViewById(R.id.ci_BirthDateField)
        detailToTextField[BirthDateDetail::class.java] = birthDateField
        val streetAddressField = findViewById<TextView>(R.id.ci_StreetAddressField)
        detailToTextField[AddressDetail::class.java] = streetAddressField
        val cityField = findViewById<TextView>(R.id.ci_CityField)
        detailToTextField[CityDetail::class.java] = cityField
        val regionField = findViewById<TextView>(R.id.ci_RegionField)
        detailToTextField[ProvinceDetail::class.java] = regionField
        val postalCodeField = findViewById<TextView>(R.id.ci_PostalCodeField)
        detailToTextField[PostalCodeDetail::class.java] = postalCodeField
        val countryField = findViewById<TextView>(R.id.ci_CountryField)
        detailToTextField[CountryDetail::class.java] = countryField

        // Email details.
        val emailField = findViewById<TextView>(R.id.ci_EMailField)
        detailToTextField[EmailAddressDetail::class.java] = emailField
        val workEmailField = findViewById<TextView>(R.id.ci_WorkEmailField)
        detailToTextField[WorkEmailAddressDetail::class.java] = workEmailField

        // Phone number details.
        val phoneField = findViewById<TextView>(R.id.ci_PhoneField)
        detailToTextField[PhoneNumberDetail::class.java] = phoneField
        val workPhoneField = findViewById<TextView>(R.id.ci_WorkPhoneField)
        detailToTextField[WorkPhoneDetail::class.java] = workPhoneField
        val mobilePhoneField = findViewById<TextView>(R.id.ci_MobilePhoneField)
        detailToTextField[MobilePhoneDetail::class.java] = mobilePhoneField
        val organizationField = findViewById<TextView>(R.id.ci_OrganizationNameField)
        detailToTextField[WorkOrganizationNameDetail::class.java] = organizationField
        val jobTitleField = findViewById<TextView>(R.id.ci_JobTitleField)
        detailToTextField[JobTitleDetail::class.java] = jobTitleField
        val aboutMeArea = findViewById<TextView>(R.id.ci_AboutMeField)
        val filterArray = arrayOfNulls<InputFilter>(1)
        filterArray[0] = LengthFilter(ABOUT_ME_MAX_CHARACTERS)
        aboutMeArea.filters = filterArray
        aboutMeArea.setBackgroundResource(R.drawable.alpha_blue_01)
        detailToTextField[AboutMeDetail::class.java] = aboutMeArea
        val mOkButton = findViewById<Button>(R.id.button_OK)
        mOkButton.setOnClickListener { v: View? -> finish() }
    }

    override fun detailsRetrieved(detailIterator: Iterator<GenericDetail?>?) {
        Handler(Looper.getMainLooper()).post {
            if (detailIterator != null) {
                while (detailIterator.hasNext()) {
                    val detail = detailIterator.next()
                    loadDetail(detail)
                }
            }
        }
    }

    /**
     * Loads a single `GenericDetail` obtained from the
     * `OperationSetServerStoredAccountInfo` into this plugin.
     *
     * @param detail to be loaded.
     */
    private fun loadDetail(detail: GenericDetail?) {
        if (detail is BinaryDetail) {
            val avatarView = findViewById<ImageView>(R.id.contactAvatar)

            // If the user has a contact image, let's use it. If not, leave the default as it
            val avatarImage = detail.getDetailValue() as ByteArray?
            val bitmap = BitmapFactory.decodeByteArray(avatarImage, 0, avatarImage!!.size)
            avatarView.setImageBitmap(bitmap)
        } else if (detail is URLDetail) {
            // If the contact's protocol supports web info, give them a link to get it
            val urlString = detail.getURL().toString()
            // urlField.setText(urlString);
            val html = ("Click to see web info for: <a href='"
                    + urlString + "'>"
                    + urlString
                    + "</a>")
            urlField!!.text = Html.fromHtml(html)
            urlField!!.setOnClickListener { v: View? ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                startActivity(browserIntent)
            }
        } else if (detail is BirthDateDetail) {
            // birthDateDetail = (BirthDateDetail) detail;
            val calendarDetail = detail.getDetailValue() as Calendar?
            val birthDate = calendarDetail!!.time
            val dateFormat = DateFormat.getDateInstance()
            val birthDateDetail = dateFormat.format(birthDate)
            birthDateField!!.text = birthDateDetail

            // Calculate age based on given birthDate
            val mDate = Calendar.getInstance()
            var age = mDate[Calendar.YEAR] - calendarDetail[Calendar.YEAR]
            if (mDate[Calendar.MONTH] < calendarDetail[Calendar.MONTH]) age--
            if (mDate[Calendar.MONTH] == calendarDetail[Calendar.MONTH]
                    && (mDate[Calendar.DAY_OF_MONTH]
                            < calendarDetail[Calendar.DAY_OF_MONTH])) age--
            val ageDetail = age.toString()
            ageField!!.text = ageDetail
        } else {
            val field = detailToTextField[detail!!.javaClass]
            if (field != null) {
                val obj = detail.getDetailValue()
                if (obj is String) field.text = obj else if (obj != null) field.text = obj.toString()
            }
        }
    }

    companion object {
        /**
         * Intent's extra's key for account ID property of this activity
         */
        const val INTENT_CONTACT_ID = "contact_id"
        const val ABOUT_ME_MAX_CHARACTERS = 200
    }
}