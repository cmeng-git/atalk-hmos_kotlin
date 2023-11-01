/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework

import org.osgi.framework.Bundle
import org.osgi.framework.Constants
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import java.util.*

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class ServiceRegistrationImpl(
        private val bundle: BundleImpl,
        private val serviceId: Long,
        private val classNames: Array<String>,
        private val service: Any,
        properties: Dictionary<String, *>?,
) : ServiceRegistration<Any> {
    private val mProperties: Map<String, Any>
    private val serviceReference = ServiceReferenceImpl()

    init {
        if (properties == null || properties.isEmpty) {
            mProperties = EMPTY_PROPERTIES
        }
        else {
            val keys = properties.keys()
            val thisProperties = newCaseInsensitiveMapInstance()

            while (keys.hasMoreElements()) {
                val key = keys.nextElement()

                when {
                    Constants.OBJECTCLASS.equals(key, ignoreCase = true) || Constants.SERVICE_ID.equals(key, ignoreCase = true) -> continue
                    else -> require(!thisProperties.containsKey(key)) { key }
                }
                thisProperties[key] = properties[key]
            }
            mProperties = if (thisProperties.isEmpty()) EMPTY_PROPERTIES else thisProperties
        }
    }

//    override fun getReference(): ServiceReference<Any> {
//        return serviceReference
//    }

    override fun getReference(): ServiceReferenceImpl {
        return serviceReference
    }

    fun getReference(clazz: Class<*>): ServiceReference<*> {
        return serviceReference
    }

    override fun setProperties(properties: Dictionary<String, *>?) {
        TODO("Not yet implemented")
    }

    override fun unregister() {
        bundle.framework!!.unregisterService(bundle, this)
    }

    inner class ServiceReferenceImpl : ServiceReference<Any> {
        override fun compareTo(other: Any): Int {
            val otherServiceId = (other as ServiceRegistrationImpl).serviceId
            return otherServiceId.compareTo(serviceId)
        }

        override fun getBundle(): Bundle {
            return this@ServiceRegistrationImpl.bundle
        }

        override fun getProperty(key: String): Any {
            var value: Any
            when {
                Constants.OBJECTCLASS.equals(key, ignoreCase = true) -> value = classNames
                Constants.SERVICE_ID.equals(key, ignoreCase = true) -> value = serviceId
                else -> synchronized(mProperties) {
                    value = mProperties[key]!!
                }
            }
            return value
        }

        override fun getPropertyKeys(): Array<String> {
            synchronized(properties!!) {
                val keys = Array(2 + properties!!.size()) { "" }
                var index = 0
                keys[index++] = Constants.OBJECTCLASS
                keys[index++] = Constants.SERVICE_ID
                for (key in properties!!.keys()) keys[index++] = key
                return keys
            }
        }

        fun getService(): Any {
            return service
        }

        override fun getUsingBundles(): Array<Bundle>? {
            return null
        }

        override fun isAssignableTo(bundle: Bundle, className: String): Boolean {
            return false
        }

        override fun getProperties(): Dictionary<String, Any>? {
            return null
        }

        override fun <A : Any?> adapt(type: Class<A>?): A? {
            return null
        }
    }

    companion object {
        private val CASE_INSENSITIVE_COMPARATOR = Comparator { s1: String, s2: String? -> s1.compareTo(s2!!, ignoreCase = true) }
        private val EMPTY_PROPERTIES: Map<String, Any> = newCaseInsensitiveMapInstance()

        private fun newCaseInsensitiveMapInstance(): MutableMap<String, Any> {
            return TreeMap(CASE_INSENSITIVE_COMPARATOR)
        }
    }
}