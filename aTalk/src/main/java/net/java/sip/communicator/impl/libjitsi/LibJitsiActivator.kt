package net.java.sip.communicator.impl.libjitsi

import org.atalk.service.libjitsi.LibJitsi
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class LibJitsiActivator : BundleActivator {
    @Throws(Exception::class)
    override fun start(bundleContext: BundleContext) {
        var start: Method?
        try {
            start = LibJitsi::class.java.getDeclaredMethod("start", Any::class.java)
            if (Modifier.isStatic(start.modifiers)) {
                start.isAccessible = true
                if (!start.isAccessible) {
                    start = null
                }
            } else {
                start = null
            }
        } catch (ex: NoSuchMethodException) {
            start = null
        } catch (ex: SecurityException) {
            start = null
        }
        if (start == null) {
            LibJitsi.start()
        } else {
            start.invoke(null, bundleContext)
        }
    }

    @Throws(Exception::class)
    override fun stop(bundleContext: BundleContext) {
    }
}