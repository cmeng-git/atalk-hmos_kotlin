/*
 * Copyright @ 2015 Atlassian Pty Ltd
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

import org.atalk.service.configuration.ConfigurationService
import java.io.File
import java.util.regex.Pattern

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
object ConfigUtils {
    /**
     * Gets an absolute path in the form of `File` from an absolute or
     * relative `path` specified in the form of a `String`. If
     * `path` is relative, it is resolved against
     * `ConfigurationService.PNAME_SC_HOME_DIR_LOCATION` and
     * `ConfigurationService.PNAME_SC_HOME_DIR_NAME`, `user.home`,
     * or the current working directory.
     *
     * @param path the absolute or relative path in the form of `String`
     * for/from which an absolute path in the form of `File` is to be returned
     * @param cfg the `ConfigurationService` to be employed by the method (invocation) if necessary
     * @return an absolute path in the form of `File` for/from the specified `path`
     */
    fun getAbsoluteFile(path: String, cfg: ConfigurationService?): File {
        var file = File(path)
        if (!file.isAbsolute) {
            var scHomeDirLocation: String?
            var scHomeDirName: String?
            if (cfg == null) {
                scHomeDirLocation = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION)
                scHomeDirName = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
            } else {
                scHomeDirLocation = cfg.scHomeDirLocation
                scHomeDirName = cfg.scHomeDirName
            }
            if (scHomeDirLocation == null) {
                scHomeDirLocation = System.getProperty("user.home")
                if (scHomeDirLocation == null) scHomeDirLocation = "."
            }
            if (scHomeDirName == null) scHomeDirName = "."
            file = File(File(scHomeDirLocation, scHomeDirName), path).absoluteFile
        }
        return file
    }

    /**
     * Gets the value as a `boolean` of a property from either a specific
     * `ConfigurationService` or `System`.
     *
     * @param cfg the `ConfigurationService` to get the value from or
     * `null` if the property is to be retrieved from `System`
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if `property` is not
     * associated with a value
     * @return the value as a `boolean` of `property` retrieved from
     * either `cfg` or `System`
     */
    fun getBoolean(cfg: ConfigurationService?, property: String, defaultValue: Boolean): Boolean {
        val b = if (cfg == null) {
            val s = System.getProperty(property)
            if (s == null || s.isEmpty()) defaultValue else java.lang.Boolean.parseBoolean(s)
        } else {
            cfg.getBoolean(property, defaultValue)
        }
        return b
    }

    /**
     * Gets the value as an `int` of a property from either a specific
     * `ConfigurationService` or `System`.
     *
     * @param cfg the `ConfigurationService` to get the value from or
     * `null` if the property is to be retrieved from `System`
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if `property` is not associated with a value
     * @return the value as an `int` of `property` retrieved from
     * either `cfg` or `System`
     */
    fun getInt(cfg: ConfigurationService?, property: String, defaultValue: Int): Int {
        val i = if (cfg == null) {
            val s = System.getProperty(property)
            if (s == null || s.isEmpty()) {
                defaultValue
            } else {
                try {
                    s.toInt()
                } catch (nfe: NumberFormatException) {
                    defaultValue
                }
            }
        } else {
            cfg.getInt(property, defaultValue)
        }
        return i
    }

    /**
     * Gets the value as an `long` of a property from either a specific
     * `ConfigurationService` or `System`.
     *
     * @param cfg the `ConfigurationService` to get the value from or
     * `null` if the property is to be retrieved from `System`
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if `property` is not
     * associated with a value
     * @return the value as an `long` of `property` retrieved from either `cfg` or `System`
     */
    fun getLong(cfg: ConfigurationService?, property: String, defaultValue: Long): Long {
        val i = if (cfg == null) {
            val s = System.getProperty(property)
            if (s == null || s.isEmpty()) {
                defaultValue
            } else {
                try {
                    s.toLong()
                } catch (nfe: NumberFormatException) {
                    defaultValue
                }
            }
        } else {
            cfg.getLong(property, defaultValue)
        }
        return i
    }

    /**
     * Gets the value as a `String` of a property from either a specific
     * `ConfigurationService` or `System`.
     *
     * @param cfg the `ConfigurationService` to get the value from or
     * `null` if the property is to be retrieved from `System`
     * @param property the name of the property to get
     * @param defaultValue the value to be returned if `property` is not
     * associated with a value
     * @return the value as a `String` of `property` retrieved from
     * either `cfg` or `System`
     */
    fun getString(cfg: ConfigurationService?, property: String, defaultValue: String?): String? {
        return if (cfg == null) System.getProperty(property, defaultValue) else cfg.getString(property, defaultValue)
    }

    /**
     * Gets the value as a `String` of a property from either a specific
     * `ConfigurationService` or `System`.
     *
     * @param cfg the `ConfigurationService` to get the value from or
     * `null` if the property is to be retrieved from `System`
     * @param property the name of the property to get
     * @param propertyAlternative an alternative name of the property
     * @param defaultValue the value to be returned if `property` is not
     * associated with a value
     * @return the value as a `String` of `property` retrieved from
     * either `cfg` or `System`
     */
    fun getString(cfg: ConfigurationService?, property: String,
                  propertyAlternative: String, defaultValue: String?): String? {
        var ret = getString(cfg, property, null)
        if (ret == null) {
            ret = getString(cfg, propertyAlternative, defaultValue)
        }
        return ret
    }

    /**
     * Specify names of command line arguments which are password, so that their
     * values will be masked when 'sun.java.command' is printed to the logs.
     * Separate each name with a comma.
     */
    // @SuppressFBWarnings({"UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", "MS_SHOULD_BE_FINAL"})
    var PASSWORD_CMD_LINE_ARGS: String? = null

    /**
     * Set this filed value to a regular expression which will be used to select
     * system properties keys whose values should be masked when printed out to
     * the logs.
     */
    // @SuppressFBWarnings({"UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", "MS_SHOULD_BE_FINAL"})
    var PASSWORD_SYS_PROPS: String? = null

    // Check if this key value should be masked
    // Mask command line arguments
    // Password system properties
    // Password command line arguments
    /**
     * Goes over all system properties and builds a string of their names and
     * values for debug purposes.
     */
    val systemPropertiesDebugString: String
        get() {
            val str = StringBuilder()
            try {
                // Password system properties
                var exclusion: Pattern? = null
                if (PASSWORD_SYS_PROPS != null) {
                    exclusion = Pattern.compile(PASSWORD_SYS_PROPS!!, Pattern.CASE_INSENSITIVE)
                }
                // Password command line arguments
                var passwordArgs: List<String>? = null
                if (PASSWORD_CMD_LINE_ARGS != null) passwordArgs = PASSWORD_CMD_LINE_ARGS!!.split(",")
                for ((key1, value1) in System.getProperties()) {
                    val key = key1.toString()
                    var value = value1.toString()
                    // Check if this key value should be masked
                    if (exclusion != null && exclusion.matcher(key).find()) {
                        value = "**********"
                    }
                    // Mask command line arguments
                    if (passwordArgs != null && "sun.java.command" == key) {
                        value = PasswordUtil.replacePasswords(value, passwordArgs)
                    }
                    str.append(key).append("=").append(value).append("\n")
                }
            } catch (e: RuntimeException) {
                str.append("An exception occurred while writing debug info").append(e.toString())
            }
            return str.toString()
        }
}