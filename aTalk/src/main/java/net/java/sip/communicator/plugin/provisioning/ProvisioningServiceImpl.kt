package net.java.sip.communicator.plugin.provisioning

import net.java.sip.communicator.service.provisioning.ProvisioningService
import net.java.sip.communicator.util.OrderedProperties
import org.apache.commons.lang3.StringUtils
import org.atalk.hmos.R
import org.atalk.hmos.aTalkApp
import org.atalk.hmos.gui.dialogs.DialogActivity
import org.atalk.service.httputil.HttpUtils
import org.atalk.service.resources.ResourceManagementService
import org.atalk.util.OSUtils
import org.json.JSONException
import org.json.JSONObject
import org.osgi.framework.BundleException
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Provisioning service.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
class ProvisioningServiceImpl : ProvisioningService {
    /**
     * List of allowed configuration prefixes.
     */
    private val allowedPrefixes = ArrayList<String>()

    /**
     * Constructor.
     */
    init {
        // check if UUID is already configured
        var uuid = ProvisioningActivator.configurationService!!.getProperty(PROVISIONING_UUID_PROP) as String?
        if (uuid == null || uuid == "") {
            uuid = UUID.randomUUID().toString()
            ProvisioningActivator.configurationService!!.setProperty(PROVISIONING_UUID_PROP, uuid)
        }
    }

    /**
     * Starts provisioning.
     *
     * @param url_ provisioning URL
     */
    fun start(url_: String?) {
        var url = url_
        if (url == null) {
            /* try to see if provisioning URL is stored in properties */
            url = provisioningUri
        }
        if (StringUtils.isNotEmpty(url)) {
            val data = retrieveConfigurationFile(url)
            if (data != null) {
                /*
                 * store the provisioning URL in local configuration in case the provisioning
                 * discovery failed (DHCP/DNS unavailable, ...)
                 */
                ProvisioningActivator.configurationService!!.setProperty(PROPERTY_PROVISIONING_URL, url)
                updateConfiguration(data)
            }
        }
    }
    /**
     * Indicates if the provisioning has been enabled.
     *
     * @return `true` if the provisioning is enabled, `false` - otherwise
     */
    /**
     * Enables the provisioning with the given method. If the provisioningMethod is null disables the provisioning.
     */
    override var provisioningMethod: String?
        get() {
            var provMethod = ProvisioningActivator.configurationService!!.getString(PROVISIONING_METHOD_PROP)
            if (provMethod == null || provMethod.isEmpty()) {
                provMethod = ProvisioningActivator.resourceService!!
                        .getSettingsString("plugin.provisioning.DEFAULT_PROVISIONING_METHOD")
                if (provMethod != null && provMethod.isNotEmpty()) provisioningMethod = provMethod
            }
            return provMethod
        }
        set(provisioningMethod) {
            ProvisioningActivator.configurationService!!.setProperty(PROVISIONING_METHOD_PROP, provisioningMethod)
        }

    /**
     * Returns the provisioning URI.
     */
    override var provisioningUri: String?
        get() {
            var provUri = ProvisioningActivator.configurationService!!.getString(PROPERTY_PROVISIONING_URL)
            if (provUri == null || provUri.isEmpty()) {
                provUri = ProvisioningActivator.resourceService!!
                        .getSettingsString("plugin.provisioning.DEFAULT_PROVISIONING_URI")
                if (provUri != null && provUri.isNotEmpty()) provisioningUri = provUri
            }
            return provUri
        }
        set(uri) {
            ProvisioningActivator.configurationService!!.setProperty(PROPERTY_PROVISIONING_URL, uri)
        }

    override var provisioningUsername: String? = null

    override var provisioningPassword: String? = null

