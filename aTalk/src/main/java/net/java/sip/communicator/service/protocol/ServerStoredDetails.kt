/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

import org.jivesoftware.smack.util.StringUtils
import java.math.BigDecimal
import java.net.URL
import java.util.*

/**
 * The ServerStoredDetails class contains a relatively large set of details that various protocols
 * may support storing online. Each detail is represented by its own inner class that could be
 * instantiated by either the protocol provider implementation (for representing details returned by
 * the server) or by the using service (e.g. the UIService, when representing details that the local
 * user would like to set for the current account).
 *
 *
 * All detail classes inherit from the GenericDetail class, extending it to represent more and more
 * concrete details. The WorkAddressDetail for example, meant to represent a postal work address,
 * extends the AddressDetailClass which is meant for representing all kinds of postal addresses. The
 * AddressDetailClass on its turn extends the StringDetail which itself inherits from the
 * GenericDetailClass.
 *
 *
 * When creating details that do not exist here (which you'll probably have to do at one point or
 * another) you are encouraged to extend from the most concrete address possible so that your detail
 * could be meaningfully handled by the User Interface.
 *
 *
 * Let's assume for example that we'd like to add a BirthPlaceAddressDetail, indicating place of
 * birth. The BirthPlaceAddress detail class should extend the AddressDetail class so that the GUI
 * could understand that this is an address and visualize it appropriately. The same goes for
 * variations of an EmailAddressDetail or any other detail having anything to do with a detail
 * represented in this class.
 *
 *
 * All details have a detailValue and a displayName as well as get methods that would give you
 * (read-only) access to them. Most classes extending the GenericDetail to something more meaningful
 * would provide additional accessors allowing you to retrieve the value casted to its native class.
 *
 *
 * Detail names may be used when visualizing the detail (however, keep in mind that you should leave
 * space for internationalization when doing so).
 *
 *
 * This class is meant for usage with the OperationSetServerStoredAccountInfo and
 * OperationSetServerStoredContactInfo operation sets.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class ServerStoredDetails {
    /**
     * A generic detail used as the root of all other server stored details. This class should be extended
     * or instantiated by implementors with the purpose of representing details not defined here.
     */
    open class GenericDetail(detailDisplayName: String?, value: Any?) {
        /**
         * Returns a display name that may be used as a description when visualizing the value of
         * the detail (make sure you don't use this string in internationalized versions).
         */
        protected var detailDisplayName: String?

        protected var value: Any?

        /**
         * Instantiates this detail setting its value and display name accordingly.
         */
        init {
            this.detailDisplayName = detailDisplayName
            this.value = value
        }

        /**
         * Returns the value of the detail.
         *
         * @return the value of the detail.
         */
        fun getDetailValue(): Any? {
            return value
        }

        /**
         * Returns a String representation of the detail using both its value and display name.
         *
         * @return a String representation of the detail using both its value and display name.
         */
        override fun toString(): String {
            return if (value == null) "" else value.toString()
        }

        /**
         * Compares two GenericDetails according their DetailDisplayName and Value
         *
         * @param other Object expected GenericDetail otherwise return false
         * @return `true` if this object has the same display name and value as `obj`
         * and false otherwise
         */
        override fun equals(other: Any?): Boolean {
            if (other !is GenericDetail) return false
            if (this === other) {
                return true
            }
            return (detailDisplayName != null && other.detailDisplayName != null && detailDisplayName == other.detailDisplayName)  // equals not null values
                    &&  (value != null && other.getDetailValue() != null && value == other.getDetailValue()
                    || (StringUtils.isEmpty(value as String) && StringUtils.isEmpty(other.getDetailValue() as String)))
        }

        override fun hashCode(): Int {
            return Objects.hash(detailDisplayName, value)
        }
    }

    /**
     * A generic detail that should be used (extended) when representing details with a String
     * content.
     */
    open class StringDetail(detailDisplayName: String?, value: String?) : GenericDetail(detailDisplayName, value) {
        fun getString(): String? {
            return value as String?
        }
    }
    // ---------------------- physical addresses -----------------------------------
    /**
     * A detail representing an address (street and street/house number).
     */
    open class AddressDetail(address: String?) : StringDetail("Address", address) {
        fun getAddress(): String {
            return getString()!!
        }
    }

    /**
     * A detail representing a street name and number associated with a work address.
     */
    class WorkAddressDetail(address: String?) : AddressDetail(address)

    /**
     * A City name associated with a (home) address.
     */
    open class CityDetail(cityName: String?) : StringDetail("City", cityName) {
        fun getCity(): String {
            return getString()!!
        }
    }

    /**
     * A City name associated with a work address.
     */
    class WorkCityDetail(cityName: String?) : CityDetail(cityName)

    /**
     * The name of a state/province/region associated with a (home) address.
     */
    open class ProvinceDetail(province: String?) : StringDetail("Region/Province/State", province) {
        fun getProvince(): String {
            return getString()!!
        }
    }

    /**
     * The name of a state/province/region associated with a work address.
     */
    class WorkProvinceDetail(workProvince: String?) : ProvinceDetail(workProvince)

    /**
     * A postal or ZIP code associated with a (home) address.
     */
    open class PostalCodeDetail(postalCode: String?) : StringDetail("Postal/Zip Code", postalCode) {
        fun getPostalCode(): String {
            return getString()!!
        }
    }

    /**
     * A postal or ZIP code associated with a work address.
     */
    class WorkPostalCodeDetail(postalCode: String?) : PostalCodeDetail(postalCode)
    // ------------------------------ LOCALE DETAILS --------------------------------
    /**
     * A generic detail that should be used (extended) when representing details that have anything
     * to do with locales (countries, languages, etc). Most of the locales field could be ignored
     * when extending the class. When representing a country for example we'd only be using the
     * fields concerning the country.
     */
    open class LocaleDetail(detailDisplayName: String?, locale: Locale?) : GenericDetail(detailDisplayName, locale) {
        open fun getLocale(): Locale? {
            return getDetailValue() as Locale?
        }
    }

    /**
     * A detail representing a country of residence for the corresponding subject.
     */
    open class CountryDetail : LocaleDetail {
        constructor(locale: Locale?) : super("Country", locale) {}
        constructor(country: String?) : super("Country", null) {
            value = country
        }

        override fun getLocale(): Locale? {
            return if (value is Locale) value as Locale? else null
        }
    }

    /**
     * The name of a country associated with a work address.
     */
    class WorkCountryDetail : CountryDetail {
        constructor(locale: Locale?) : super(locale) {}
        constructor(country: String?) : super(country) {}
    }
    // -------------------------------- Language ------------------------------------
    /**
     * A locale detail indicating a language spoken by the corresponding Contact.
     */
    class SpokenLanguageDetail(language: Locale?) : LocaleDetail("Language", language)
    // ------------------------- phones --------------------------------------------
    /**
     * A generic detail used for representing a (personal) phone number.
     */
    open class PhoneNumberDetail(number: String?) : StringDetail("Phone", number) {
        fun getNumber(): String? {
            return getString()!!
        }
    }

    /**
     * A detail used for representing a work phone number.
     */
    class WorkPhoneDetail(workPhone: String?) : PhoneNumberDetail(workPhone) {
        init {
            super.detailDisplayName = "Work Phone"
        }
    }

    /**
     * A detail used for representing a (personal) mobile phone number.
     */
    open class MobilePhoneDetail(privateMobile: String?) : PhoneNumberDetail(privateMobile) {
        init {
            super.detailDisplayName = "Mobile Phone"
        }
    }

    /**
     * A detail used for representing a work mobile phone number.
     */
    class WorkMobilePhoneDetail(workMobile: String?) : MobilePhoneDetail(workMobile)

    /**
     * A Fax number.
     */
    open class FaxDetail(number: String?) : PhoneNumberDetail(number) {
        init {
            super.detailDisplayName = "Fax"
        }
    }

    /**
     * A detail used for representing a video phone number.
     */
    open class VideoDetail(number: String?) : PhoneNumberDetail(number) {
        init {
            super.detailDisplayName = "Video"
        }
    }

    /**
     * A detail used for representing a work video phone number.
     */
    class WorkVideoDetail(number: String?) : VideoDetail(number)

    /**
     * A Pager number.
     */
    class PagerDetail(number: String?) : PhoneNumberDetail(number) {
        init {
            super.detailDisplayName = "Pager"
        }
    }
    // ----------------------------- web page ---------------------------------------
    /**
     * A generic detail representing any url
     */
    open class URLDetail(name: String?, url: Any?) : GenericDetail(name, url) {
        fun getURL(): Any {
            return getDetailValue()!!
        }

        /**
         * Compares two URLDetails according their name and URLs
         *
         * @param other Object expected URLDetail otherwise return false
         * @return `true` if this object has the same name and URL value as `obj` and
         * false otherwise
         */
        override fun equals(other: Any?): Boolean {
            if (other !is URLDetail) return false
            if (this === other) {
                return true
            }
            val other = other
            val equalsDisplayName = detailDisplayName != null && other.detailDisplayName != null && detailDisplayName == other.detailDisplayName
            val equalValues = value != null && other.getDetailValue() != null && value == other.getDetailValue()
            val bothNullValues = value == null && other.value == null
            return if (equalsDisplayName && (equalValues || bothNullValues)) true else false
        }
    }

    /**
     * A personal web page.
     */
    open class WebPageDetail(url: URL?) : URLDetail("Web Page", url)

    /**
     * A web page associated with the subject's principal occupation (work).
     */
    class WorkPageDetail(url: URL?) : WebPageDetail(url)
    // --------------------------- Binary -------------------------------------------
    /**
     * A generic detail used for representing binary content such as photos logos, avatars ....
     */
    open class BinaryDetail(displayDetailName: String?, bytes: ByteArray?) : GenericDetail(displayDetailName, bytes) {
        fun getBytes(): ByteArray {
            return getDetailValue() as ByteArray
        }

        /**
         * Compares two BinaryDetails according their DetailDisplayName and the result of invoking
         * their getBytes() methods.
         *
         * @param other Object expected BinaryDetail otherwise return false
         * @return `true` if this object has the same display name and value as `obj`
         * and false otherwise
         */
        override fun equals(other: Any?): Boolean {
            if (other !is BinaryDetail) return false
            if (this === other) {
                return true
            }
            val other = other
            val equalsDisplayName = detailDisplayName != null && other.detailDisplayName != null && detailDisplayName == other.detailDisplayName
            val equalsNotNull = (value != null && other.getDetailValue() != null
                    && Arrays.equals(getBytes(), other.getBytes()))
            val nullOrEmpty = ((value == null || getBytes().isEmpty())
                    && (other.getDetailValue() == null || other.getBytes().isEmpty()))
            return equalsDisplayName && (equalsNotNull || nullOrEmpty)
        }
    }

    /**
     * A detail containing any contact related images.
     */
    class ImageDetail(detailDisplayName: String?, image: ByteArray?) : BinaryDetail(detailDisplayName, image)
    // -------------------------- Names ---------------------------------------------
    /**
     * A generic detail representing any kind of name.
     */
    open class NameDetail(detailDisplayName: String?, name: String?) : StringDetail(detailDisplayName, name) {
        fun getName(): String {
            return getString()!!
        }
    }

    /**
     * The name of the organization (company, ngo, university, hospital or other) employing the
     * corresponding contact.
     */
    class WorkOrganizationNameDetail(workOrganizationName: String?) : NameDetail("Work Organization Name", workOrganizationName)

    /**
     * A first, given name.
     */
    class FirstNameDetail(firstName: String?) : NameDetail("First Name", firstName)

    /**
     * A Middle (father's) name.
     */
    class MiddleNameDetail(middleName: String?) : NameDetail("Middle Name", middleName)

    /**
     * A last (family) name.
     */
    class LastNameDetail(lastName: String?) : NameDetail("Last Name", lastName)

    /**
     * The name that should be displayed to identify the information author.
     */
    class DisplayNameDetail(name: String?) : NameDetail("Display Name", name)

    /**
     * An informal name (nickname) used for referring to the subject.
     */
    class NicknameDetail(name: String?) : NameDetail("Nickname", name)

    /**
     * A bi-state detail indicating a gender. Constructor is private and the only possible instances
     * are GenderDetail.MALE and GenderDetail.FEMALE construction.
     */
    class GenderDetail(gender: String?) : StringDetail("Gender", gender) {
        /**
         * Returns a "Male" or "Female" string.
         *
         * @return a String with a "Male" or "Female" contents
         */
        fun getGender(): String {
            return getString()!!
        }

        companion object {
            val MALE = GenderDetail("Male")
            val FEMALE = GenderDetail("Female")
        }
    }
    // -------------------------------- Date & Time ---------------------------------
    /**
     * A generic detail meant to represent any date (calendar) associated details. Protocols that
     * support separate fields for year, month, day and time, or even age should try their best to
     * convert to a date (setting to 0 all unknown details).
     */
    open class CalendarDetail(detailDisplayName: String?, date: Any?) : GenericDetail(detailDisplayName, date) {
        fun getCalendar(): Any {
            return getDetailValue()!!
        }
    }

    /**
     * A complete birth date.
     */
    class BirthDateDetail(date: Any?) : CalendarDetail("Birth Date", date) {
        /**
         * Compares two BirthDateDetails according to their Calender's year, month and day.
         *
         * @param other Object expected BirthDateDetail otherwise return false
         * @return `true` if this object has the same value as `obj` and false otherwise
         */
        override fun equals(other: Any?): Boolean {
            if (other !is BirthDateDetail) return false
            if (this === other) {
                return true
            }
            val other = other

            // both null dates
            if (value == null && other.getDetailValue() == null) return true
            return if (value != null && other.getDetailValue() != null) {
                if (value is Calendar) {
                    val yearEquals = ((value as Calendar)[Calendar.YEAR]
                            == (other.value as Calendar)[Calendar.YEAR])
                    val monthEquals = ((value as Calendar)[Calendar.MONTH]
                            == (other.value as Calendar)[Calendar.MONTH])
                    val dayEquals = ((value as Calendar)[Calendar.DAY_OF_MONTH]
                            == (other.value as Calendar)[Calendar.DAY_OF_MONTH])
                    yearEquals && monthEquals && dayEquals
                } else {
                    value == other.value
                }
            } else false
        }

    }

    /**
     * A generic detail meant to represent the time zone associated with the corresponding contact
     * and that could be extended to represent other time zone related details.
     */
    class TimeZoneDetail(displayDetailName: String?, timeZone: TimeZone?) : GenericDetail(displayDetailName, timeZone) {
        fun getTimeZone(): TimeZone {
            return getDetailValue() as TimeZone
        }
    }
    // ------------------------------- E-Mails ------------------------------------
    /**
     * Represents a (personal) email address.
     */
    open class EmailAddressDetail protected constructor(detailDisplayName: String?, value: String?) : StringDetail(detailDisplayName, value) {
        constructor(value: String?) : this("e-mail", value) {}

        fun getEMailAddress(): String {
            return getString()!!
        }
    }

    /**
     * Represents a (personal) email address.
     */
    class WorkEmailAddressDetail(value: String?) : EmailAddressDetail("Work e-mail", value)
    // ----------------------------- Interests -------------------------------------
    /**
     * Represents a personal interest or hobby.
     */
    class InterestDetail(value: String?) : StringDetail("Interest", value) {
        fun getInterest(): String {
            return getString()!!
        }
    }
    // ---------------------------- Numbers -----------------------------------------
    /**
     * A generic detail that should be used (extended) when representing any numbers.
     */
    class NumberDetail(detailName: String?, value: BigDecimal?) : GenericDetail(detailName, value) {
        fun getNumber(): BigDecimal {
            return getDetailValue() as BigDecimal
        }
    }
    // ---------------------------- Numbers -----------------------------------------
    /**
     * A generic detail that should be used (extended) when representing any boolean values.
     */
    class BooleanDetail(detailName: String?, value: Boolean) : GenericDetail(detailName, value) {
        fun getBoolean(): Boolean {
            return getDetailValue() as Boolean
        }
    }
    // ---------------------------- Others ------------------------------------------
    /**
     * A job title.
     */
    class JobTitleDetail(jobTitle: String?) : StringDetail("Job Title", jobTitle)

    /**
     * Represents a (personal) "about me" short description.
     */
    class AboutMeDetail(description: String?) : StringDetail("Description", description)
}