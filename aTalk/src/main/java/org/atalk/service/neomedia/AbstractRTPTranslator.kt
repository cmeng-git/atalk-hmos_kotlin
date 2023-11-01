/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

import org.atalk.service.neomedia.RTPTranslator.WriteFilter

/**
 * An abstract, base implementation of [RTPTranslator] which aid the implementation of the
 * interface.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractRTPTranslator : RTPTranslator {
    /**
     * The `WriteFilter`s added to this `RTPTranslator`.
     */
    private var writeFilters = NO_WRITE_FILTERS

    /**
     * The `Object` which synchronizes the access to [.writeFilters].
     */
    private val writeFiltersSyncRoot = Any()

    /**
     * {@inheritDoc}
     */
    override fun addWriteFilter(writeFilter: WriteFilter?) {
        if (writeFilter == null) throw NullPointerException("writeFilter")
        synchronized(writeFiltersSyncRoot) {
            for (wf in writeFilters) {
                if (wf == writeFilter) return
            }
            val newWriteFilters = arrayOfNulls<WriteFilter>(writeFilters.size + 1)
            if (writeFilters.size != 0) {
                System.arraycopy(writeFilters, 0, newWriteFilters, 0, writeFilters.size)
            }
            newWriteFilters[writeFilters.size] = writeFilter
            writeFilters = newWriteFilters
        }
    }

    /**
     * Gets the `WriteFilter`s added to this `RTPTranslator`.
     *
     * @return the `WriteFilter`s added to this `RTPTranslator`
     */
    protected fun getWriteFilters(): Array<WriteFilter?> {
        synchronized(writeFiltersSyncRoot) { return if (writeFilters.size == 0) NO_WRITE_FILTERS else writeFilters.clone() }
    }

    /**
     * {@inheritDoc}
     */
    override fun removeWriteFilter(writeFilter: WriteFilter?) {
        if (writeFilter != null) {
            synchronized(writeFiltersSyncRoot) {
                for (i in writeFilters.indices) {
                    if (writeFilters[i] == writeFilter) {
                        val newWriteFilters: Array<WriteFilter?>
                        if (writeFilters.size == 1) {
                            newWriteFilters = NO_WRITE_FILTERS
                        } else {
                            val newWriteFiltersLength = writeFilters.size - 1
                            newWriteFilters = arrayOfNulls(newWriteFiltersLength)
                            if (i != 0) {
                                System.arraycopy(writeFilters, 0, newWriteFilters, 0, i)
                            }
                            if (i != newWriteFiltersLength) {
                                System.arraycopy(writeFilters, i + 1, newWriteFilters, i,
                                        newWriteFiltersLength - i)
                            }
                        }
                        writeFilters = newWriteFilters
                        break
                    }
                }
            }
        }
    }

    /**
     * Notifies this `RTPTranslator` that a `buffer` from a `source` will be
     * written into a `destination`.
     *
     * @param source
     * the source of `buffer`
     * @param pkt
     * the packet from `source` which is to be written into `destination`
     * @param destination
     * the destination into which `buffer` is to be written
     * @param data
     * `true` for data/RTP or `false` for control/RTCP
     * @return `true` if the writing is to continue or `false` if the writing is to
     * abort
     */
    protected fun willWrite(source: MediaStream?, pkt: RawPacket?,
                            destination: MediaStream?, data: Boolean): Boolean {
        var writeFilter: WriteFilter? = null
        var writeFilters: Array<WriteFilter?>? = null
        var accept = true
        synchronized(writeFiltersSyncRoot) {
            if (this.writeFilters.size != 0) {
                if (this.writeFilters.size == 1) writeFilter = this.writeFilters[0] else writeFilters = this.writeFilters.clone()
            }
        }
        if (writeFilter != null) {
            accept = willWrite(writeFilter, source, pkt, destination, data)
        } else if (writeFilters != null) {
            for (wf in writeFilters!!) {
                accept = willWrite(wf, source, pkt, destination, data)
                if (!accept) break
            }
        }
        return accept
    }

    /**
     * Invokes a specific `WriteFilter`.
     *
     * @param source
     * the source of `buffer`
     * @param pkt
     * the packet from `source` which is to be written into `destination`
     * @param destination
     * the destination into which `buffer` is to be written
     * @param data
     * `true` for data/RTP or `false` for control/RTCP
     * @return `true` if the writing is to continue or `false` if the writing is to
     * abort
     */
    protected fun willWrite(writeFilter: WriteFilter?, source: MediaStream?,
                            pkt: RawPacket?, destination: MediaStream?, data: Boolean): Boolean {
        var accept: Boolean
        try {
            accept = writeFilter!!.accept(source, pkt, destination, data)
        } catch (t: Throwable) {
            accept = true
            if (t is InterruptedException) Thread.currentThread().interrupt() else if (t is ThreadDeath) throw t
        }
        return accept
    }

    companion object {
        /**
         * An empty array with element type `WriteFilter`. Explicitly defined in order to reduce
         * unnecessary allocations and the consequent effects of the garbage collector.
         */
        private val NO_WRITE_FILTERS = arrayOfNulls<WriteFilter>(0)
    }
}