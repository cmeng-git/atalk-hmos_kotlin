/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.launchutils

import net.java.sip.communicator.util.ScStdOut
import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.service.configuration.ConfigurationService
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.*
import java.util.logging.ConsoleHandler
import java.util.logging.Logger

/**
 * The `LauncherArgHandler` class handles invocation arguments that have
 * been passed to us when running SIP Communicator. The class supports a fixed
 * set of options and also allows for registration of delegates.
 *
 * @author Emil Ivov <emcho at sip-communicator.org>
 * @author Eng Chong Meng
</emcho> */
class LaunchArgHandler private constructor() {
    /**
     * Returns an error code that could help identify an error when
     * `handleArgs` returns ACTION_ERROR or 0 if everything went fine.
     *
     * @return an error code that could help identify an error when
     * `handleArgs` returns ACTION_ERROR or 0 if everything went fine.
     */
    /**
     * The errorCode identifying the error that occurred last time `handleArgs` was called.
     */
    var errorCode = 0
        private set

    /**
     * A reference to the instance of the
     */
    private val argDelegator = ArgDelegator()

    /**
     * The properties where we load version info from our update location.
     */
    private val versionProperties = Properties()

    /**
     * Creates the sole instance of this class;
     */
    init {
        val versionPropertiesStream = javaClass.getResourceAsStream(VERSION_PROPERTIES)
        var versionPropertiesAreLoaded = false
        if (versionPropertiesStream != null) {
            try {
                versionPropertiesAreLoaded = try {
                    versionProperties.load(versionPropertiesStream)
                    true
                } finally {
                    versionPropertiesStream.close()
                }
            } catch (exc: IOException) {
                // no need to worry the user, so only print if we're in FINEST
            }
        }
        if (!versionPropertiesAreLoaded) {
            Timber.log(TimberLog.FINER, "Couldn't open version.properties")
        }

        // Start url handler for Mac OS X.
        /*
         * XXX The detection of the operating systems is the responsibility of
         * OSUtils. It used to reside in the util.jar which is in the classpath
         * but it is now in libjitsi.jar which is not in the classpath.
         */
        val osName = System.getProperty("os.name")
        if (osName != null && osName.startsWith("Mac")) AEGetURLEventHandler(this)
    }

    /**
     * Does the actual argument handling.
     *
     * @param args the arguments the way we have received them from the main() method.
     * @return one of the ACTION_XXX fields defined here, intended to indicate
     * to the caller they action that they are supposed as a result of the arg handling.
     */
    fun handleArgs(args: Array<String>): Int {
        var returnAction = ACTION_CONTINUE
        var i = 0
        while (i < args.size) {
            Timber.log(TimberLog.FINER, "handling arg %s", i)
            if (args[i] == "--version" || args[i] == "-v") {
                handleVersionArg()
                //we're supposed to exit after printing version info
                returnAction = ACTION_EXIT
                break
            } else if (args[i] == "--help" || args[i] == "-h") {
                handleHelpArg()
                //we're supposed to exit after printing the help message
                returnAction = ACTION_EXIT
                break
            } else if (args[i] == "--debug" || args[i] == "-d") {
                handleDebugArg(args[i])
                i++
                continue
            } else if (args[i] == "--ipv6" || args[i] == "-6") {
                handleIPv6Enforcement()
                break
            } else if (args[i] == "--ipv4" || args[i] == "-4") {
                handleIPv4Enforcement()
                break
            } else if (args[i].startsWith("--config=")) {
                returnAction = handleConfigArg(args[i])
                if (returnAction == ACTION_ERROR) break else {
                    i++
                    continue
                }
            } else if (args[i] == "-c") {
                //make sure we have at least one more argument left.
                if (i == args.size - 1) {
                    println("The \"-c\" option expects a directory parameter.")
                    returnAction = ACTION_ERROR
                    break
                }
                handleConfigArg(args[++i])
                i++
                continue
            } else if (args[i] == "--multiple" || args[i] == "-m") {
                returnAction = ACTION_CONTINUE_LOCK_DISABLED
                i++
                continue
            } else if (args[i].startsWith("--splash=")) {
                // do nothing already handled by startup script/binary
                i++
                continue
            } else if (args[i].startsWith("--notray")) {
                System.setProperty("disable-tray", "true")
                i++
                continue
            } else if (i == args.size - 1
                    && !args[i].startsWith("-")) {
                handleUri(args[i])
            } else {
                handleUnknownArg(args[i])
                errorCode = ERROR_CODE_UNKNOWN_ARG
                returnAction = ACTION_ERROR
                break
            }
            i++
        }
        return returnAction
    }