    /**
     * Retrieve configuration file from provisioning URL. This method is blocking until
     * configuration file is retrieved from the network or if an exception happen
     *
     * url provisioning URL
     * jsonParameters the already filled parameters if any.
     * Stream of provisioning data
     */
    /**
     * Retrieve configuration file from provisioning URL. This method is blocking until
     * configuration file is retrieved from the network or if an exception happen
     *
     * @param url provisioning URL
     * @return Stream of provisioning data
     */
    private fun retrieveConfigurationFile(url: String?, jsonParameters: JSONObject? = null): InputStream? {
        var url1 = url
        return try {
            val arg: String
            var args: List<String>? = null
            val ipaddr = ProvisioningActivator.networkAddressManagerService!!
                    .getLocalHost(InetAddress.getByName(URL(url1).host))

            // Get any system environment identified by ${env.xyz}
            var p = Pattern.compile("\\$\\{env\\.([^}]*)}")
            var m = p.matcher(url1!!)
            var sb = StringBuffer()
            while (m.find()) {
                val value = System.getenv(m.group(1)!!)
                if (value != null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(value))
                }
            }
            m.appendTail(sb)
            url1 = sb.toString()

            // Get any system property variable identified by ${system.xyz}
            p = Pattern.compile("\\$\\{system\\.([^}]*)}")
            m = p.matcher(url1)
            sb = StringBuffer()
            while (m.find()) {
                val value = System.getProperty(m.group(1)!!)
                if (value != null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(value))
                }
            }
            m.appendTail(sb)
            url1 = sb.toString()
            if (url1.contains("\${home.location}")) {
                url1 = url1.replace("\${home.location}",
                        ProvisioningActivator.configurationService!!.scHomeDirLocation!!)
            }
            if (url1.contains("\${home.name}")) {
                url1 = url1.replace("\${home.name}",
                        ProvisioningActivator.configurationService!!.scHomeDirName!!)
            }
            if (url1.contains("\${uuid}")) {
                url1 = url1.replace("\${uuid}",
                        (ProvisioningActivator.configurationService!!.getProperty(PROVISIONING_UUID_PROP) as String))
            }
            if (url1.contains("\${osname}")) {
                url1 = url1.replace("\${osname}", System.getProperty("os.name")!!)
            }
            if (url1.contains("\${arch}")) {
                url1 = url1.replace("\${arch}", System.getProperty("os.arch")!!)
            }

//            if (url.indexOf("${resx}") != -1 || url.indexOf("${resy}") != -1) {
//                Rectangle screen = ScreenInformation.getScreenBounds();
//
//                if (url.indexOf("${resx}") != -1) {
//                    url = url.replace("${resx}", String.valueOf(screen.width));
//                }
//
//                if (url.indexOf("${resy}") != -1) {
//                    url = url.replace("${resy}", String.valueOf(screen.height));
//                }
//            }
            if (url1.contains("\${build}")) {
                url1 = url1.replace("\${build}", System.getProperty("sip-communicator.version")!!)
            }
            if (url1.contains("\${locale}")) {
                var locale = ProvisioningActivator.configurationService!!
                        .getString(ResourceManagementService.DEFAULT_LOCALE_CONFIG)
                if (locale == null) locale = ""
                url1 = url1.replace("\${locale}", locale)
            }
            if (url1.contains("\${ipaddr}")) {
                url1 = url1.replace("\${ipaddr}", ipaddr.hostAddress!!)
            }
            if (url1.contains("\${hostname}")) {
                val name = if (OSUtils.IS_WINDOWS) {
                    // avoid reverse DNS lookup
                    System.getenv("COMPUTERNAME")
                } else {
                    ipaddr.hostName
                }
                url1 = url1.replace("\${hostname}", name!!)
            }
            if (url1.contains("\${hwaddr}")) {
                /*
                 * find the hardware address of the interface that has this IP address
                 */
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val iface = en.nextElement()
                    val enInet = iface.inetAddresses
                    while (enInet.hasMoreElements()) {
                        val inet = enInet.nextElement()
                        if (inet == ipaddr) {
                            val hw = ProvisioningActivator.networkAddressManagerService!!.getHardwareAddress(iface)
                            if (hw == null || hw.isEmpty()) continue
                            val buf = StringBuilder()
                            for (h in hw) {
                                val hi = if (h >= 0) h.toInt() else h + 256
                                var t = if (hi <= 0xf) "0" else ""
                                t += Integer.toHexString(hi)
                                buf.append(t)
                                buf.append(":")
                            }
                            buf.deleteCharAt(buf.length - 1)
                            url1 = url1!!.replace("\${hwaddr}", buf.toString())
                            break
                        }
                    }
                }
            }
            if (url1!!.contains("?")) {
                /*
                 * do not handle URL of type http://domain/index.php? (no parameters)
                 */
                if (url1.indexOf('?') + 1 != url1.length) {
                    arg = url1.substring(url1.indexOf('?') + 1)
                    args = arg.split("&")
                }
                url1 = url1.substring(0, url1.indexOf('?'))
            }
            var username: String? = null
            var password: String? = null
            val jsonBody = JSONObject()
            if (args != null && args.isNotEmpty()) {
                val usernameParam = "\${username}"
                val passwordParam = "\${password}"
                for (param in args) {
                    var paramValue = ""
                    val params = param.split("=")
                    val paramName = params[0]
                    if (params.size == 2) {
                        paramValue = params[1]
                    }

                    // pre loaded value we will reuse.
                    val preloadedParamValue = getParamValue(jsonParameters, paramName)

                    // If we find the username or password parameter at this stage we replace it
                    // with an empty string. or if we have an already filled value we will reuse it.
                    if (param.contains(usernameParam)) {
                        username = preloadedParamValue ?: paramValue
                        continue
                    } else if (param.contains(passwordParam)) {
                        password = preloadedParamValue ?: paramValue
                        continue
                    }
                    jsonBody.put(paramName, paramValue)
                }
            }
            var res: HttpUtils.HTTPResponseResult? = null
            var errorWhileProvisioning: IOException? = null
            try {
                res = HttpUtils.postForm(url1, PROPERTY_PROVISIONING_USERNAME, PROPERTY_PROVISIONING_PASSWORD,
                        jsonBody, username, password, null, null)
            } catch (e: IOException) {
                Timber.e("Error posting form: %s", e.message)
                errorWhileProvisioning = e
            }

            // if there was an error in retrieving stop
            // if canceled, lets check whether provisioning is mandatory
            if (res == null) {
                if (ProvisioningActivator.configurationService!!.getBoolean(PROPERTY_PROVISIONING_MANDATORY, false)) {
                    val errorMsg = if (errorWhileProvisioning != null) errorWhileProvisioning.localizedMessage!! else ""
                    DialogActivity.showDialog(aTalkApp.globalContext, R.string.plugin_provisioning_PROV_FAILED,
                            R.string.plugin_provisioning_PROV_FAILED_MSG, errorMsg)

                    // as shutdown service is not started and other bundles are scheduled to start, stop all of them
                    run {
                        for (b in ProvisioningActivator.bundleContext!!.bundles) {
                            try {
                                // skip our Bundle avoiding stopping us while starting and NPE in felix
                                if (ProvisioningActivator.bundleContext == b.bundleContext) {
                                    continue
                                }
                                b.stop()
                            } catch (ex: BundleException) {
                                Timber.e(ex, "Failed to being gentle stop %s", b.location)
                            }
                        }
                    }
                }
                // stop processing
                return null
            }
            val userPass = res.credentials
            if (userPass[0] != null && userPass[1] != null) {
                provisioningUsername = userPass[0]
                provisioningPassword = userPass[1]
            }
            val `in` = res.content

            // Skips ProgressMonitorInputStream wrapper on Android
            if (!OSUtils.IS_ANDROID) {
                // Chain a ProgressMonitorInputStream to the URLConnection's InputStream
//				final ProgressMonitorInputStream pin;
//				pin = new ProgressMonitorInputStream(null, u.toString(), in);
//
//				// Set the maximum value of the ProgressMonitor
//				ProgressMonitor pm = pin.getProgressMonitor();
//				pm.setMaximum((int) res.getContentLength());

                // Uses ProgressMonitorInputStream if available
                null //pin;
            } else `in`
        } catch (e: Exception) {
            Timber.i(e, "Error retrieving provisioning file!")
            null
        }
    }

    /**
     * Update configuration with properties retrieved from provisioning URL.
     *
     * @param data Provisioning data
     */
    private fun updateConfiguration(data: InputStream) {
        val fileProps = OrderedProperties()
        try {
            BufferedInputStream(data).use { `in` ->
                fileProps.load(`in`)
                for ((key1, value) in fileProps) {
                    val key = key1 as String

                    // skip empty keys, prevent them going into the configuration
                    if (key.trim { it <= ' ' }.isEmpty()) continue
                    if (key == PROVISIONING_ALLOW_PREFIX_PROP) {
                        val prefixes = (value as String).split("\\|".toRegex()).toTypedArray()

                        /* updates allowed prefixes list */
                        Collections.addAll(allowedPrefixes, *prefixes)
                        continue
                    } else if (key == PROVISIONING_ENFORCE_PREFIX_PROP) {
                        checkEnforcePrefix(value as String)
                        continue
                    }

                    /* check that properties is allowed */
                    if (!isPrefixAllowed(key)) {
                        continue
                    }
                    processProperty(key, value)
                }
                try {
                    /* save and reload the "new" configuration */
                    ProvisioningActivator.configurationService!!.storeConfiguration()
                    ProvisioningActivator.configurationService!!.reloadConfiguration()
                } catch (e: Exception) {
                    Timber.e("Cannot reload configuration")
                }
            }
        } catch (e: IOException) {
            Timber.w("Error during load of provisioning file")
        }
    }

    /**
     * Check if a property name belongs to the allowed prefixes.
     *
     * @param key property key name
     * @return true if key is allowed, false otherwise
     */
    private fun isPrefixAllowed(key: String): Boolean {
        return if (allowedPrefixes.isNotEmpty()) {
            for (s in allowedPrefixes) {
                if (key.startsWith(s)) {
                    return true
                }
            }
            /* current property prefix is not allowed */
            false
        } else {
            /* no allowed prefixes configured so key is valid by default */
            true
        }
    }

    /**
     * Process a new property. If value equals "${null}", it means to remove the property in the
     * configuration service. If the key name end with "PASSWORD", its value is encrypted through
     * credentials storage service, otherwise the property is added/updated in the configuration service.
     *
     * @param key property key name
     * @param value property value
     */
    private fun processProperty(key: String, value: Any) {
        if (value is String && value == "\${null}") {
            ProvisioningActivator.configurationService!!.removeProperty(key)
        } else if (key.endsWith(".PASSWORD")) {
            /* password => credentials storage service */
            ProvisioningActivator.credentialsStorageService!!
                    .storePassword(key.substring(0, key.lastIndexOf(".")), value as String)
            Timber.i("%s = <password hidden>", key)
            return
        } else if (key.startsWith(SYSTEM_PROP_PREFIX)) {
            val sysKey = key.substring(SYSTEM_PROP_PREFIX.length, key.length)
            System.setProperty(sysKey, value as String)
        } else {
            ProvisioningActivator.configurationService!!.setProperty(key, value)
        }
        Timber.i("%s = %s", key, value)
    }

    /**
     * Walk through all properties and make sure all properties keys match a specific set of
     * prefixes defined in configuration.
     *
     * @param enforcePrefix list of enforce prefix.
     */
    private fun checkEnforcePrefix(enforcePrefix: String?) {
        val config = ProvisioningActivator.configurationService
        if (enforcePrefix == null) {
            return
        }
        /* must escape the | character */
        val prefixes = enforcePrefix.split("\\|".toRegex()).toTypedArray()

        /* get all properties */
        for (key in config!!.getAllPropertyNames(enforcePrefix)!!) {
            var isValid = false
            for (k in prefixes) {
                if (key!!.startsWith(k)) {
                    isValid = true
                    break
                }
            }
            /*
             * property name does is not in the enforce prefix list so remove it
             */
            if (!isValid) {
                config.removeProperty(key!!)
            }
        }
    }

    companion object {
        /**
         * Name of the UUID property.
         */
        const val PROVISIONING_UUID_PROP = "net.java.sip.communicator.UUID"

        /**
         * Name of the provisioning URL in the configuration service.
         */
        private const val PROPERTY_PROVISIONING_URL = "provisioning.URL"

        /**
         * Name of the provisioning username in the configuration service authentication).
         */
        const val PROPERTY_PROVISIONING_USERNAME = "provisioning.auth.USERNAME"

        /**
         * Name of the provisioning password in the configuration service (HTTP authentication).
         */
        const val PROPERTY_PROVISIONING_PASSWORD = "provisioning.auth"

        /**
         * Name of the property that contains the provisioning method (i.e. DHCP, DNS, manual, ...).
         */
        private const val PROVISIONING_METHOD_PROP = "provisioning.METHOD"

        /**
         * Name of the property, whether provisioning is mandatory.
         */
        private const val PROPERTY_PROVISIONING_MANDATORY = "provisioning.MANDATORY"

        /**
         * Name of the property that contains enforce prefix list (separated by pipe) for the provisioning.
         * The retrieved configuration properties will be checked against these prefixes to avoid having
         * incorrect content in the configuration file (such as HTML content resulting of HTTP error).
         */
        private const val PROVISIONING_ALLOW_PREFIX_PROP = "provisioning.ALLOW_PREFIX"

        /**
         * Name of the enforce prefix property.
         */
        private const val PROVISIONING_ENFORCE_PREFIX_PROP = "provisioning.ENFORCE_PREFIX"

        /**
         * Prefix that can be used to indicate a property that will be set as a system property.
         */
        private const val SYSTEM_PROP_PREFIX = "\${system}."

        /**
         * Search param value for the supplied name.
         *
         * @param jsonObject the JSONOBject can be null.
         * @param paramName the name to search.
         * @return the corresponding parameter value.
         */
        private fun getParamValue(jsonObject: JSONObject?, paramName: String?): String? {
            if (jsonObject == null || paramName == null) return null
            try {
                return jsonObject.get(paramName).toString()
            } catch (e: JSONException) {
                Timber.e("JSONObject exception: %s", e.message)
            }
            return null
        }
    }
}