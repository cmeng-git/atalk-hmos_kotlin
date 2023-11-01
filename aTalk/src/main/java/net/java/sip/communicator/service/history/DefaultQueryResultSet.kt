/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history

import java.util.*

/**
 * @author Alexander Pelov
 * @author Eng Chong Meng
 */
class DefaultQueryResultSet<T>(records: Vector<T>) : QueryResultSet<T> {
    private var records = Vector<T>()
    private var currentPos = -1

    init {
        this.records = records
    }

    @Throws(NoSuchElementException::class)
    override fun nextRecord(): T {
        return next()
    }

    @Throws(NoSuchElementException::class)
    override fun prevRecord(): T {
        return prev()
    }

    override fun hasPrev(): Boolean {
        return currentPos - 1 >= 0
    }

    @Throws(NoSuchElementException::class)
    override fun prev(): T {
        currentPos--
        if (currentPos < 0) {
            throw NoSuchElementException()
        }
        return records[currentPos]
    }

    override fun hasNext(): Boolean {
        return currentPos + 1 < records.size
    }

    override fun next(): T {
        currentPos++
        if (currentPos >= records.size) {
            throw NoSuchElementException()
        }
        return records[currentPos]
    }

    @Throws(UnsupportedOperationException::class)
    override fun remove() {
        throw UnsupportedOperationException("Cannot remove elements from underlaying collection.")
    }
}