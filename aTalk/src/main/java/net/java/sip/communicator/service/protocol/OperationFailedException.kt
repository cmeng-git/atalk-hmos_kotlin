/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol

/**
 * `OperationFailedException` indicates an exception that occurred in the API.
 *
 *
 * `OperationFailedException` contains an error code that gives more information on the
 * exception. The application can obtain the error code using
 * [OperationFailedException.getErrorCode]. The error code values are defined in the
 * `OperationFailedException` fields.
 *
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class OperationFailedException : Exception {
    /**
     * The error code of the exception
     */
    private val errorCode: Int

    /**
     * Creates an exception with the specified error message and error code.
     *
     * @param message A message containing details on the error that caused the exception
     * @param errorCode the error code of the exception (one of the error code fields of this class)
     */
    constructor(message: String?, errorCode: Int) : super(message) {
        this.errorCode = errorCode
    }

    /**
     * Creates an exception with the specified message, errorCode and cause.
     *
     * @param message A message containing details on the error that caused the exception
     * @param errorCode the error code of the exception (one of the error code fields of this class)
     * @param cause the error that caused this exception
     */
    constructor(message: String?, errorCode: Int, cause: Throwable?) : super(message, cause) {
        this.errorCode = errorCode
    }

    /**
     * Obtain the error code value.
     *
     * @return the error code for the exception.
     */
    fun getErrorCode(): Int {
        return errorCode
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * Set when no other error code can describe the exception that occurred.
         */
        const val GENERAL_ERROR = 1

        /**
         * Set when command fails due to a failure in network communications or a transport error.
         */
        const val NETWORK_FAILURE = 2

        /**
         * Set to indicate that a provider needs to be registered or signed on a public service before
         * calling the method that threw the exception.
         */
        const val PROVIDER_NOT_REGISTERED = 3

        /**
         * Set when an operation fails for implementation specific reasons.
         */
        const val INTERNAL_ERROR = 4

        /**
         * Set when an operation fails for an error that has occurred on the server side.
         */
        const val INTERNAL_SERVER_ERROR = 500

        /**
         * Indicates that a user has tried to subscribe to a contact that already had an active
         * subscription.
         */
        const val SUBSCRIPTION_ALREADY_EXISTS = 5

        /**
         * Indicates that a user has tried to create a group that already exist.
         */
        const val CONTACT_GROUP_ALREADY_EXISTS = 6

        /**
         * Indicates that a user has entered wrong account properties, like wrong port for example.
         */
        const val INVALID_ACCOUNT_PROPERTIES = 7

        /**
         * Indicates that authentication with a server has failed.
         */
        const val AUTHENTICATION_FAILED = 401

        /**
         * Indicates that the user is currently not allowed to perform the operation that failed.
         */
        const val FORBIDDEN = 403

        /**
         * Indicates that the user is trying to perform the current operation on a resource that does
         * not exist.
         */
        const val NOT_FOUND = 404

        /**
         * Indicates that the user is trying to perform an operation with an identifier that was already
         * in use on the target resource (e.g. log with a nickname that is already in use in a chat
         * room, or create a chat room on a server that already contains a room with the same ID).
         */
        const val IDENTIFICATION_CONFLICT = 10

        /**
         * Indicates that the exception was thrown because a method has been passed an illegal or
         * inappropriate argument.
         */
        const val ILLEGAL_ARGUMENT = 11

        /**
         * Indicates that the exception was thrown, because the user doesn't have enough privileges.
         * Meant to be used by multi user chat to indicate that the user is trying to make an operation,
         * which requires admin or owner privileges.
         */
        const val NOT_ENOUGH_PRIVILEGES = 12

        /**
         * Indicates that the user is required to be registered before performing the operation. This
         * property is initially created to take care of chat room join error.
         */
        const val REGISTRATION_REQUIRED = 13

        /**
         * Indicates that we are currently not joined to the chat room, over which we try to perform an
         * operation.
         */
        const val CHAT_ROOM_NOT_JOINED = 14

        /**
         * Indicates that the authentication process has been canceled.
         */
        const val AUTHENTICATION_CANCELED = 15

        /**
         * Indicates that the operation has been canceled by the user.
         */
        const val OPERATION_CANCELED = 16

        /**
         * Indicates that the operation has been canceled because of a missing server information.
         */
        const val SERVER_NOT_SPECIFIED = 17

        /**
         * The operation that throws this exception is not supported.
         */
        const val NOT_SUPPORTED_OPERATION = 18

        /**
         * The operation that throws this exception - CAPTCHA verification failed.
         */
        const val CAPTCHA_CHALLENGE = 19
    }
}