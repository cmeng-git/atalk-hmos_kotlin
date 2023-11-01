/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi.framework

import org.atalk.impl.osgi.framework.ServiceRegistrationImpl.ServiceReferenceImpl
import org.atalk.impl.osgi.framework.launch.FrameworkImpl
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.BundleListener
import org.osgi.framework.Filter
import org.osgi.framework.FrameworkListener
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.InvalidSyntaxException
import org.osgi.framework.ServiceFactory
import org.osgi.framework.ServiceListener
import org.osgi.framework.ServiceObjects
import org.osgi.framework.ServiceReference
import org.osgi.framework.ServiceRegistration
import java.io.File
import java.io.InputStream
import java.util.*

/**
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class BundleContextImpl(
        private val framework: FrameworkImpl,
        private val bundle: BundleImpl) : BundleContext {
    override fun addBundleListener(listener: BundleListener) {
        framework.addBundleListener(getBundle(), listener)
    }

    override fun addFrameworkListener(listener: FrameworkListener) {
        // TODO Auto-generated method stub
    }

    override fun addServiceListener(listener: ServiceListener) {
        try {
            addServiceListener(listener, null)
        } catch (ise: InvalidSyntaxException) {
            // Since filter is null, there should be no InvalidSyntaxException.
        }
    }

    @Throws(InvalidSyntaxException::class)
    override fun addServiceListener(listener: ServiceListener, filter: String?) {
        framework.addServiceListener(getBundle(), listener, if (filter == null) null else createFilter(filter))
    }

    @Throws(InvalidSyntaxException::class)
    override fun createFilter(filter: String): Filter {
        return FrameworkUtil.createFilter(filter)
    }

    @Throws(InvalidSyntaxException::class)
    override fun getAllServiceReferences(className: String, filter: String): Array<ServiceReference<*>> {
        return getServiceReferences(className, filter, false)
    }

    override fun getBundle(): BundleImpl {
        return bundle
    }

    override fun getBundle(id: Long): Bundle {
        return framework.getBundle(id)!!
    }

    override fun getBundle(location: String): Bundle? {
        return null
    }

    override fun getBundles(): Array<Bundle>? {
        return null
    }

    override fun getDataFile(filename: String): File? {
        return null
    }

    override fun getProperty(key: String): String? {
        return null
    }

    // override fun getService(reference: ServiceReference<*>): Any {
    override fun <S : Any> getService(reference: ServiceReference<S>): S {
        return (reference as ServiceReferenceImpl).getService() as S
    }

    // override fun getServiceReference(clazz: Class<*>): ServiceReference<*>? {
    override fun <S : Any> getServiceReference(clazz: Class<S>): ServiceReference<S> {
        return getServiceReference(clazz, clazz.name) as ServiceReference<S>
    }


    private fun getServiceReference(clazz: Class<*>, className: String): ServiceReference<*>? {
        val serviceReferences = try {
            getServiceReferences(className, null)
        } catch (ise: InvalidSyntaxException) {
            // No InvalidSyntaxException is expected because the filter is null.
            null
        }
        return if (serviceReferences == null || serviceReferences.isEmpty()) null else serviceReferences[0]
    }

    override fun getServiceReference(className: String): ServiceReference<*>? {
        return getServiceReference(Any::class.java, className)
    }

    @Throws(InvalidSyntaxException::class)
    // override fun getServiceReferences(clazz: Class<*>, filter: String?): Collection<ServiceReference<*>> {
    override fun <S: Any> getServiceReferences(clazz: Class<S>, filter: String?): Collection<ServiceReference<S>> {

        return getServiceReferences(clazz, clazz.name, filter, true) as Collection<ServiceReference<S>>
    }

    @Throws(InvalidSyntaxException::class)
    private fun getServiceReferences(clazz: Class<*>, className: String, filter: String?, checkAssignable: Boolean): Collection<ServiceReference<*>> {
        return framework.getServiceReferences(getBundle(), clazz, className, filter?.let { createFilter(it) }, checkAssignable)
    }

    @Throws(InvalidSyntaxException::class)
    override fun getServiceReferences(className: String, filter: String?): Array<ServiceReference<*>> {
        return getServiceReferences(className, filter, true)
    }

    @Throws(InvalidSyntaxException::class)
    private fun getServiceReferences(className: String, filter: String?, checkAssignable: Boolean): Array<ServiceReference<*>> {
        val serviceReferences = getServiceReferences(Any::class.java, className, filter, checkAssignable)
        return serviceReferences.toTypedArray()
    }

    @Throws(BundleException::class)
    override fun installBundle(location: String): Bundle {
        return installBundle(location, null)
    }

    @Throws(BundleException::class)
    override fun installBundle(location: String, input: InputStream?): Bundle {
        return framework.installBundle(getBundle(), location, input)!!
    }

    override fun <S> registerService(clazz: Class<S>, service: S, properties: Dictionary<String, *>?): ServiceRegistration<S> {
        return registerService(clazz, arrayOf(clazz.name), service, properties) as ServiceRegistration<S>
    }

    override fun <S> registerService(clazz: Class<S>, factory: ServiceFactory<S>, properties: Dictionary<String, *>?): ServiceRegistration<S>? {
        return null
    }

    private fun <S> registerService(clazz: Class<S>, classNames: Array<String>, service: S, properties: Dictionary<String, *>?): ServiceRegistration<*> {
        return framework.registerService(getBundle(), clazz, classNames, service as Any, properties)
    }

    override fun registerService(className: String, service: Any, properties: Dictionary<String, *>?): ServiceRegistration<*> {
        return registerService(arrayOf(className), service, properties)
    }

    override fun registerService(classNames: Array<String>, service: Any, properties: Dictionary<String, *>?): ServiceRegistration<*> {
        return registerService(Any::class.java, classNames, service, properties)
    }

    override fun removeBundleListener(listener: BundleListener) {
        framework.removeBundleListener(getBundle(), listener)
    }

    override fun removeFrameworkListener(listener: FrameworkListener) {
        // TODO Auto-generated method stub
    }

    override fun removeServiceListener(listener: ServiceListener) {
        framework.removeServiceListener(getBundle(), listener)
    }

    override fun ungetService(reference: ServiceReference<*>?): Boolean {
        return false
    }

    override fun <S> getServiceObjects(reference: ServiceReference<S>): ServiceObjects<S>? {
        return null
    }
}