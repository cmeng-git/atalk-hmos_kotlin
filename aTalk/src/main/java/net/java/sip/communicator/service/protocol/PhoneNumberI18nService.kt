/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * Implements `PhoneNumberI18nService` which aids the parsing, formatting
 * and validating of international phone numbers.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
interface PhoneNumberI18nService {
    /**
     * Normalizes a `String` which may be a phone number or a identifier by removing useless
     * characters and, if necessary, replacing the alpahe characters in corresponding dial pad
     * numbers.
     *
     * @param possibleNumber a `String` which may represents a phone number or an identifier to normalize.
     * @return a `String` which is a normalized form of the specified `possibleNumber`
     * .
     */
    fun normalize(possibleNumber: String?): CharSequence

    /**
     * Tries to format the passed phone number into the international format. If
     * parsing fails or the string is not recognized as a valid phone number,
     * the input is returned as is.
     *
     * @param phoneNumber The phone number to format.
     * @return the formatted phone number in the international format.
     */
    fun formatForDisplay(phoneNumber: String?): String?

    /**
     * Determines whether two `String` phone numbers match.
     *
     * @param aPhoneNumber a `String` which represents a phone number to match to `bPhoneNumber`
     * @param bPhoneNumber a `String` which represents a phone number to match to `aPhoneNumber`
     * @return `true` if the specified `String`s match as phone numbers; otherwise,
     * `false`
     */
    fun phoneNumbersMatch(aPhoneNumber: String?, bPhoneNumber: String?): Boolean

    /**
     * Indicates if the given string is possibly a phone number.
     *
     * @param possibleNumber the string to be verified
     * @return `true` if the possibleNumber is a phone number, `false` - otherwise
     */
    fun isPhoneNumber(possibleNumber: String?): Boolean
}