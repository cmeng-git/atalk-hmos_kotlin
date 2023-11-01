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

/**
 * Utility fields for OS detection.
 *
 * @author Sebastien Vincent
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
object OSUtils {
    /* <code>true</code> if architecture is 32 bit. */
    var IS_32_BIT = false

    /* <code>true</code> if architecture is 64 bit. */
    var IS_64_BIT = false

    /* <code>true</code> if OS is Android */
    var IS_ANDROID = false

    /* <code>true</code> if OS is Linux. */
    var IS_LINUX = false

    /* <code>true</code> if OS is Linux 32-bit. */
    var IS_LINUX32 = false

    /* <code>true</code> if OS is Linux 64-bit. */
    var IS_LINUX64 = false

    /* <code>true</code> if OS is MacOSX. */
    var IS_MAC = false

    /* <code>true</code> if OS is MacOSX 32-bit. */
    var IS_MAC32 = false

    /* <code>true</code> if OS is MacOSX 64-bit. */
    var IS_MAC64 = false

    /* <code>true</code> if OS is Windows. */
    var IS_WINDOWS = false

    /* <code>true</code> if OS is Windows 32-bit. */
    var IS_WINDOWS32 = false

    /* <code>true</code> if OS is Windows 64-bit. */
    var IS_WINDOWS64 = false

    /* <code>true</code> if OS is Windows 7. */
    var IS_WINDOWS_7 = false

    /* <code>true</code> if OS is Windows 8. */
    var IS_WINDOWS_8 = false

    /* <code>true</code> if OS is Windows 10. */
    var IS_WINDOWS_10 = false

    /* <code>true</code> if OS is FreeBSD. */
    var IS_FREEBSD = false

    init {
        // OS
        val osName = System.getProperty("os.name")
        if (osName == null) {
            IS_ANDROID = false
            IS_LINUX = false
            IS_MAC = false
            IS_WINDOWS = false
            IS_WINDOWS_7 = false
            IS_WINDOWS_8 = false
            IS_WINDOWS_10 = false
            IS_FREEBSD = false
        } else if (osName.startsWith("Linux")) {
            val javaVmName = System.getProperty("java.vm.name")
            if (javaVmName != null && javaVmName.equals("Dalvik", ignoreCase = true)) {
                IS_ANDROID = true
                IS_LINUX = false
            } else {
                IS_ANDROID = false
                IS_LINUX = true
            }
            IS_MAC = false
            IS_WINDOWS = false
            IS_WINDOWS_7 = false
            IS_WINDOWS_8 = false
            IS_WINDOWS_10 = false
            IS_FREEBSD = false
        } else if (osName.startsWith("Mac")) {
            IS_ANDROID = false
            IS_LINUX = false
            IS_MAC = true
            IS_WINDOWS = false
            IS_WINDOWS_7 = false
            IS_WINDOWS_8 = false
            IS_WINDOWS_10 = false
            IS_FREEBSD = false
        } else if (osName.startsWith("Windows")) {
            IS_ANDROID = false
            IS_LINUX = false
            IS_MAC = false
            IS_WINDOWS = true
            IS_WINDOWS_7 = osName.contains("7")
            IS_WINDOWS_8 = osName.contains("8")
            IS_WINDOWS_10 = osName.contains("10")
            IS_FREEBSD = false
        } else if (osName.startsWith("FreeBSD")) {
            IS_ANDROID = false
            IS_LINUX = false
            IS_MAC = false
            IS_WINDOWS = false
            IS_WINDOWS_7 = false
            IS_WINDOWS_8 = false
            IS_WINDOWS_10 = false
            IS_FREEBSD = true
        } else {
            IS_ANDROID = false
            IS_LINUX = false
            IS_MAC = false
            IS_WINDOWS = false
            IS_WINDOWS_7 = false
            IS_WINDOWS_8 = false
            IS_WINDOWS_10 = false
            IS_FREEBSD = false
        }

        // arch i.e. x86, amd64
        val osArch = System.getProperty("sun.arch.data.model")
        if (osArch == null) {
            IS_32_BIT = true
            IS_64_BIT = false
        } else if (osArch.contains("32")) {
            IS_32_BIT = true
            IS_64_BIT = false
        } else if (osArch.contains("64")) {
            IS_32_BIT = false
            IS_64_BIT = true
        } else {
            IS_32_BIT = false
            IS_64_BIT = false
        }

        // OS && arch
        IS_LINUX32 = IS_LINUX && IS_32_BIT
        IS_LINUX64 = IS_LINUX && IS_64_BIT
        IS_MAC32 = IS_MAC && IS_32_BIT
        IS_MAC64 = IS_MAC && IS_64_BIT
        IS_WINDOWS32 = IS_WINDOWS && IS_32_BIT
        IS_WINDOWS64 = IS_WINDOWS && IS_64_BIT
    }
}