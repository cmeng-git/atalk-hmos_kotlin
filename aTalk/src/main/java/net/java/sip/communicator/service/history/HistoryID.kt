/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

/**
 * Object used to uniquely identify a group of history records.
 *
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
class HistoryID private constructor(private val id: Array<String?>) {
    private val stringRepresentation: String
    private val hashCode: Int

    init {
        val buff = StringBuilder()
        for (i in id.indices) {
            if (i > 0) buff.append(' ')
            buff.append(id[i])
        }
        stringRepresentation = buff.toString()
        hashCode = stringRepresentation.hashCode()
    }

    fun getID(): Array<String?> {
        return id
    }

    override fun toString(): String {
        return stringRepresentation
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        var eq = false
        if (other is HistoryID) {
            val id = other.id
            if (this.id.size == id.size) {
                eq = true
                for (i in id.indices) {
                    val s1 = id[i]
                    val s2 = this.id[i]
                    if (s1 != s2 && (s1 == null || s1 != s2)) {
                        eq = false
                        break
                    }
                }
            }
        }
        return eq
    }

    companion object {
        /**
         * Create a HistoryID from a raw ID. You can pass any kind of strings and they will be safely
         * converted to valid IDs.
         */
        fun createFromRawID(rawid: Array<String>): HistoryID {
            // TODO: Validate: Assert.assertNonNull(rawid, "Parameter RAWID should be non-null");
            // TODO: Validate: Assert.assertTrue(rawid.length > 0, "RAWID.length should be > 0");
            val id = arrayOfNulls<String>(rawid.size)
            for (i in rawid.indices) {
                id[i] = readableHash(rawid[i])
            }
            return HistoryID(id)
        }

        /**
         * Create a HistoryID from a raw Strings. You can pass any kind of strings and they will be
         * checked and converted to valid IDs.
         */
        fun createFromRawStrings(rawStrings: Array<String>): HistoryID {
            val id = arrayOfNulls<String>(rawStrings.size)
            for (i in rawStrings.indices) {
                id[i] = decodeReadableHash(rawStrings[i])
            }
            return HistoryID(id)
        }

        /**
         * Create a HistoryID from a valid ID. You should pass only valid IDs (ones produced from
         * readableHash).
         *
         * @throws IllegalArgumentException
         * Thrown if a string from the ID is not valid an exception.
         */
        @Throws(java.lang.IllegalArgumentException::class)
        fun createFromID(id: Array<String?>): HistoryID {
            // TODO: Validate: Assert.assertNonNull(id, "Parameter ID should be non-null");
            // TODO: Validate: Assert.assertTrue(id.length > 0, "ID.length should be > 0");
            for (s in id) {
                require(isIDValid(s!!)) { "Not a valid ID: $s" }
            }
            val newID = arrayOfNulls<String>(id.size)
            System.arraycopy(id, 0, newID, 0, id.size)
            return HistoryID(newID)
        }

        /**
         * An one-way function returning a "human readable" containing no special characters. All
         * characters _, a-z, A-Z, 0-9 are kept unchanged. All other are replaced with _ and the word
         * is post-fixed with $HASHCODE, where HASHCODE is the hexadecimal hash value of the original
         * string. If there are no special characters the word is not post-fixed.
         *
         *
         * Note: This method does not use URLEncoder, because in url-encoding the * sign is considered
         * as "safe".
         *
         * @param rawString
         * The string to be hashed.
         * @return The human-readable hash.
         */
        fun readableHash(rawString: String): String {
            val encodedString = StringBuilder(rawString)
            var addHash = false
            for (i in 0 until encodedString.length) {
                if (isSpecialChar(encodedString[i])) {
                    addHash = true
                    encodedString.setCharAt(i, '_')
                }
            }
            if (addHash) {
                encodedString.append('$')
                encodedString.append(Integer.toHexString(rawString.hashCode()))
            }
            return encodedString.toString()
        }

        /**
         * Decodes readable hash.
         *
         * @param rawString
         * The string to be checked.
         * @return The human-readable hash.
         */
        fun decodeReadableHash(rawString: String): String {
            val replaceCharIx = rawString.indexOf("_")
            val hashCharIx = rawString.indexOf("$")
            return if (replaceCharIx > -1 && hashCharIx > -1 && replaceCharIx < hashCharIx) {
                // String rawStrNotHashed = encodedString.substring(0, hashCharIx);
                // String hashValue = encodedString.substring(hashCharIx + 1);
                // TODO: we can check the string, just to be sure, if we now
                // the char to replace, when dealing with accounts it will be :
                rawString
            } else rawString
        }

        /**
         * Tests if an ID is valid.
         */
        private fun isIDValid(id: String): Boolean {
            var isValid = true
            val pos = id.indexOf('$')
            if (pos < 0) {
                // There is no $ in the id. In order to be valid all characters should be non-special
                isValid = !hasSpecialChar(id)
            } else {
                // There is a $ sign in the id. In order to be valid it has to be in the form
                // X..X$Y..Y, where there should
                // be no special characters in X..X, and Y..Y should be a hexadecimal number
                if (pos + 1 < id.length) {
                    val start = id.substring(0, pos)
                    val end = id.substring(pos + 1)

                    // Check X..X
                    isValid = !hasSpecialChar(start)
                    if (isValid) {
                        // OK; Check Y..Y
                        isValid = try {
                            end.toInt(16)
                            // OK
                            true
                        } catch (e: Exception) {
                            // Not OK
                            false
                        }
                    }
                } else {
                    // The % sign is in the beginning - bad ID.
                    isValid = false
                }
            }
            return isValid
        }

        /**
         * Tests if a character is a special one. A character is special if it is not in the range _,
         * a-z, A-Z, 0-9.
         *
         * @param c
         * The character to test.
         * @return Returns true if the character is special. False otherwise.
         */
        private fun isSpecialChar(c: Char): Boolean {
            return (c != '_' && c != '@' && c != '.' && c != '-' && c != '+'
                    && (c < 'A' || c > 'Z') && (c < 'a' || c > 'z')
                    && (c < '0' || c > '9'))
        }

        /**
         * Tests there is a special character in a string.
         */
        private fun hasSpecialChar(str: String): Boolean {
            var hasSpecialChar = false
            for (i in 0 until str.length) {
                if (isSpecialChar(str[i])) {
                    hasSpecialChar = true
                    break
                }
            }
            return hasSpecialChar
        }
    }
}