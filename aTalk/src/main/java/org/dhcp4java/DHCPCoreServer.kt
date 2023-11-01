/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 *	(c) 2006 Stephan Hadinger
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.dhcp4java

import org.atalk.hmos.plugin.timberlog.TimberLog
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A simple generic DHCP Server.
 *
 * The DHCP Server provided is based on a multi-thread model. The main thread listens
 * at the socket, then dispatches work to a pool of threads running the servlet.
 *
 *
 * Configuration: the Server reads the following properties in "/DHCPd.properties"
 * at the root of the class path. You can however provide a properties set when
 * contructing the server. Default values are:
 *
 * <blockquote>
 * `serverAddress=127.0.0.1:67` *[address:port]*
 * <br></br>
 * `serverThreads=2` *[number of concurrent threads for servlets]*
</blockquote> *
 *
 *
 * Note: this class implements `Runnable` allowing it to be run
 * in a dedicated thread.
 *
 *
 * Example:
 *
 * <pre>
 * public static void main(String[] args) {
 * try {
 * DHCPCoreServer server = DHCPCoreServer.initServer(new DHCPStaticServlet(), null);
 * new Thread(server).start();
 * } catch (DHCPServerInitException e) {
 * // die gracefully
 * }
 * }
</pre> *
 *
 * @author Stephan Hadinger
 * @author Eng Chong Meng
 * @version 1.00
 */
class DHCPCoreServer
/**
 * Constructor
 *
 *
 * Constructor shall not be called directly. New servers are created through
 * `initServer()` factory.
 */
