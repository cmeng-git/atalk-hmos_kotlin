/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.plugin.loggingutils

import org.atalk.service.fileaccess.FileCategory
import org.atalk.util.OSUtils
import timber.log.Timber
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Collect logs and save them in compressed zip file.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
object LogsCollector {
    /**
     * The name of the log dir.
     */
    const val LOGGING_DIR_NAME = "log"

    /**
     * The prefix name of standard java crash log file.
     */
    private const val JAVA_ERROR_LOG_PREFIX = "hs_err_pid"

    /**
     * The date format used in file names.
     */
    private val FORMAT = SimpleDateFormat("yyyy-MM-dd@HH.mm.ss", Locale.US)

    /**
     * The pattern uses to match crash logs.
     */
    private val JAVA_ERROR_LOG_PATTERN = Pattern.compile(Pattern.quote("sip.communicator"), Pattern.CASE_INSENSITIVE)

    /**
     * Save the log files in archive file. If destination is a folder, we generate the filename with
     * current date and time. If the destination is null we do nothing and if it is a file we use
     * as it, as we check does it end with zip extension, is missing we add it.
     *
     * @param destination_ the possible destination archived file
     * @param optional an optional file to be added to the archive.
     * @return the resulting file in zip format
     * @throws FileNotFoundException on file access permission denied
     */
    @Throws(FileNotFoundException::class)
    fun collectLogs(destination_: File?, optional: File?): File? {
        var destination = destination_ ?: return null
        if (!destination.isDirectory) {
            if (!destination.name.endsWith("zip")) destination = File(destination.parentFile, destination.name + ".zip")
        } else {
            destination = File(destination, defaultFileName)
        }
        val out = ZipOutputStream(FileOutputStream(destination))
        collectJavaCrashLogs(out)
        collectHomeFolderLogs(out)
        if (optional != null) {
            addFileToZip(optional, out)
        }
        try {
            out.close()
            return destination
        } catch (ex: IOException) {
            Timber.e(ex, "Error closing archive file")
        }
        return null
    }

    /**
     * The default filename to use.
     *
     * @return the default filename to use.
     */
    private val defaultFileName: String
        get() = FORMAT.format(Date()) + "-logs.zip"

    /**
     * Collects all files from log folder except the lock file; and put them in the zip file as zip entries.
     *
     * @param out the output zip file.
     */
    private fun collectHomeFolderLogs(out: ZipOutputStream) {
        try {
            val fs = LoggingUtilsActivator.fileAccessService!!
                    .getPrivatePersistentDirectory(LOGGING_DIR_NAME, FileCategory.LOG)!!.listFiles()
            if (fs != null) {
                for (f in fs) {
                    if (f.name.endsWith(".lck")) continue
                    addFileToZip(f, out)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error obtaining logs folder")
        }
    }

    /**
     * Copies a file to the given archive.
     *
     * @param file the file to copy.
     * @param out the output archive stream.
     */
    private fun addFileToZip(file: File, out: ZipOutputStream) {
        val buf = ByteArray(1024)
        try {
            val `in` = FileInputStream(file)
            // new ZIP entry
            out.putNextEntry(ZipEntry(LOGGING_DIR_NAME + File.separator + file.name))

            // transfer bytes
            var len: Int
            while (`in`.read(buf).also { len = it } > 0) {
                out.write(buf, 0, len)
            }
            out.closeEntry()
            `in`.close()
        } catch (ex: FileNotFoundException) {
            Timber.e(ex, "Error obtaining file to archive")
        } catch (ex: IOException) {
            Timber.e(ex, "Error saving file to archive")
        }
    }

    /**
     * Search for java crash logs belonging to us and add them to the log archive.
     *
     * @param out the output archive stream.
     */
    private fun collectJavaCrashLogs(out: ZipOutputStream) {
        // First check in working dir
        addCrashFilesToArchive(File(".").listFiles(), JAVA_ERROR_LOG_PREFIX, out)

        // If we don't have permissions to write to working directory the crash logs maybe in the
        // temp directory, or on the Desktop if its windows
        val homeDir = System.getProperty("user.home")
        if (OSUtils.IS_WINDOWS) {
            // check Desktop
            val desktopFiles = File(homeDir!! + File.separator + "Desktop").listFiles()
            addCrashFilesToArchive(desktopFiles, JAVA_ERROR_LOG_PREFIX, out)
        }
        if (OSUtils.IS_MAC) {
            val logDir = "/Library/Logs/"

            // Look in the following directories:
            // /Library/Logs/CrashReporter (OSX 10.4, 10.5)
            // ~/Library/Logs/CrashReporter (OSX 10.4, 10.5)
            // ~/Library/Logs/DiagnosticReports (OSX 10.6, 10.7, 10.8)
            //
            // Note that for 10.6, there are aliases in
            // ~/Library/Logs/CrashReporter for the crash files in
            // ~/Library/Logs/DiagnosticReports, but the code won't load the
            // aliases so we shouldn't get duplicates.
            val locations = arrayOf(logDir + "CrashReporter",
                    homeDir!! + logDir + "CrashReporter",
                    homeDir + logDir + "DiagnosticReports")
            for (location in locations) {
                val crashLogs = File(location).listFiles()
                addCrashFilesToArchive(crashLogs, null, out)
            }
        } else {
            // search in /tmp folder
            // Solaris OS and Linux the temporary directory is /tmp
            // windows TMP or TEMP environment variable is the temporary folder

            //java.io.tmpdir
            var tmpDir: String?
            if (System.getProperty("java.io.tmpdir").also { tmpDir = it } != null) {
                val tempFiles = File(tmpDir!!).listFiles()
                addCrashFilesToArchive(tempFiles, JAVA_ERROR_LOG_PREFIX, out)
            }
        }
    }

    /**
     * Checks if file is a crash log file and does it belongs to us.
     *
     * @param files files to check.
     * @param filterStartsWith a prefix for the files, can be null if no prefix check should be made.
     * @param out the output archive stream.
     */
    private fun addCrashFilesToArchive(files: Array<File>?, filterStartsWith: String?, out: ZipOutputStream) {
        // no files to add
        if (files == null) return

        // First check in working dir
        for (f in files) {
            if (filterStartsWith != null && !f.name.startsWith(filterStartsWith)) {
                continue
            }
            if (isOurCrashLog(f)) {
                addFileToZip(f, out)
            }
        }
    }

    /**
     * Checks whether the crash log file is for our application.
     *
     * @param file the crash log file.
     * @return `true` if error log is ours.
     */
    private fun isOurCrashLog(file: File): Boolean {
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (JAVA_ERROR_LOG_PATTERN.matcher(line).find()) return true
                }
            }
        } catch (ignored: Throwable) {
        }
        return false
    }
}