    /**
     * Forces use of IPv6 addresses where possible. (This should one day become a default mode of operation.)
     */
    private fun handleIPv6Enforcement() {
        System.setProperty("java.net.preferIPv4Stack", "false")
        System.setProperty("java.net.preferIPv6Addresses", "true")
    }

    /**
     * Forces non-support for IPv6 and use of IPv4 only.
     */
    private fun handleIPv4Enforcement() {
        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv6Addresses", "false")
    }

    /**
     * Passes `uriArg` to our uri manager for handling.
     *
     * @param uri the uri that we'd like to pass to
     */
    private fun handleUri(uri: String) {
        Timber.log(TimberLog.FINER, "Handling uri %s", uri)
        argDelegator.handleUri(uri)
    }

    /**
     * Instructs SIP Communicator to print logging messages to the console.
     *
     * @param arg the debug arg which we are not really using in this method.
     */
    private fun handleDebugArg(arg: String) {
        //first enable standard out printing
        ScStdOut.Companion.setStdOutPrintingEnabled(true)

        //then find a console handler (or create a new one) and set its level to FINEST
        val rootLogger = Logger.getAnonymousLogger().parent!!
        var conHan: ConsoleHandler? = null
        for (handler in rootLogger.handlers) {
            if (handler is ConsoleHandler) {
                conHan = handler
                break
            }
        }
        if (conHan == null) {
            conHan = ConsoleHandler()
            rootLogger.addHandler(conHan)
        }
        //conHan.setLevel(Level.SEVERE);
    }

