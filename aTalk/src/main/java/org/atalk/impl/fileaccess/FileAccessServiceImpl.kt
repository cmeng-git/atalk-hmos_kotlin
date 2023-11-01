/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.fileaccess

import org.apache.commons.lang3.StringUtils
import org.atalk.service.configuration.ConfigurationService
import org.atalk.service.fileaccess.FailSafeTransaction
import org.atalk.service.fileaccess.FileAccessService
import org.atalk.service.fileaccess.FileCategory
import org.atalk.service.libjitsi.LibJitsi
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Default FileAccessService implementation.
 *
 * @author Alexander Pelov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class FileAccessServiceImpl : FileAccessService {
    private var profileDirLocation: String? = null
    private var cacheDirLocation: String? = null
    private var logDirLocation: String? = null
    private var scHomeDirName: String? = null

    /**
     * The indicator which determines whether [.initialize] has been invoked on this instance. Introduced to
     * delay the initialization of the state of this instance until it is actually necessary.
     */
    private var initialized = false

    /**
     * This method returns a created temporary file. After you close this file it is not guaranteed that you will be
     * able to open it again nor that it will contain any information.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @return The created temporary file
     * @throws IOException If the file cannot be created
     */
    @get:Throws(IOException::class)
    override val temporaryFile: File
        get() = TempFileManager.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)

    /**
     * Returns the temporary directory.
     *
     * @return the created temporary directory
     * @throws IOException if the temporary directory cannot not be created
     */
    @get:Throws(IOException::class)
    override val temporaryDirectory: File
        get() {
            val file = temporaryFile
            if (!file.delete()) {
                throw IOException("Could not create temporary directory, because: could not delete temporary file.")
            }
            if (!file.mkdirs()) {
                throw IOException("Could not create temporary directory")
            }
            return file
        }

    /**
     * Please use [.getPrivatePersistentFile].
     */
    @Deprecated("")
    @Throws(Exception::class)
    override fun getPrivatePersistentFile(fileName: String): File {
        return this.getPrivatePersistentFile(fileName, FileCategory.PROFILE)
    }

    /**
     * This method returns a file specific to the current user. It may not exist, but it is guaranteed that you will
     * have the sufficient rights to create it.
     *
     * This file should not be considered secure because the implementor may return a file accessible to everyone.
     * Generally it will reside in current user's homedir, but it may as well reside in a shared directory.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param fileName The name of the private file you wish to access
     * @param category The classification of the file.
     * @return The file
     * @throws Exception if we failed to create the file.
     */
    @Throws(Exception::class)
    override fun getPrivatePersistentFile(fileName: String, category: FileCategory): File {
        return accessibleFile(getFullPath(category), fileName)
                ?: throw SecurityException("Insufficient rights to access this file in current user's home directory: "
                        + File(getFullPath(category), fileName).path)
    }

    /**
     * Please use [.getPrivatePersistentDirectory]
     */
    @Deprecated("")
    @Throws(Exception::class)
    override fun getPrivatePersistentDirectory(dirName: String): File {
        return getPrivatePersistentDirectory(dirName, FileCategory.PROFILE)
    }

    /**
     * This method creates a directory specific to the current user.
     *
     * This directory should not be considered secure because the implementor may return a directory accessible to
     * everyone. Generally it will reside in current user's homedir, but it may as well reside in a shared directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName The name of the private directory you wish to access.
     * @param category The classification of the directory.
     * @return The created directory.
     * @throws Exception Thrown if there is no suitable location for the persistent directory.
     */
    @Throws(Exception::class)
    override fun getPrivatePersistentDirectory(dirName: String, category: FileCategory): File {
        val dir = File(getFullPath(category), dirName)
        if (dir.exists()) {
            if (!dir.isDirectory) {
                throw RuntimeException("Could not create directory " + "because: A file exists with this name:" + dir.absolutePath)
            }
        } else if (!dir.mkdirs()) {
            throw IOException("Could not create directory")
        }
        return dir
    }

    /**
     * Returns the full path corresponding to a file located in the sip-communicator config home and carrying the
     * specified name.
     *
     * @param category The classification of the file or directory.
     * @return the config home location of a a file with the specified name.
     */
    private fun getFullPath(category: FileCategory?): File {
        initialize()

        // bypass the configurationService here to remove the dependency
        val directory = when (category) {
            FileCategory.CACHE -> cacheDirLocation
            FileCategory.LOG -> logDirLocation
            else -> profileDirLocation
        }
        return File(directory, scHomeDirName!!)
    }
    // For all other operating systems we return the Downloads folder.

    /**
     * Returns the default download directory.
     *
     * @return the default download directory
     */
    override val defaultDownloadDirectory: File
        get() =// For all other operating systems we return the Downloads folder.
            File(getSystemProperty("user.home"), "Downloads")

    /**
     * Creates a failsafe transaction which can be used to safely store informations into a file.
     *
     * @param file The file concerned by the transaction, null if file is null.
     * @return A new failsafe transaction related to the given file.
     */
    override fun createFailSafeTransaction(file: File): FailSafeTransaction {
        return FailSafeTransactionImpl(file)
    }

    /**
     * Initializes this instance if it has not been initialized yet i.e. acts as a delayed constructor of this instance.
     * Introduced because this `FileAccessServiceImpl` queries `System` properties that may not be set yet
     * at construction time and, consequently, throws an `IllegalStateException` which could be avoided.
     */
    @Synchronized
    private fun initialize() {
        if (initialized) return
        val cfg = LibJitsi.configurationService
        profileDirLocation = if (cfg != null) cfg.scHomeDirLocation else getSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION)
        checkNotNull(profileDirLocation) { ConfigurationService.PNAME_SC_HOME_DIR_LOCATION }
        scHomeDirName = if (cfg != null) cfg.scHomeDirName else getSystemProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME)
        checkNotNull(scHomeDirName) { ConfigurationService.PNAME_SC_HOME_DIR_NAME }
        val cacheDir = getSystemProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION)
        cacheDirLocation = cacheDir ?: profileDirLocation
        val logDir = getSystemProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION)
        logDirLocation = logDir ?: profileDirLocation
        initialized = true
    }

    companion object {
        /**
         * The file prefix for all temp files.
         */
        private const val TEMP_FILE_PREFIX = "SIPCOMM"

        /**
         * The file suffix for all temp files.
         */
        private const val TEMP_FILE_SUFFIX = "TEMP"

        /**
         * Returns the value of the specified java system property. In case the value was a zero length String or one that
         * only contained whitespaces, null is returned. This method is for internal use only. Users of the configuration
         * service are to use the getProperty() or getString() methods which would automatically determine whether a
         * property is system or not.
         *
         * @param propertyName the name of the property whose value we need.
         * @return the value of the property with name propertyName or null if the value had length 0 or only contained
         * spaces tabs or new lines.
         */
        private fun getSystemProperty(propertyName: String): String? {
            val retval = System.getProperty(propertyName)
            return if (StringUtils.isBlank(retval)) null else retval
        }

        /**
         * Checks if a file exists and if it is writable or readable. If not - checks if the user has a write privileges to
         * the containing directory.
         *
         * If those conditions are met it returns a File in the directory with a fileName. If not - returns null.
         *
         * @param homeDir the location of the sip-communicator home directory.
         * @param fileName the name of the file to create.
         * @return Returns null if the file does not exist and cannot be created. Otherwise - an object to this file
         * @throws IOException Thrown if the home directory cannot be created
         */
        @Throws(IOException::class)
        private fun accessibleFile(homeDir: File, fileName: String): File? {
            val file = File(homeDir, fileName)
            if (file.canRead() || file.canWrite()) {
                return file
            }

            if (!homeDir.exists()) {
                Timber.d("Creating home directory : %s", homeDir.absolutePath)
                if (!homeDir.mkdirs()) {
                    val message = "Could not create the home directory : " + homeDir.absolutePath
                    Timber.d("%s", message)
                    throw IOException(message)
                }
                Timber.d("Home directory created : %s", homeDir.absolutePath)
            } else if (!homeDir.canWrite()) {
                return null
            }

            if (!file.parentFile?.exists()!!) {
                if (!file.parentFile!!.mkdirs()) {
                    val message = "Could not create the parent directory : " + homeDir.absolutePath
                    Timber.d("%s", message)
                    throw IOException(message)
                }
            }
            return file
        }

        /**
         * Gets the major version of the executing operating system as defined by the `os.version` system property.
         *
         * @return the major version of the executing operating system as defined by the `os.version` system property
         */
        private val majorOSVersion: Int
            get() {
                val osVersion = System.getProperty("os.version")
                val majorOSVersion = if (osVersion != null && osVersion.isNotEmpty()) {
                    val majorOSVersionEnd = osVersion.indexOf('.')
                    val majorOSVersionString = if (majorOSVersionEnd > -1) osVersion.substring(0, majorOSVersionEnd) else osVersion
                    majorOSVersionString.toInt()
                } else 0
                return majorOSVersion
            }
    }
}