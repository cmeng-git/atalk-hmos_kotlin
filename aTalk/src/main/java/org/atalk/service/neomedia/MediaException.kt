/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia

/**
 * Implements an `Exception` thrown by the neomedia service interfaces and their
 * implementations. `MediaException` carries an error code in addition to the standard
 * `Exception` properties which gives more information about the specifics of the particular
 * `MediaException`.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
class MediaException : Exception {
    /**
     * Gets the error code carried by this `MediaException` which gives more information
     * about the specifics of this `MediaException`.
     *
     * @return the error code carried by this `MediaException` which gives more information
     * about the specifics of this `MediaException`
     */
    /**
     * The error code carried by this `MediaException` which gives more information about the
     * specifics of this `MediaException`.
     */
    val errorCode: Int
    /**
     * Initializes a new `MediaException` instance with a specific detailed message and a
     * specific error code.
     *
     * @param message
     * the detailed message to initialize the new instance with
     * @param errorCode
     * the error code which is to give more information about the specifics of the new
     * instance
     */
    /**
     * Initializes a new `MediaException` instance with a specific detailed message and
     * [.GENERAL_ERROR] error code.
     *
     * @param message
     * the detailed message to initialize the new instance with
     */
    @JvmOverloads
    constructor(message: String?, errorCode: Int = GENERAL_ERROR) : super(message) {
        this.errorCode = errorCode
    }

    /**
     * Initializes a new `MediaException` instance with a specific detailed message,
     * [.GENERAL_ERROR] error code and a specific `Throwable` cause.
     *
     * @param message
     * the detailed message to initialize the new instance with
     * @param cause
     * the `Throwable` which is to be carried by the new instance and which is to be
     * reported as the cause for throwing the new instance. If `cause` is
     * `null`, the cause for throwing the new instance is considered to be unknown.
     */
    constructor(message: String?, cause: Throwable?) : this(message, GENERAL_ERROR, cause) {}

    /**
     * Initializes a new `MediaException` instance with a specific detailed message, a
     * specific error code and a specific `Throwable` cause.
     *
     * @param message
     * the detailed message to initialize the new instance with
     * @param errorCode
     * the error code which is to give more information about the specifics of the new
     * instance
     * @param cause
     * the `Throwable` which is to be carried by the new instance and which is to be
     * reported as the cause for throwing the new instance. If `cause` is
     * `null`, the cause for throwing the new instance is considered to be unknown.
     */
    constructor(message: String?, errorCode: Int, cause: Throwable?) : super(message, cause) {
        this.errorCode = errorCode
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The error code value which specifies that the `MediaException` carrying it does not
         * give more information about its specifics.
         */
        const val GENERAL_ERROR = 1
    }
}