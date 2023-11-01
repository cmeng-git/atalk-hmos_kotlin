package net.java.sip.communicator.impl.history

import net.java.sip.communicator.service.history.QueryResultSet
import java.util.*

/**
 * This implementation is the same as DefaultQueryResultSet but the container holding the records is LinkedList - so
 * guarantees that values are ordered
 *
 * @param <T>
 * element type of query
 * @author Damian Minkov
</T> */
class OrderedQueryResultSet<T>(records: Set<T>?) : QueryResultSet<T> {
    private var records: LinkedList<T>? = null
    private var currentPos = -1

    /**
     * Constructor.
     *
     * @param records
     * the `Set` of records
     */
    init {
        this.records = LinkedList(records)
    }

    /**
     * Returns `true` if the iteration has more elements.
     *
     * @return `true` if the iterator has more elements.
     */
    override fun hasNext(): Boolean {
        return currentPos + 1 < records!!.size
    }

    /**
     * Returns true if the iteration has elements preceeding the current one.
     *
     * @return true if the iterator has preceeding elements.
     */
    override fun hasPrev(): Boolean {
        return currentPos - 1 >= 0
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     */
    override fun next(): T {
        currentPos++
        if (currentPos >= records!!.size) {
            throw NoSuchElementException()
        }
        return records!![currentPos]
    }

    /**
     * A strongly-typed variant of `next()`.
     *
     * @return the next history record.
     * @throws NoSuchElementException
     * iteration has no more elements.
     */
    @Throws(NoSuchElementException::class)
    override fun nextRecord(): T {
        return next()
    }

    /**
     * Returns the previous element in the iteration.
     *
     * @return the previous element in the iteration.
     * @throws NoSuchElementException
     * iteration has no more elements.
     */
    @Throws(NoSuchElementException::class)
    override fun prev(): T {
        currentPos--
        if (currentPos < 0) {
            throw NoSuchElementException()
        }
        return records!![currentPos]
    }

    /**
     * A strongly-typed variant of `prev()`.
     *
     * @return the previous history record.
     * @throws NoSuchElementException
     * iteration has no more elements.
     */
    @Throws(NoSuchElementException::class)
    override fun prevRecord(): T {
        return prev()
    }

    /**
     * Removes from the underlying collection the last element returned by the iterator (optional operation).
     */
    override fun remove() {
        throw UnsupportedOperationException("Cannot remove elements from underlaying collection.")
    }
}