private constructor(
        /**
         * the servlet it must run
         */
        protected var servlet: DHCPServlet,
        /**
         * Reference of user-provided parameters
         */
        protected var userProps: Properties) : Runnable {
    /**
     * working threads pool.
     */
    protected var threadPool: ThreadPoolExecutor? = null

    /**
     * Consolidated parameters of the server.
     */
    protected var properties: Properties? = null
    /**
     * @return Returns the socket address.
     */
    /**
     * IP address and port for the server
     */
    var sockAddress: InetSocketAddress? = null
        private set

    /**
     * The socket for receiving and sending.
     */
    private var serverSocket: DatagramSocket? = null

    /**
     * do we need to stop the server?
     */
    private var stopped = false

    /**
     * Initialize the server context from the Properties, and open socket.
     */
    @Throws(DHCPServerInitException::class)
    protected fun init() {
        check(serverSocket == null) { "Server already initialized" }
        try {
            // default built-in minimal properties
            properties = Properties(DEF_PROPS)

            // try to load default configuration file
            val propFileStream = this.javaClass.getResourceAsStream("/DHCPd.properties")
            if (propFileStream != null) {
                properties!!.load(propFileStream)
            } else {
                Timber.e("Could not load DHCPd.properties")
            }

            // now integrate provided properties
            if (userProps != null) {
                properties!!.putAll(userProps)
            }

            // load socket address, this method may be overriden
            sockAddress = getInetSocketAddress(properties)
            if (sockAddress == null) {
                throw DHCPServerInitException("Cannot find which SockAddress to open")
            }

            // open socket for listening and sending
            serverSocket = DatagramSocket(null)
            serverSocket!!.broadcast = true // allow sending broadcast
            serverSocket!!.bind(sockAddress)

            // initialize Thread Pool
            val numThreads = Integer.parseInt(properties!!.getProperty(SERVER_THREADS))
            val maxThreads = Integer.parseInt(properties!!.getProperty(SERVER_THREADS_MAX))
            val keepaliveThreads = Integer.parseInt(properties!!.getProperty(SERVER_THREADS_KEEPALIVE))
            threadPool = ThreadPoolExecutor(numThreads, maxThreads,
                    keepaliveThreads.toLong(), TimeUnit.MILLISECONDS,
                    ArrayBlockingQueue(BOUNDED_QUEUE_SIZE),
                    ServerThreadFactory())
            threadPool!!.prestartAllCoreThreads()

            // now intialize the servlet
            servlet.server = this
            servlet.init(properties)
        } catch (e: DHCPServerInitException) {
            throw e // transparently re-throw
        } catch (e: Exception) {
            serverSocket = null
            Timber.e(e, "Cannot open socket")
            throw DHCPServerInitException("Unable to init server", e)
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    protected fun dispatch() {
        try {
            val requestDatagram = DatagramPacket(ByteArray(PACKET_SIZE), PACKET_SIZE)
            Timber.log(TimberLog.FINER, "Waiting for packet")

            // receive datagram
            serverSocket!!.receive(requestDatagram)
            if (TimberLog.isTraceEnable) {
                val sbuf = StringBuilder("Received packet from ")
                DHCPPacket.Companion.appendHostAddress(sbuf, requestDatagram.address)
                sbuf.append('(')
                        .append(requestDatagram.port)
                        .append(')')
                Timber.log(TimberLog.FINER, sbuf.toString())
            }

            // send work to thread pool
            val dispatcher = DHCPServletDispatcher(this, servlet, requestDatagram)
            threadPool!!.execute(dispatcher)
        } catch (e: IOException) {
            Timber.log(TimberLog.FINER, e, "IOException")
        }
    }

    /**
     * Send back response packet to client.
     *
     *
     * This is a callback method used by servlet dispatchers to send back responses.
     */
    fun sendResponse(responseDatagram: DatagramPacket?) {
        if (responseDatagram == null) {
            return  // skipping
        }
        try {
            // sending back
            serverSocket!!.send(responseDatagram)
        } catch (e: IOException) {
            Timber.log(TimberLog.FINER, e, "IOException")
        }
    }

    /**
     * Returns the `InetSocketAddress` for the server (client-side).
     *
     * <pre>
     *
     * serverAddress (default 127.0.0.1)
     * serverPort (default 67)
     *
    </pre> *
     *
     *
     *
     * This method can be overriden to specify an non default socket behaviour
     *
     * @param props Properties loaded from /DHCPd.properties
     * @return the socket address, null if there was a problem
     */
    protected fun getInetSocketAddress(props: Properties?): InetSocketAddress {
        requireNotNull(props) { "null props not allowed" }
        val serverAddress = props.getProperty(SERVER_ADDRESS)
                ?: throw IllegalStateException("Cannot load SERVER_ADDRESS property")
        return parseSocketAddress(serverAddress)
    }

    /**
     * This is the main loop for accepting new request and delegating work to
     * servlets in different threads.
     */
    override fun run() {
        checkNotNull(serverSocket) { "Listening socket is not open - terminating" }
        while (!stopped) {
            try {
                dispatch() // do the stuff
            } catch (e: Exception) {
                Timber.w(e, "Unexpected Exception")
            }
        }
    }

    /**
     * This method stops the server and closes the socket.
     */
    fun stopServer() {
        stopped = true
        serverSocket!!.close() // this generates an exception when trying to receive
    }

    private class ServerThreadFactory internal constructor() : ThreadFactory {
        val threadNumber = AtomicInteger(1)
        val namePrefix: String

        init {
            namePrefix = "DHCPCoreServer-" + poolNumber.getAndIncrement() + "-thread-"
        }

        override fun newThread(runnable: Runnable): Thread {
            return Thread(runnable, namePrefix + threadNumber.getAndIncrement())
        }

        companion object {
            private val poolNumber = AtomicInteger(1)
        }
    }

    companion object {
        private const val BOUNDED_QUEUE_SIZE = 20

        /**
         * default MTU for ethernet
         */
        protected const val PACKET_SIZE = 1500

        /**
         * Creates and initializes a new DHCP Server.
         *
         *
         * It instanciates the object, then calls `init()` method.
         *
         * @param servlet the `DHCPServlet` instance processing incoming requests,
         * must not be `null`.
         * @param userProps specific properties, overriding file and default properties,
         * may be `null`.
         * @return the new `DHCPCoreServer` instance (never null).
         * @throws DHCPServerInitException unable to start the server.
         */
        @Throws(DHCPServerInitException::class)
        fun initServer(servlet: DHCPServlet?, userProps: Properties): DHCPCoreServer {
            requireNotNull(servlet) { "servlet must not be null" }
            val server = DHCPCoreServer(servlet, userProps)
            server.init()
            return server
        }

        /**
         * Parse a string of the form 'server:port' or '192.168.1.10:67'.
         *
         * @param address string to parse
         * @return InetSocketAddress newly created
         * @throws IllegalArgumentException if unable to parse string
         */
        fun parseSocketAddress(address: String?): InetSocketAddress {
            requireNotNull(address) { "Null address not allowed" }
            val index = address.indexOf(':')
            require(index > 0) { "semicolon missing for port number" }
            val serverStr = address.substring(0, index)
            val portStr = address.substring(index + 1)
            val port = portStr.toInt()
            return InetSocketAddress(serverStr, port)
        }

        private val DEF_PROPS = Properties()
        const val SERVER_ADDRESS = "serverAddress"
        private const val SERVER_ADDRESS_DEFAULT = "127.0.0.1:67"
        const val SERVER_THREADS = "serverThreads"
        private const val SERVER_THREADS_DEFAULT = "2"
        const val SERVER_THREADS_MAX = "serverThreadsMax"
        private const val SERVER_THREADS_MAX_DEFAULT = "4"
        const val SERVER_THREADS_KEEPALIVE = "serverThreadsKeepalive"
        private const val SERVER_THREADS_KEEPALIVE_DEFAULT = "10000"

        init {
            // initialize defProps
            DEF_PROPS[SERVER_ADDRESS] = SERVER_ADDRESS_DEFAULT
            DEF_PROPS[SERVER_THREADS] = SERVER_THREADS_DEFAULT
            DEF_PROPS[SERVER_THREADS_MAX] = SERVER_THREADS_MAX_DEFAULT
            DEF_PROPS[SERVER_THREADS_KEEPALIVE] = SERVER_THREADS_KEEPALIVE_DEFAULT
        }
    }
}

/**
 * Servlet dispatcher
 */
internal class DHCPServletDispatcher(private val server: DHCPCoreServer, private val dispatchServlet: DHCPServlet, private val dispatchPacket: DatagramPacket) : Runnable {
    override fun run() {
        try {
            val response = dispatchServlet.serviceDatagram(dispatchPacket)
            server.sendResponse(response) // invoke callback method
        } catch (e: Exception) {
            Timber.log(TimberLog.FINER, e, "Exception in dispatcher")
        }
    }
}