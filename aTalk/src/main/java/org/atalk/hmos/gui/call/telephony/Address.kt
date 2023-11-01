package org.atalk.hmos.gui.call.telephony

import android.text.TextUtils
import android.text.util.Rfc822Tokenizer
import androidx.annotation.VisibleForTesting
import org.apache.james.mime4j.codec.EncoderUtil
import org.jivesoftware.smack.util.StringUtils
import timber.log.Timber
import java.io.Serializable
import java.util.regex.Pattern

/*
 *  Implementation of Address as [mAddress, mPerson] where:
 *  mAddress = Contact Phone Number
 *  mPerson = Contact Name
 */
class Address private constructor(address: String?, person: String?, parse: Boolean) : Serializable {
    private var mAddress: String? = null
    private var mPerson: String? = null

    constructor(address: String?, person: String?) : this(address, person, true)

    init {
        if (parse) {
            val tokens = Rfc822Tokenizer.tokenize(address)
            if (tokens.isNotEmpty()) {
                val token = tokens[0]
                mAddress = token.address
                val name = token.name

                /*
                 * Don't use the "person" argument if "address" is of the form:
                 * James Bond <james.bond@mi6.uk>
                 * See issue 2920
                 */
                mPerson = StringUtils.returnIfNotEmptyTrimmed(name)
                if (mPerson == null && person != null) mPerson = person.trim { it <= ' ' }
            } else {
                Timber.e("Invalid address: %s", address)
            }
        } else {
            mAddress = address
            mPerson = person
        }
    }

    fun getAddress(): String? {
        return mAddress
    }

    fun setAddress(address: String?) {
        mAddress = address
    }

    fun getPerson(): String? {
        return mPerson
    }

    fun setPerson(person: String?) {
        mPerson = StringUtils.returnIfNotEmptyTrimmed(person)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val address = other as Address
        if (if (mAddress != null) mAddress != address.mAddress else address.mAddress != null) {
            return false
        }
        return if (mPerson != null) mPerson == address.mPerson else address.mPerson == null
    }

    override fun hashCode(): Int {
        var hash = 0
        if (mAddress != null) {
            hash += mAddress.hashCode()
        }
        if (mPerson != null) {
            hash += 3 * mPerson.hashCode()
        }
        return hash
    }

    override fun toString(): String {
        return if (!TextUtils.isEmpty(mPerson)) {
            quoteAtoms(mPerson) + " <" + mAddress + ">"
        } else {
            mAddress!!
        }
    }

    private fun toEncodedString(): String? {
        return if (!TextUtils.isEmpty(mPerson)) {
            EncoderUtil.encodeAddressDisplayName(mPerson) + " <" + mAddress + ">"
        } else {
            mAddress
        }
    }

