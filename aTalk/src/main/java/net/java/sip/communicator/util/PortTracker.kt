/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util

import timber.log.Timber

/**
 * The `PortTracker` class allows for a controlled selection of bind
 * ports. This is typically useful in cases where we would like to set bounds
 * for the ports that we are going to use for a particular socket. For example,
 * at the time of writing of this class, this policy allows Jitsi to bind RTP
 * sockets on ports that are always between 5000 and 6000 (default values). It
 * is also used to allow for different port ranges for Audio and Video streams.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
class PortTracker(minPort: Int, maxPort: Int) {
    /**
     * Returns the lowest/minimum port that this tracker would use.
     *
     * @return the minimum port number allowed by this tracker.
     */
    /**
     * The minimum port number that this allocator would be allocate to return.
     */
    var minPort = NetworkUtils.MIN_PORT_NUMBER
        private set
    /**
     * Returns the highest/maximum port that this tracker would use.
     *
     * @return the maximum port number allowed by this tracker.
     */
    /**
     * The maximum port number that this allocator would be allocate to return.
     */
    var maxPort = NetworkUtils.MAX_PORT_NUMBER
        private set
    /**
     * Returns the next port that the using class is supposed to try allocating.
     *
     * @return the next port that the using class is supposed to try allocating.
     */
    /**
     * The next port that we will return if asked.
     */
    var port = -1
        private set

    /**
     * Initializes a port tracker with the specified port range.
     *
     * @param minPort the minimum port that we would like to bind on
     * @param maxPort the maximum port that we would like to bind on
     */
    init {
        setRange(minPort, maxPort)
    }

    /**
     * (Re)Sets the range that this tracker returns values in. The method would
     * also update the value of the next port to allocate in case it is
     * currently outside the specified range. The method also allows configuring
     * this allocator in a way that it would always return the same port. This
     * would happen when `newMinPort` is equal to `newMaxPort`
     * which would make both equal to the only possible value.
     *
     * @param newMinPort the minimum port that we would like to bind on
     * @param newMaxPort the maximum port that we would like to bind on
     * @throws IllegalArgumentException if the arguments do not correspond to
     * valid port numbers, or in case newMaxPort < newMinPort
     */
    @Throws(IllegalArgumentException::class)
    fun setRange(newMinPort: Int, newMaxPort: Int) {
        //validate
        require(!(newMaxPort < newMinPort
                || !NetworkUtils.isValidPortNumber(newMinPort)
                || !NetworkUtils.isValidPortNumber(newMaxPort))) { "[$newMinPort, $newMaxPort] is not a valid port range." }

        //reset bounds
        minPort = newMinPort
        maxPort = newMaxPort

        /*
         * Make sure that nextPort is within the specified range. Preserve value
         * if already valid.
         */
        if (port < minPort || port > maxPort) port = minPort
    }

    /**
     * Attempts to set the range specified by the min and max port string
     * params. If the attempt fails, for reasons such as invalid porameters,
     * this method will simply return without an exception and without an impact
     * on the state of this class.
     *
     * @param newMinPort the minimum port that we would like to bind on
     * @param newMaxPort the maximum port that we would like to bind on
     */
    fun tryRange(newMinPort: String, newMaxPort: String) {
        try {
            setRange(newMinPort.toInt(), newMaxPort.toInt())
        } catch (e: Exception) //Null, NumberFormat, IllegalArgument
        {
            Timber.i("Ignoring invalid port range [%s, %s]", newMinPort, newMaxPort)
            Timber.d("Cause: %s", e.message)
        }
    }

    /**
     * Sets the next port to specified value unless it is outside the range that
     * this tracker operates in, in which case it sets it to the minimal possible.
     *
     * @param nextPort the next port we'd like this tracker to return.
     */
    fun setNextPort(nextPort: Int) {
        /*
         * Make sure that nextPort is within the specified range unless
         */
        if (nextPort < minPort || nextPort > maxPort) {
            port = minPort
        } else {
            port = nextPort
        }
    }

    companion object {
        /**
         * Attempts to create a port tracker that uses the min and max values
         * indicated by the `newMinPortString` and `newMinPortString`
         * strings and returns it if successful. The method fails silently (returning `null`) otherwise.
         *
         * @param newMinPortString the [String] containing the minimum port
         * number that this tracker should allow.
         * @param newMaxPortString the [String] containing the minimum port
         * number that this tracker should allow.
         * @return the newly created port tracker or `null` if the string
         * params do not contain valid port numbers.
         */
        fun createTracker(newMinPortString: String,
                          newMaxPortString: String): PortTracker? {
            return try {
                val minPort = newMinPortString.toInt()
                val maxPort = newMaxPortString.toInt()
                PortTracker(minPort, maxPort)
            } catch (exc: Exception) //Null, NumberFormat, IllegalArgument
            {
                Timber.i("Ignoring invalid port range [%s to %s]", newMinPortString, newMaxPortString)
                Timber.d("Cause: %s", exc.message)
                null
            }
        }
    }
}