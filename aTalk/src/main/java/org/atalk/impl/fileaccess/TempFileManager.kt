/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.fileaccess

import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.io.PrintStream
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Generates and properly cleans up temporary files. Similar to
 * [File.createTempFile], this class provides a static method to create
 * temporary files. The temporary files will be created in a special directory to be cleaned up the next time this class
 * is loaded by the JVM. This functionality is required because Win32 platforms will not allow the JVM to delete files
 * that are open. This causes problems with items such as JARs that get opened by a URLClassLoader and can therefore not
 * be deleted by the JVM (including deleteOnExit).
 *
 * The caller should not need to create an instance of this class, although it is possible. Simply use the static
 * methods to perform the required operations. Note that all files created by this class should be considered as deleted
 * at JVM exit (although the actual deletion may be delayed). If persistent temporary files are required, use
 * [java.io.File] instead.
 *
 * Refer to Sun bugs 4171239 and 4950148 for more details.
 */
object TempFileManager {
    /**
     * Creates a temporary file in the proper directory to allow for cleanup after execution. This method delegates to
     * [File.createTempFile] so refer to it for more
     * documentation. Any file created using this method should be considered as deleted at JVM exit; therefore, do not
     * use this method to create files that need to be persistent between application runs.
     *
     * @param prefix
     * the prefix string used in generating the file name; must be at least three characters long
     * @param suffix
     * the suffix string to be used in generating the file's name; may be null, in which case the suffix ".tmp"
     * will be used
     * @return an abstract pathname denoting a newly created empty file
     * @throws IOException
     * if a file could not be created
     */
    @Throws(IOException::class)

    fun createTempFile(prefix: String, suffix: String): File {
        // Check to see if you have already initialized a temp directory for this class.
        if (sTmpDir == null) {
            // Initialize your temp directory. You use the java temp directory
            // property, so you are sure to find the files on the next run.
            val tmpDirName = System.getProperty("java.io.tmpdir")!!
            val tmpDir = File.createTempFile(TEMP_DIR_PREFIX, ".tmp", File(tmpDirName))

            // Delete the file if one was automatically created by the JVM.
            // You are going to use the name of the file as a directory name,
            // so you do not want the file laying around.
            tmpDir.delete()

            // Create a lock before creating the directory so
            // there is no race condition with another application trying
            // to clean your temp dir.
            val lockFile = File(tmpDirName, tmpDir.name + ".lck")
            lockFile.createNewFile()

            // Set the lock file to delete on exit so it is properly cleaned
            // by the JVM. This will allow the TempFileManager to clean
            // the overall temp directory next time.
            lockFile.deleteOnExit()

            // Make a temp directory that you will use for all future requests.
            if (!tmpDir.mkdirs()) {
                throw IOException("Unable to create temporary directory:" + tmpDir.absolutePath)
            }
            sTmpDir = tmpDir
        }

        // Generate a temp file for the user in your temp directory
        // and return it.
        return File.createTempFile(prefix, suffix, sTmpDir)
    }

    /**
     * Deletes all of the files in the given directory, recursing into any sub directories found. Also deletes the root
     * directory.
     *
     * @param rootDir the root directory to be recursively deleted
     * @throws IOException if any file or directory could not be deleted
     */
    @Throws(IOException::class)
    private fun recursiveDelete(rootDir: File) {
        // Select all the files
        val files = rootDir.listFiles()!!
        for (file in files) {
            // If the file is a directory, we will recursively call deleteRecursive on it.
            if (file.isDirectory) {
                recursiveDelete(file)
            } else {
                // It is just a file so we are safe to delete it
                if (!file.delete()) {
                    throw IOException("Could not delete: " + file.absolutePath)
                }
            }
        }

        // Finally, delete the root directory now
        // that all of the files in the directory have
        // been properly deleted.
        if (!rootDir.delete()) {
            throw IOException("Could not delete: " + rootDir.absolutePath)
        }
    }

    /**
     * The prefix for the temp directory in the system temp directory
     */
    private const val TEMP_DIR_PREFIX = "tmp-mgr-"

    /**
     * The temp directory to generate all files in
     */
    private var sTmpDir: File? = null

    /*
	 * Static block used to clean up any old temp directories found -- the JVM will run this block when a class loader
	 * loads the class.
	 */
    init {
        // Clean up any old temp directories by listing
        // all of the files, using a filter that will
        // return only directories that start with your prefix.
        val tmpDirFilter = FileFilter { pathname -> pathname.isDirectory && pathname.name.startsWith(TEMP_DIR_PREFIX) }

        // Get the system temp directory and filter the files.
        val tmpDirName = System.getProperty("java.io.tmpdir")!!
        val tmpDir = File(tmpDirName)
        val tmpFiles = tmpDir.listFiles(tmpDirFilter)!!

        // Find all the files that do not have a lock by
        // checking if the lock file exists.
        for (tmpFile in  tmpFiles) {
            // Create a file to represent the lock and test.
            val lockFile = File(tmpFile.parent,  tmpFile.name + ".lck")
            if (!lockFile.exists()) {
                // Delete the contents of the directory since it is no longer locked.
                Timber.log(Level.FINE.intValue(), "TempFileManager::deleting old temp directory %s",  tmpFile)
                try {
                    recursiveDelete(tmpFile)
                } catch (ex: IOException) {
                    // You log at a fine level since not being able to delete
                    // the temp directory should not stop the application
                    // from performing correctly. However, if the application
                    // generates a lot of temp files, this could become
                    // a disk space problem and the level should be raised.
                    Logger.getLogger("default").log(Level.INFO, "TempFileManager::unable to delete " +  tmpFile.absolutePath)

                    // Print the exception.
                    val ostream = ByteArrayOutputStream()
                    ex.printStackTrace(PrintStream(ostream))
                    Logger.getLogger("default").log(Level.FINE, ostream.toString())
                }
            }
        }
    }
}