    /**
     * Instructs SIP Communicator change the location of its home dir.
     *
     * @param configArg the arg containing the location of the new dir.
     * @return either ACTION_ERROR or ACTION_CONTINUE depending on whether or
     * not parsing the option went fine.
     */
    private fun handleConfigArg(configArg: String): Int {
        var configArg = configArg
        if (configArg.startsWith("--config=")) {
            configArg = configArg.substring("--config=".length)
        }
        val configDir = File(configArg)
        configDir.mkdirs()
        if (!configDir.isDirectory) {
            println("Failed to create directory $configArg")
            errorCode = ERROR_CODE_CREATE_DIR_FAILED
            return ACTION_ERROR
        }
        System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_LOCATION, configDir.parent)
        System.setProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION, configDir.parent)
        System.setProperty(ConfigurationService.PNAME_SC_LOG_DIR_LOCATION, configDir.parent)
        System.setProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME, configDir.name)

        //we instantiated our class logger before we had a chance to change the dir so we need to reset it now.
        logger.reset()
        return ACTION_CONTINUE
    }

    /**
     * Prints the name and the version of this application. This method uses the
     * version.properties file which is created by ant during the build process.
     * If this file does not exist the method would print a default name and version string.
     */
    private fun handleVersionArg() {
        var name = applicationName
        var version = version
        if (name == null || name.trim { it <= ' ' }.isEmpty()) {
            name = "atalk"
        }
        if ((version == null) || (version.trim { it <= ' ' }.isEmpty())) {
            version = "build.by.SVN"
        }
        println("$name $version")
    }

    /**
     * Returns the version of the SIP Communicator instance that we are currently running.
     *
     * @return a String containing the version of the SC instance we are currently running.
     */
    private val version: String
        private get() {
            val version = versionProperties.getProperty(PNAME_VERSION)
            return version ?: "build.by.SVN"
        }

    /**
     * Returns the name of the application. That should be aTalk most of the time but who knows ..
     *
     * @return the name of the application (i.e. SIP Communicator until we change our name some day.)
     */
    private val applicationName: String
        private get() {
            val name = versionProperties.getProperty(PNAME_APPLICATION_NAME)
            return name ?: "atalk"
        }

    /**
     * Returns the package name of the application. That should be jitsi most of the time but who knows ..
     *
     * @return the package name of the application.
     */
    private val packageName: String
        private get() {
            val name = versionProperties.getProperty(PNAME_PACKAGE_NAME)
            return name ?: "atalk"
        }

    /**
     * Prints an error message and then prints the help message.
     *
     * @param arg the unknown argument we need to print
     */
    fun handleUnknownArg(arg: String) {
        println("Unknown argument: $arg")
        handleHelpArg()
    }

    /**
     * Prints a help message containing usage instructions and descriptions of
     * all options currently supported by Jitsi.
     */
    fun handleHelpArg() {
        handleVersionArg()
        println("Usage: " + packageName + " [OPTIONS] [uri-to-call]")
        println("")
        println("  -c, --config=DIR  use DIR for config files")
        println("  -d, --debug       print debugging messages to stdout")
        println("  -h, --help        display this help message and exit")
        println("  -m, --multiple    do not ensure single instance")
        println("  -6, --ipv6        prefer IPv6 addresses where possible only")
        println("  -4, --ipv4        forces use of IPv4 only")
        println("  -v, --version     display the current version and exit")
        println("  -n, --notray      disable the tray icon and show the GUI")
    }

    /**
     * Sets the `delegationPeer` that would be handling all URIs passed
     * as command line arguments to SIP Communicator.
     *
     * @param delegationPeer the `delegationPeer` that should handle URIs
     * or `null` if we'd like to unset a previously set peer.
     */
    fun setDelegationPeer(delegationPeer: ArgDelegationPeer?) {
        argDelegator.setDelegationPeer(delegationPeer)
    }

    /**
     * Called when the user has tried to launch a second instance of
     * SIP Communicator while a first one was already running. This method
     * only handles arguments that need to be handled by a running instance
     * of SIP Communicator assuming that simple ones such as "--version" or
     * "--help" have been handled by the calling instance.
     *
     * @param args the args that we need to handle.
     */
    fun handleConcurrentInvocationRequestArgs(args: Array<String>) {
        //if we have 1 or more args then we only care about the last one since
        //the only interinstance arg we currently know how to handle are URIs.
        //Change this if one day we implement fun stuff like inter instance command execution.
        if (args.size >= 1
                && !args[args.size - 1].startsWith("-")) {
            argDelegator.handleUri(args[args.size - 1])
        } else {
            argDelegator.handleConcurrentInvocationRequest()
        }
    }

    companion object {
        /**
         * Our class logger.
         */
        private val logger = net.java.sip.communicator.util.Logger.getLogger(LaunchArgHandler::class.java)

        /**
         * Returned by the `handleArgs` methods when the arguments that have
         * been parsed do not require for SIP Communicator to be started and the
         * Launcher is supposed to exit. That could happen when "SIP Communicator"
         * is launched with a --version argument for example or when trying to
         * run the application after an instance was already launched.
         */
        const val ACTION_EXIT = 0

        /**
         * Returned by the `handleArgs` methods when all arguments have been
         * parsed and the SIP Communicator launch can continue.
         */
        const val ACTION_CONTINUE = 1

        /**
         * Returned by the `handleArgs` method when parsing the arguments
         * has failed or if no arguments were passed and an instance of SC was already
         * launched. If this is the code returned by handleArgs, then the `getErrorCode`
         * method would return an error code indicating what the error was.
         */
        const val ACTION_ERROR = 2

        /**
         * Returned by the `handleArgs` methods when all arguments have been successfully
         * parsed and one of them indicates that the user has requested a multi instance launch.
         */
        const val ACTION_CONTINUE_LOCK_DISABLED = 3

        /**
         * The error code returned when we couldn't parse one of the options.
         */
        const val ERROR_CODE_UNKNOWN_ARG = 1

        /**
         * The error code returned when we try to launch SIP Communicator while there is already
         * a running instance and there were no arguments that we forward to that instance.
         */
        const val ERROR_CODE_ALREADY_STARTED = 2

        /**
         * The error code that we return when we fail to create a directory that has
         * been specified with the -c|--config option.
         */
        const val ERROR_CODE_CREATE_DIR_FAILED = 3

        /**
         * The property name containing the name of the application (e.g. SIP Communicator)
         */
        private const val PNAME_APPLICATION_NAME = "APPLICATION_NAME"

        /**
         * The package name of the applications (e.g. atalk).
         */
        private const val PNAME_PACKAGE_NAME = "PACKAGE_NAME"

        /**
         * The property name containing the current version.
         */
        private const val PNAME_VERSION = "APPLICATION_VERSION"

        /**
         * The name of the file containing version properties for use with the argument handler.
         */
        private const val VERSION_PROPERTIES = "version.properties"

        /**
         * The singleton instance of this handler.
         */
        private var argHandler: LaunchArgHandler? = null

        /**
         * Creates a singleton instance of the LauncherArgHandler if necessary and returns a reference to it.
         *
         * @return the singleton instance of the LauncherArgHandler.
         */
        val instance: LaunchArgHandler?
            get() {
                if (argHandler == null) {
                    argHandler = LaunchArgHandler()
                }
                return argHandler
            }
    }
}