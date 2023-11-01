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

import org.apache.commons.lang3.StringUtils

/**
 * The utility class which can be used to clear passwords values from
 * 'sun.java.command' system property.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
object PasswordUtil {
    /**
     * The method will replace password argument values with 'X' in a string
     * which represents command line arguments(arg=value arg2=value4).
     *
     * @param cmd a string which represent command line arguments in a form
     * where each argument is separated by space and value is
     * assigned by '=' sign. For example "arg=value -arg2=value4 --arg3=val45".
     * @param passwordArg the name of password argument to be shadowed.
     * @return `cmdLine` string with password argument values shadowed by
     * 'X'
     */
    private fun replacePassword(cmd: String, passwordArg: String): String {
        var cmdLine = cmd
        val passwordIdx = cmdLine.indexOf("$passwordArg=")
        if (passwordIdx != -1) {
            // Get arg=pass
            var argEndIdx = cmdLine.indexOf(" ", passwordIdx)
            // Check if this is not the last argument
            if (argEndIdx == -1) argEndIdx = cmdLine.length
            val passArg = cmdLine.substring(passwordIdx, argEndIdx)

            // Split to get arg=
            val strippedPassArg = passArg.substring(0, passArg.indexOf("="))

            // Modify to have arg=X
            cmdLine = cmdLine.replace(passArg, "$strippedPassArg=X")
        }
        return cmdLine
    }

    /**
     * Does [.replacePassword] for every argument given in `passwordArgs` array.
     *
     * @param str command line argument string, e.g. "arg=3 pass=secret"
     * @param passwordArgs the array which contains the names of password argument to be shadowed.
     * @return `cmdLine` string with password arguments values shadowed by 'X'
     */
    fun replacePasswords(str: String, passwordArgs: List<String>?): String {
        var string = str
        if (passwordArgs != null) {
            for (passArg in passwordArgs) {
                if (StringUtils.isEmpty(passArg)) continue
                string = replacePassword(string, passArg)
            }
        }
        return string
    }
}