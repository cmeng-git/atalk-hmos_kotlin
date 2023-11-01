/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.phonenumbers

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import net.java.sip.communicator.service.protocol.PhoneNumberI18nService
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator
import java.util.regex.Pattern

/**
 * Implements `PhoneNumberI18nService` which aids the parsing, formatting and validating of international phone
 * numbers.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
class PhoneNumberI18nServiceImpl : PhoneNumberI18nService {
    /**
     * Normalizes a `String` which may be a phone number or a identifier by removing useless characters and, if
     * necessary, replacing the alpahe characters in corresponding dial pad numbers.
     *
     * @param possibleNumber a `String` which may represents a phone number or an identifier to normalize.
     * @return a `String` which is a normalized form of the specified `possibleNumber`.
     */
    override fun normalize(possibleNumber: String?): String {
        val normalizedNumber: String
        normalizedNumber = if (isPhoneNumber(possibleNumber)) {
            normalizePhoneNumber(possibleNumber)
        } else {
            normalizeIdentifier(possibleNumber)
        }
        return normalizedNumber
    }

    /**
     * Determines whether two `String` phone numbers match.
     *
     * @param aPhoneNumber a `String` which represents a phone number to match to `bPhoneNumber`
     * @param bPhoneNumber a `String` which represents a phone number to match to `aPhoneNumber`
     * @return `true` if the specified `String`s match as phone numbers; otherwise, `false`
     */
    override fun phoneNumbersMatch(aPhoneNumber: String?, bPhoneNumber: String?): Boolean {
        val match = PhoneNumberUtil.getInstance().isNumberMatch(aPhoneNumber, bPhoneNumber)
        return match != PhoneNumberUtil.MatchType.NOT_A_NUMBER && match != PhoneNumberUtil.MatchType.NO_MATCH
    }

    /**
     * Tries to format the passed phone number into the international format. If
     * parsing fails or the string is not recognized as a valid phone number,
     * the input is returned as is.
     *
     * @param phoneNumber The phone number to format.
     * @return the formatted phone number in the international format.
     */
    override fun formatForDisplay(phoneNumber: String?): String? {
        try {
            val pn = PhoneNumberUtil.getInstance().parse(phoneNumber,
                    System.getProperty("user.country"))
            if (PhoneNumberUtil.getInstance().isPossibleNumber(pn)) {
                return PhoneNumberUtil.getInstance().format(pn,
                        PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            }
        } catch (e: NumberParseException) {
        }
        return phoneNumber
    }

    /**
     * Indicates if the given string is possibly a phone number.
     *
     * @param possibleNumber the string to be verified
     * @return `true` if the possibleNumber is a phone number, `false` - otherwise
     */
    override fun isPhoneNumber(possibleNumber: String?): Boolean {
        // If the string does not contains an "@", this may be a phone number.
        if (possibleNumber!!.indexOf('@') == -1) {
            // If the string does not contain any alphabetical characters, then this is a phone number.
            if (!possibleNumber.matches(Regex(".*[a-zA-Z].*"))) {
                return true
            } else {
                // Removes the " ", "(" and ")" in order to search the "+" character at the beginning at the string.
                val tmpPossibleNumber = possibleNumber.replace(" \\(\\)".toRegex(), "")
                // If the property is enabled and the string starts with a "+", then we consider that this is a phone
                // number.
                if (configService!!.getBoolean("impl.gui.ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS", true) && tmpPossibleNumber.startsWith("+")) {
                    return true
                }
            }
        }
        // Else the string is not a phone number.
        return false
    }

    companion object {
        /**
         * The configuration service.
         */
        private val configService = ProtocolProviderActivator.getConfigurationService()

        /**
         * Characters which have to be removed from a phone number in order to normalized it.
         */
        private val removedCharactersToNormalizedPhoneNumber = Pattern.compile("[-().\\\\/ ]")

        /**
         * Characters which have to be removed from a number (which is not a phone number, such as a sip id, a jabber id,
         * etc.) in order to normalized it.
         */
        private val removedCharactersToNormalizedIdentifier = Pattern.compile("[() ]")

        /**
         * The list of characters corresponding to the number 2 in a phone dial pad.
         */
        private val charactersFordialPadNumber2 = Pattern.compile("[abc]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 3 in a phone dial pad.
         */
        private val charactersFordialPadNumber3 = Pattern.compile("[def]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 4 in a phone dial pad.
         */
        private val charactersFordialPadNumber4 = Pattern.compile("[ghi]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 5 in a phone dial pad.
         */
        private val charactersFordialPadNumber5 = Pattern.compile("[jkl]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 6 in a phone dial pad.
         */
        private val charactersFordialPadNumber6 = Pattern.compile("[mno]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 7 in a phone dial pad.
         */
        private val charactersFordialPadNumber7 = Pattern.compile("[pqrs]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 8 in a phone dial pad.
         */
        private val charactersFordialPadNumber8 = Pattern.compile("[tuv]", Pattern.CASE_INSENSITIVE)

        /**
         * The list of characters corresponding to the number 9 in a phone dial pad.
         */
        private val charactersFordialPadNumber9 = Pattern.compile("[wxyz]", Pattern.CASE_INSENSITIVE)

        /**
         * Normalizes a `String` phone number by converting alpha characters to their respective digits on a keypad
         * and then stripping non-digit characters.
         *
         * @param phoneNumber a `String` which represents a phone number to normalize
         * @return a `String` which is a normalized form of the specified `phoneNumber`
         * @see net.java.sip.communicator.impl.phonenumbers.PhoneNumberI18nServiceImpl.normalize
         */
        private fun normalizePhoneNumber(phoneNumber_: String?): String {
            var phoneNumber = phoneNumber_
            phoneNumber = convertAlphaCharactersInNumber(phoneNumber)
            return removedCharactersToNormalizedPhoneNumber.matcher(phoneNumber).replaceAll("")
        }

        /**
         * Removes useless characters from a identifier (which is not a phone number) in order to normalized it.
         *
         * @param id The identifier string with some useless characters like: " ", "(", ")".
         * @return The normalized identifier.
         */
        private fun normalizeIdentifier(id: String?): String {
            return removedCharactersToNormalizedIdentifier.matcher(id as CharSequence).replaceAll("")
        }

        /**
         * Changes all alphabetical characters into numbers, following phone dial pad disposition.
         *
         * @param phoneNumber The phone number string with some alphabetical characters.
         * @return The phone number with all alphabetical caracters replaced with the corresponding dial pad number.
         */
        private fun convertAlphaCharactersInNumber(phoneNumber_: String?): String {
            var phoneNumber = phoneNumber_.toString()
            phoneNumber = charactersFordialPadNumber2.matcher(phoneNumber).replaceAll("2")
            phoneNumber = charactersFordialPadNumber3.matcher(phoneNumber).replaceAll("3")
            phoneNumber = charactersFordialPadNumber4.matcher(phoneNumber).replaceAll("4")
            phoneNumber = charactersFordialPadNumber5.matcher(phoneNumber).replaceAll("5")
            phoneNumber = charactersFordialPadNumber6.matcher(phoneNumber).replaceAll("6")
            phoneNumber = charactersFordialPadNumber7.matcher(phoneNumber).replaceAll("7")
            phoneNumber = charactersFordialPadNumber8.matcher(phoneNumber).replaceAll("8")
            return charactersFordialPadNumber9.matcher(phoneNumber).replaceAll("9")
        }
    }
}