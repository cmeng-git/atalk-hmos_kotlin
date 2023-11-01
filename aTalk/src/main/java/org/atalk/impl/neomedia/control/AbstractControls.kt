/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control

import timber.log.Timber
import javax.media.Controls

/**
 * Provides an abstract implementation of `Controls` which facilitates implementers by
 * requiring them to only implement [Controls.getControls].
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
abstract class AbstractControls : Controls {
    /**
     * Implements [Controls.getControl]. Invokes [.getControls] and then
     * looks for a control of the specified type in the returned array of controls.
     *
     * @param controlType
     * a `String` value naming the type of the control of this instance to be
     * retrieved
     * @return an `Object` which represents the control of this instance with the specified
     * type
     */
    override fun getControl(controlType: String): Any? {
        return getControl(this, controlType)
    }

    companion object {
        /**
         * Gets the control of a specific `Controls` implementation of a specific type if such a
         * control is made available through [Controls.getControls]; otherwise, returns
         * `null`.
         *
         * @param controlsImpl
         * the implementation of `Controls` which is to be queried for its list of
         * controls so that the control of the specified type can be looked for
         * @param controlType
         * a `String` value which names the type of the control to be retrieved
         * @return an `Object` which represents the control of `controlsImpl` of the
         * specified `controlType` if such a control is made available through
         * `Controls#getControls()`; otherwise, `null`
         */
        fun getControl(controlsImpl: Controls, controlType: String?): Any? {
            val controls = controlsImpl.controls
            if (controls != null && controls.isNotEmpty()) {
                var controlClass: Class<*>?
                try {
                    controlClass = Class.forName(controlType)
                } catch (cnfe: ClassNotFoundException) {
                    controlClass = null
                    Timber.w(cnfe, "Failed to find control class %s", controlType)
                }
                if (controlClass != null) {
                    for (control in controls) {
                        if (controlClass.isInstance(control)) return control
                    }
                }
            }
            return null
        }

        /**
         * Returns an instance of a specific `Class` which is either a control of a specific
         * `Controls` implementation or the `Controls` implementation itself if it is an
         * instance of the specified `Class`. The method is similar to
         * [.getControl] in querying the specified `Controls`
         * implementation about a control of the specified `Class` but is different in
         * looking at the type hierarchy of the `Controls` implementation for the specified
         * `Class`.
         *
         * @param controlsImpl
         * the `Controls` implementation to query
         * @param controlType
         * the runtime type of the instance to be returned
         * @return an instance of the specified `controlType` if such an instance can be found
         * among the controls of the specified `controlsImpl` or `controlsImpl` is
         * an instance of the specified `controlType`; otherwise, `null`
         */
        fun <T> queryInterface(controlsImpl: Controls?, controlType: Class<T>): T? {
            var control: T?
            if (controlsImpl == null) {
                control = null
            } else {
                control = controlsImpl.getControl(controlType.name) as T
                if (control == null && controlType.isInstance(controlsImpl)) control = controlsImpl as T
            }
            return control
        }

        /**
         * Returns an instance of a specific `Class` which is either a control of a specific
         * `Controls` implementation or the `Controls` implementation itself if it is an
         * instance of the specified `Class`. The method is similar to
         * [.getControl] in querying the specified `Controls`
         * implementation about a control of the specified `Class` but is different in looking at
         * the type hierarchy of the `Controls` implementation for the specified `Class`.
         *
         * @param controlsImpl
         * the `Controls` implementation to query
         * @param controlType
         * the runtime type of the instance to be returned
         * @return an instance of the specified `controlType` if such an instance can be found
         * among the controls of the specified `controlsImpl` or `controlsImpl` is
         * an instance of the specified `controlType`; otherwise, `null`
         */
        fun queryInterface(controlsImpl: Controls?, controlType: String?): Any? {
            var control: Any?
            if (controlsImpl == null) {
                control = null
            } else {
                control = controlsImpl.getControl(controlType)
                if (control == null) {
                    var controlClass: Class<*>?
                    try {
                        controlClass = Class.forName(controlType)
                    } catch (cnfe: ClassNotFoundException) {
                        controlClass = null
                        Timber.w(cnfe, "Failed to find control class %s", controlType)
                    }
                    if (controlClass != null && controlClass.isInstance(controlsImpl)) {
                        control = controlsImpl
                    }
                }
            }
            return control
        }
    }
}