/*
 * Copyright @ 2019 - Present 8x8, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Implements utility functions to facilitate work with `String`s.
 *
 * @author Grigorii Balutsel
 * @author Emil Ivov
 * @author Eng Chong Meng
 *
 * Use StringUtils from apache commons except methods defined below.
 */
object StringUtils {
    /**
     * Checks whether a string is `null` or blank (empty or whitespace).
     *
     * @param s the string to analyze.
     * @return `true` if the string is `null` or blank.
     */
    fun isNullOrEmpty(s: String?): Boolean {
        return isNullOrEmpty(s, true)
    }

    /**
     * Indicates whether string is <tt>null</tt> or empty.
     *
     * @param s the string to analyze.
     * @param trim indicates whether to trim the string.
     * @return <tt>true</tt> if string is <tt>null</tt> or empty.
     */
    fun isNullOrEmpty(s: String?, trim: Boolean): Boolean {
        var string = s ?: return true
        if (trim) string = string.trim { it <= ' ' }
        return string.isEmpty()
    }
    /**
     * Creates `InputStream` from the string in the specified encoding.
     *
     * @param string the string to convert.
     * @param encoding the encoding
     * @return the `InputStream`.
     * @throws UnsupportedEncodingException if the encoding is unsupported.
     */
    /**
     * Creates <tt>InputStream</tt> from the string in UTF8 encoding.
     *
     * @param string the string to convert.
     * @return the `InputStream`.
     * @throws UnsupportedEncodingException if UTF8 is unsupported.
     */
    @JvmOverloads
    @Throws(UnsupportedEncodingException::class)
    fun fromString(string: String, encoding: String? = "UTF-8"): InputStream {
        return ByteArrayInputStream(string.toByteArray(charset(encoding!!)))
    }

    /**
     * Returns the UTF8 bytes for <tt>string</tt> and handles the unlikely case
     * where UTF-8 is not supported.
     *
     * @param string the <tt>String</tt> whose bytes we'd like to obtain.
     * @return <tt>string</tt>'s bytes.
     */
    fun getUTF8Bytes(string: String): ByteArray {
        return try {
            string.toByteArray(charset("UTF-8"))
        } catch (exc: UnsupportedEncodingException) {
            // shouldn't happen. UTF-8 is always supported, anyways ... if this happens, we'll cheat
            string.toByteArray()
        }
    }

    /**
     * Converts <tt>string</tt> into an UTF8 <tt>String</tt> and handles the
     * unlikely case where UTF-8 is not supported.
     *
     * @param bytes the <tt>byte</tt> array that we'd like to convert into a <tt>String</tt>.
     * @return the UTF-8 <tt>String</tt>.
     */
    fun getUTF8String(bytes: ByteArray?): String {
        return String(bytes!!, StandardCharsets.UTF_8)
    }

    /**
     * Indicates whether the given string contains any letters.
     *
     * @param string the string to check for letters
     * @return <tt>true</tt> if the given string contains letters;
     * <tt>false</tt>, otherwise
     */
    fun containsLetters(string: String): Boolean {
        for (element in string) {
            if (Character.isLetter(element)) return true
        }
        return false
    }

    /**
     * Initializes a new <tt>String</tt> instance by decoding a specified array of bytes (mostly used by JNI).
     *
     * @param bytes the bytes to be decoded into characters/a new `String` instance
     * @return a new `String` instance whose characters are decoded from the specified `bytes`
     */
    fun newString(bytes: ByteArray?): String? {
        return if (bytes == null || bytes.isEmpty()) null else {
            val defaultCharset = Charset.defaultCharset()
            val charsetName = if (defaultCharset == null) "UTF-8" else defaultCharset.name()
            try {
                String(bytes, charset(charsetName))
            } catch (ueex: UnsupportedEncodingException) {
                String(bytes)
            }
        }
    }
}