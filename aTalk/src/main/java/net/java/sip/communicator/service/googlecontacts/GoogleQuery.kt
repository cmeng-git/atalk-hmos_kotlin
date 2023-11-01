/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.googlecontacts

import java.util.regex.Pattern

/**
 * Describes a Google query.
 *
 * @author Sebastien Vincent
 */
class GoogleQuery(query: Pattern?) {
    /**
     * If the query is cancelled.
     */
    private var cancelled = false

    /**
     * The query pattern.
     */
    private var query: Pattern? = null

    /**
     * Constructor.
     *
     * @param query query string
     */
    init {
        this.query = query
    }

    /**
     * Get the query pattern.
     *
     * @return query pattern
     */
    fun getQueryPattern(): Pattern? {
        return query
    }

    /**
     * Cancel the query.
     */
    fun cancel() {
        cancelled = true
    }

    /**
     * If the query has been cancelled.
     *
     * @return true If the query has been cancelled, false otherwise
     */
    fun isCancelled(): Boolean {
        return cancelled
    }
}