    companion object {
        private val ATOM = Pattern.compile("^(?:[a-zA-Z\\d!#$%&'*+\\-/=?^_`{|}~]|\\s)+$")

        /**
         * Immutable empty [Address] array
         */
        private val EMPTY_ADDRESS_ARRAY = arrayOfNulls<Address>(0)

        /**
         * Parse a comma separated list of phone addresses in human readable format and return an
         * array of Address objects, RFC-822 encoded.
         *
         * @param addressList List of addresses
         * @return An array of 0 or more Addresses.
         */
        fun parseUnencoded(addressList: String?): Array<Address> {
            val addresses = ArrayList<Address>()
            if (StringUtils.isNotEmpty(addressList)) {
                val tokens = Rfc822Tokenizer.tokenize(addressList)
                for (token in tokens) {
                    val address = token.address
                    if (!TextUtils.isEmpty(address)) {
                        addresses.add(Address(token.address, token.name, false))
                    }
                }
            }
            return addresses.toArray(EMPTY_ADDRESS_ARRAY)
        }

        /**
         * Parse a comma separated list of addresses in RFC-822 format and return an array of Address objects.
         *
         * @param addressList List of addresses
         * @return An array of 0 or more Addresses.
         */
        fun parse(addressList: String?): Array<Address?> {
            if (StringUtils.isEmpty(addressList)) {
                return EMPTY_ADDRESS_ARRAY
            }
            // parseUnencoded(addressList);
            val addresses = ArrayList<Address>()
            return addresses.toArray(EMPTY_ADDRESS_ARRAY)
        }

        fun toString(addresses: Array<Address?>?): String? {
            return if (addresses == null) {
                null
            } else TextUtils.join(", ", addresses)
        }

        fun toEncodedString(addresses: Array<Address>?): String? {
            if (addresses == null) {
                return null
            }
            val sb = StringBuilder()
            for (i in addresses.indices) {
                sb.append(addresses[i].toEncodedString())
                if (i < addresses.size - 1) {
                    sb.append(',')
                }
            }
            return sb.toString()
        }

        /**
         * Unpacks an address list previously packed with packAddressList()
         *
         * @param addressList Packed address list.
         * @return Unpacked list.
         */
        fun unpack(addressList: String?): Array<Address> {
            if (addressList == null) {
                return arrayOf()
            }
            val addresses = ArrayList<Address>()
            val length = addressList.length
            var pairStartIndex = 0
            var pairEndIndex: Int
            var addressEndIndex: Int
            while (pairStartIndex < length) {
                pairEndIndex = addressList.indexOf(",\u0000", pairStartIndex)
                if (pairEndIndex == -1) {
                    pairEndIndex = length
                }
                addressEndIndex = addressList.indexOf(";\u0000", pairStartIndex)
                var address: String
                var person: String? = null
                if (addressEndIndex == -1 || addressEndIndex > pairEndIndex) {
                    address = addressList.substring(pairStartIndex, pairEndIndex)
                } else {
                    address = addressList.substring(pairStartIndex, addressEndIndex)
                    person = addressList.substring(addressEndIndex + 2, pairEndIndex)
                }
                addresses.add(Address(address, person, false))
                pairStartIndex = pairEndIndex + 2
            }
            return addresses.toTypedArray()
        }

        /**
         * Packs an address list into a String that is very quick to read
         * and parse. Packed lists can be unpacked with unpackAddressList()
         * The packed list is a ",\u0000" separated list of: address;\u0000person
         *
         * @param addresses Array of addresses to pack.
         * @return Packed addresses.
         */
        fun pack(addresses: Array<Address>?): String? {
            if (addresses == null) {
                return null
            }
            val sb = StringBuilder()
            var i = 0
            val count = addresses.size
            while (i < count) {
                val address = addresses[i]
                sb.append(address.getAddress())
                var person = address.getPerson()
                if (person != null) {
                    sb.append(";\u0000")
                    // Escape quotes in the address part on the way in
                    person = person.replace("\"".toRegex(), "\\\"")
                    sb.append(person)
                }
                if (i < count - 1) {
                    sb.append(",\u0000")
                }
                i++
            }
            return sb.toString()
        }

        /**
         * Quote a string, if necessary, based upon the definition of an "atom," as defined by RFC2822
         * (https://tools.ietf.org/html/rfc2822#section-3.2.4). Strings that consist purely of atoms are
         * left unquoted; anything else is returned as a quoted string.
         *
         * @param text String to quote.
         * @return Possibly quoted string.
         */
        private fun quoteAtoms(text: String?): String? {
            return if (ATOM.matcher(text).matches()) {
                text
            } else {
                quoteString(text)
            }
        }

        /**
         * Ensures that the given string starts and ends with the double quote character. The string is not modified in any way except to add the
         * double quote character to start and end if it's not already there.
         * sample -> "sample"
         * "sample" -> "sample"
         * ""sample"" -> ""sample""
         * "sample"" -> "sample"
         * sa"mp"le -> "sa"mp"le"
         * "sa"mp"le" -> "sa"mp"le"
         * (empty string) -> ""
         * " -> """
         *
         * @param s
         * @return
         */
        @VisibleForTesting
        fun quoteString(s: String?): String? {
            if (s == null) {
                return null
            }
            return if (!s.matches(Regex("^\".*\"$"))) {
                "\"" + s + "\""
            } else {
                s
            }
        }
    }
}