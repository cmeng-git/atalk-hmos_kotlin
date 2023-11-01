/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import okhttp3.internal.notifyAll
import timber.log.Timber
import java.awt.Dimension

/**
 * `ViewDependentProvider` is used to implement classes that provide objects dependent on
 * `View` visibility state. It means that they can provide it only when `View` is
 * visible, and they have to release the object before `View` is hidden.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
abstract class ViewDependentProvider<T>
/**
 * Create a new instance of `ViewDependentProvider`.
 *
 * activity parent `Activity` that manages the `container`.
 * container the container that will hold maintained `View`.
 */
(
        /**
         * `Activity` context.
         */
        protected val mActivity: Activity,
        /**
         * The container that will hold maintained view.
         */
        private val mContainer: ViewGroup) {
    /**
     * Returns maintained `View` object.
     *
     * @return maintained `View` object.
     */
    /**
     * The view (can either be SurfaceView or TextureView) maintained by this instance.
     */
    var view: View? = null
        protected set
    /**
     * Set the [.mVideoSize] with the video size selected by user for this instance
     *
     * videoSize user selected video size independent of the device orientation
     */
    /**
     * Use for surfaceCreation to set surface holder size for correct camera local preview aspect ratio
     * This size is the user selected video resolution independent of the device orientation
     */
    lateinit var videoSize: Dimension

    /**
     * Provided object created when `View` is visible.
     */
    protected var providedObject: T? = null

    /**
     * Factory method that creates new `View` instance.
     *
     * @return new `View` instance.
     */
    protected abstract fun createViewInstance(): View
    abstract fun setAspectRatio(width: Int, height: Int)

    /**
     * Checks if the view is currently created. If not creates new `View` and adds it to the
     * `container`.
     */
    private fun ensureViewCreated() {
        if (view == null) {
            mActivity.runOnUiThread {
                view = createViewInstance()
                val params = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                mContainer.addView(view, params)
                mContainer.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Checks if maintained view exists and removes if from the `container`.
     */
    private fun ensureViewDestroyed() {
        if (view != null) {
            val viewToRemove = view
            view = null
            mActivity.runOnUiThread {
                mContainer.removeView(viewToRemove)
                mContainer.visibility = View.GONE
            }
        }
    }

    /**
     * Must be called by subclasses when provided object is created.
     *
     * @param obj provided object instance.
     */
    @Synchronized
    protected fun onObjectCreated(obj: T) {
        providedObject = obj
        this.notifyAll()
    }

    /**
     * Should be called by consumer to obtain the object. It is causing hidden `View` to be
     * displayed and eventually [.onObjectCreated] method to be called which results
     * in object creation.
     *
     * @return provided object.
     */
    @Synchronized
    open fun obtainObject(): T {
        ensureViewCreated()
        if (providedObject == null) {
            try {
                Timber.i("Waiting for object...%s", hashCode())
                (this as Object).wait(CREATE_TIMEOUT)
                if (providedObject == null) {
                    throw RuntimeException("Timeout waiting for surface")
                }
                Timber.i("Returning object! %s", hashCode())
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        return providedObject!!
    }

    /**
     * Checks if provider has already the object and returns it immediately. If there is no object,
     * and we would have to wait for it, then the `null` is returned.
     *
     * @return the object if it is currently held by this provider or `null` otherwise.
     */
    @Synchronized
    fun tryObtainObject(): T? {
        return providedObject
    }

    /**
     * Should be called by subclasses when object is destroyed.
     */
    @Synchronized
    protected fun onObjectDestroyed() {
        releaseObject()
    }

    /**
     * Should be called by the consumer to release the object.
     */
    open fun onObjectReleased() {
        releaseObject()
        // Remove the view once it's released
        ensureViewDestroyed()
    }

    /**
     * Releases the subject object and notifies all threads waiting on the lock.
     */
    @Synchronized
    protected fun releaseObject() {
        if (providedObject != null) {
            providedObject = null
            this.notifyAll()
        }
    }

    /**
     * Blocks the current thread until subject object is released. It should be used to block UI thread
     * before the `View` is hidden.
     */
    @Synchronized
    fun waitForObjectRelease() {
        if (providedObject != null) {
            try {
                Timber.i("Waiting for object release... %s", hashCode())
                (this as Object).wait(REMOVAL_TIMEOUT)
                if (providedObject != null) {
                    // cmeng - do not throw, as this hangs the video call screen
                    // throw new RuntimeException("Timeout waiting for preview surface removal");
                    Timber.w("Timeout waiting for preview surface removal!")
                }
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
        ensureViewDestroyed()
    }

    companion object {
        /**
         * Timeout for dispose surface operation
         */
        private const val REMOVAL_TIMEOUT = 5000L

        /**
         * Timeout for create surface operation
         */
        private const val CREATE_TIMEOUT = 5000L
    }
}