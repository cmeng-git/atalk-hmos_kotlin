/*
 * Copyright @ 2015 - Present 8x8, Inc
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
package org.atalk.util.logging

import org.atalk.service.configuration.ConfigurationService
import java.io.File
import java.util.logging.LogManager

/**
 * Simple file logging `Handler`.
 * Extends java.util.logging.FileHandler and adds the special component to
 * the file pattern - %s which is replaced at runtime with sip-communicator's
 * home directory. If the pattern option is missing creates log directory in sip-communicator's
 * home directory. If the directory is missing create it.
 *
 * @author Damian Minkov
 */
class FileHandler : java.util.logging.FileHandler {
    /**
     * Initialize a `FileHandler` to write to a set of files.  When
     * (approximately) the given limit has been written to one file,
     * another file will be opened.  The output will cycle through a set of count files.
     *
     * The `FileHandler` is configured based on `LogManager`
     * properties (or their default values) except that the given pattern
     * argument is used as the filename pattern, the file limit is
     * set to the limit argument, and the file count is set to the given count argument.
     *
     * The count must be at least 1.
     *
     * @param pattern the pattern for naming the output file
     * @param limit the maximum number of bytes to write to any one file
     * @param count the number of files to use
     * @throws IOException if there are IO problems opening the files.
     * @throws SecurityException if a security manager exists and if
     * the caller does not have `LoggingPermission("control")`.
     * @throws IllegalArgumentException if limit < 0, or count < 1.
     * @throws IllegalArgumentException if pattern is an empty string
     */
    constructor(pattern: String?, limit: Int, count: Int) : super(pattern, limit, count) {}

    /**
     * Construct a default `FileHandler`.  This will be configured entirely from
     * `LogManager` properties (or their default values). Will change.
     *
     * @throws IOException if there are IO problems opening the files.
     * @throws SecurityException if a security manager exists and if
     * the caller does not have `LoggingPermission("control"))`.
     * @throws NullPointerException if pattern property is an empty String.
     */
    constructor() : super(pattern, limit, count) {}

    companion object {// default value
        /**
         * Returns the count of the log files or the default value 1;
         *
         * @return file count
         */
        /**
         * Specifies how many output files to cycle through (defaults to 1).
         */
        private var count = -1
            private get() {
                if (field == -1) {
                    val countStr = LogManager.getLogManager().getProperty(FileHandler::class.java.name + ".count")

                    // default value
                    field = 1
                    try {
                        field = countStr.toInt()
                    } catch (ex: Exception) {
                    }
                }
                return field
            }
        /**
         * Returns the limit size for one log file or default 0, which is unlimited.
         *
         * @return the limit size
         */
        /**
         * Specifies an approximate maximum amount to write (in bytes)
         * to any one file.  If this is zero, then there is no limit. (Defaults to no limit).
         */
        private var limit = -1
            private get() {
                if (field == -1) {
                    val limitStr = LogManager.getLogManager().getProperty(FileHandler::class.java.name + ".limit")

                    // default value
                    field = 0
                    try {
                        field = limitStr.toInt()
                    } catch (ex: Exception) {
                    }
                }
                return field
            }

        /**
         * Specifies a pattern for generating the output file name.
         * A pattern consists of a string that includes the special
         * components that will be replaced at runtime such as : %t, %h, %g, %u.
         * Also adds the special component :
         * %s sip-communicator's home directory, typically -
         * ${net.java.sip.communicator.SC_HOME_DIR_LOCATION}/
         * ${net.java.sip.communicator.SC_HOME_DIR_NAME}.
         *
         * The field is public so that our `Logger` could reset it if necessary.
         */
        var pattern: String? = null
            /**
             * Substitute %s in the pattern and creates the directory if it doesn't exist.
             */
            get() {
                if (field == null) {
                    field = LogManager.getLogManager().getProperty(FileHandler::class.java.name + ".pattern")
                    val homeLocation = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
                    val dirName = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
                    if (homeLocation != null && dirName != null) {
                        if (field == null) field = "$homeLocation/$dirName/log/atalk%u.log" else field = field!!.replace("%s".toRegex(), "$homeLocation/$dirName")
                    }

                    // if pattern is missing and both dir name and home location properties are also not
                    // defined its most probably running from source or testing - lets create log
                    // directory in working dir.
                    if (field == null) field = "./log/atalk%u.log"
                    createDestinationDirectory(field)
                }
                return field
            }

        /**
         * Creates the directory in the pattern.
         *
         * @param pattern the directory we'd like to check.
         */
        private fun createDestinationDirectory(pattern: String?) {
            try {
                val ix = pattern!!.lastIndexOf('/')
                if (ix != -1) {
                    var dirName = pattern.substring(0, ix)
                    dirName = dirName.replace("%h".toRegex(), System.getProperty("user.home"))
                    dirName = dirName.replace("%t".toRegex(), System.getProperty("java.io.tmpdir"))
                    File(dirName).mkdirs()
                }
            } catch (e: Exception) {
            }
        }
    }
}