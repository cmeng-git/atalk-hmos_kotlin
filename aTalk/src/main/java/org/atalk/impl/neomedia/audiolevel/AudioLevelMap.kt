/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel

/**
 * The class implements a basic mapping utility that allows binding `long` CSRC ID-s to
 * `int` audio levels. The class does not implement any synchronization for neither read nor
 * write operations but it is still intended to handle concurrent access in a manner that can be
 * considered graceful for the audio level use case. The class uses a bi-dimensional
 * `long[][]` matrix that is recreated every time a new CSRC is added or an existing one is
 * removed. Iterating through the matrix is only possible after obtaining a direct reference to it.
 * It is possible for this reference to become invalid shortly after someone has obtained it (e.g.
 * because someone added a new CSRC) but this should not cause problems for the CSRC audio level
 * delivery case.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class AudioLevelMap {
    /**
     * The matrix containing a CSRC-to-level mappings.
     */
    private var levels: Array<LongArray>? = null

    /**
     * If this map already contains `csrc` this method updates its level, otherwise we add a
     * new entry mapping `csrc` to `level`.
     *
     * @param csrc the CSRC key that we'd like to add/update.
     * @param level the new audio level for the specified `csrc`.
     */
    fun putLevel(csrc: Long, level: Int) {
        // copy the levels matrix so that no one pulls it from under our feet.
        val levelsRef = levels
        val csrcIndex = findCSRC(levelsRef, csrc)

        if (csrcIndex == -1) {
            // we don't have the csrc in there yet so we need a new row.
            levels = appendCSRCToMatrix(levelsRef, csrc, level)
        } else {
            levelsRef!![csrcIndex][1] = level.toLong()
        }
    }

    /**
     * Removes `csrc` and its mapped level from this map.
     *
     * @param csrc the CSRC ID that we'd like to remove from this map.
     * @return `true` if `csrc` was present in the `Map` and `false` otherwise.
     */
    fun removeLevel(csrc: Long): Boolean {
        // copy the levels matrix so that no one pulls it from under our feet.
        val levelsRef = levels
        val index = findCSRC(levelsRef, csrc)
        if (index == -1)
            return false

        if (levelsRef!!.size == 1) {
            levels = null
            return true
        }

        // copy levelsRef into newLevels ref making sure we skip the entry
        // containing the CSRC ID that we are trying to remove;
        val newLevelsRef = ArrayList<LongArray>(levelsRef.size - 1)
        System.arraycopy(levelsRef, 0, newLevelsRef, 0, index)
        System.arraycopy(levelsRef, index + 1, newLevelsRef, index, newLevelsRef.size - index)

        levels = newLevelsRef.toTypedArray()
        return true
    }

    /**
     * Returns the audio level of the specified `csrc` id or `-1` if `csrc` is not currently registered in this map.
     *
     * @param csrc the CSRC ID whose level we'd like to obtain.
     * @return the audio level of the specified `csrc` id or `-1` if `csrc` is not currently registered in this map.
     */
    fun getLevel(csrc: Long): Int {
        val levelsRef = levels
        val index = findCSRC(levelsRef, csrc)
        return if (index == -1)
            -1
        else levelsRef!![index][1].toInt()
    }

    /**
     * Returns the index of the specified `csrc` level in the `levels` matrix or
     * `-1` if `levels` is `null` or does not contain `csrc`.
     *
     * @param levels the bi-dimensional array that we'd like to search for the specified `csrc`.
     * @param csrc the CSRC identifier that we are looking for.
     * @return the the index of the specified `csrc` level in the `levels` matrix or
     * `-1` if `levels` is `null` or does not contain `csrc`.
     */
    private fun findCSRC(levels: Array<LongArray>?, csrc: Long): Int {
        if (levels != null) {
            for (i in levels.indices) {
                if (levels[i][0] == csrc)
                    return i
            }
        }
        return -1
    }

    /**
     * Creates a new bi-dimensional array containing all entries (if any) from the `levels`
     * matrix and an extra entry for the specified `csrc` and `level`.
     *
     * @param levels the bi-dimensional levels array that we'd like to add a mapping to.
     * @param csrc the CSRC identifier that we'd like to add to the `levels` bi-dimensional array.
     * @param level the level corresponding to the `csrc` identifier.
     * @return a new matrix containing all entries from levels and a new one mapping `csrc` to `level`
     */
    private fun appendCSRCToMatrix(levels: Array<LongArray>?, csrc: Long, level: Int): Array<LongArray> {
        val newLength = 1 + (levels?.size ?: 0)
        val newLevels = emptyArray<LongArray>()

        // put the new level.
        newLevels[0] = longArrayOf(csrc, level.toLong())
        if (newLength == 1)
            return newLevels

        System.arraycopy(levels!!, 0, newLevels, 1, levels.size)
        return newLevels
    }
}