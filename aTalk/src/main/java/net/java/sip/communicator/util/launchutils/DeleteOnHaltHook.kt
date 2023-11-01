/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.launchutils

import java.io.File
import java.util.*

/**
 * In the fashion of `java.io.DeleteOnExitHook`, provides a way to delete
 * files when `Runtime.halt(int)` is to be invoked.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
object DeleteOnHaltHook {
    /**
     * The set of files to be deleted when `Runtime.halt(int)` is to be
     * invoked.
     */
    private var files: MutableSet<String?>? = LinkedHashSet()

    /**
     * Adds a file to the set of files to be deleted when
     * `Runtime.halt(int)` is to be invoked.
     *
     * @param file the name of the file to be deleted when
     * `Runtime.halt(int)` is to be invoked
     */
    @Synchronized
    fun add(file: String?) {
        checkNotNull(files) { "Shutdown in progress." }
        files!!.add(file)
    }

    /**
     * Deletes the files which have been registered for deletion when
     * `Runtime.halt(int)` is to be invoked.
     */
    fun runHooks() {
        var files: Set<String?>?
        synchronized(DeleteOnHaltHook::class.java) {
            files = DeleteOnHaltHook.files
            DeleteOnHaltHook.files = null
        }
        if (files != null) {
            val toBeDeleted = ArrayList(files!!)
            toBeDeleted.reverse()
            for (filename in toBeDeleted) File(filename!!).delete()
        }
    }
}