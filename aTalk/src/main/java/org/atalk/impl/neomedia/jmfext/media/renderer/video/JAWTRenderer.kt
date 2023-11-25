/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.video

import org.atalk.hmos.plugin.timberlog.TimberLog
import org.atalk.impl.neomedia.codec.video.SwScale
import org.atalk.impl.neomedia.jmfext.media.renderer.AbstractRenderer
import org.atalk.util.OSUtils
import org.atalk.util.swing.VideoLayout
import timber.log.Timber
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.lang.reflect.InvocationTargetException
import javax.media.*
import javax.media.PlugIn.BUFFER_PROCESSED_FAILED
import javax.media.PlugIn.BUFFER_PROCESSED_OK
import javax.media.format.RGBFormat
import javax.media.format.VideoFormat
import javax.media.format.YUVFormat
import javax.media.renderer.VideoRenderer
import javax.swing.SwingUtilities

/**
 * Implements a `VideoRenderer` which uses JAWT to perform native painting in an AWT or Swing `Component`.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class JAWTRenderer
/**
 * Initializes a new `JAWTRenderer` instance.
 */
    : AbstractRenderer<VideoFormat>(), VideoRenderer {
    /**
     * The AWT `Component` into which this `VideoRenderer` draws.
     */
    private var component: Component? = null
    /**
     * Gets the handle to the native counterpart of this `JAWTRenderer`.
     *
     * @return the handle to the native counterpart of this `JAWTRenderer`
     */
    /**
     * The handle to the native counterpart of this `JAWTRenderer`.
     */
    var handle: Long = 0
        private set

    /**
     * The last known height of the input processed by this `JAWTRenderer`.
     */
    private var height = 0

    /**
     * The `Runnable` which is executed to bring the invocations of
     * [.reflectInputFormatOnComponent] into the AWT event dispatching thread.
     */
    private val reflectInputFormatOnComponentInEventDispatchThread = Runnable { reflectInputFormatOnComponentInEventDispatchThread() }

    /**
     * The last known width of the input processed by this `JAWTRenderer`.
     */
    private var width = 0

    /**
     * Closes this `PlugIn` and releases the resources it has retained during its execution.
     * No more data will be accepted by this `PlugIn` afterwards. A closed `PlugIn`
     * can be reinstated by calling `open` again.
     */
    @Synchronized
    override fun close() {
        if (handle != 0L) {
            close(handle, component)
            handle = 0
        }
    }

    /**
     * Gets the region in the component of this `VideoRenderer` where the video is rendered.
     * `JAWTRenderer` always uses the entire component i.e. always returns `null`.
     *
     * @return the region in the component of this `VideoRenderer` where the video is
     * rendered; `null` if the entire component is used
     */
    override fun getBounds(): Rectangle? {
        return null
    }

    /**
     * Gets the AWT `Component` into which this `VideoRenderer` draws.
     *
     * @return the AWT `Component` into which this `VideoRenderer` draws
     */
    @Synchronized
    override fun getComponent(): Component {
        if (component == null) {
            val componentClassName = StringBuilder()
            componentClassName.append(
                "org.atalk.impl.neomedia.jmfext.media.renderer.video.JAWTRenderer")
            if (OSUtils.IS_ANDROID) componentClassName.append("Android")
            componentClassName.append("VideoComponent")
            try {
                val componentClass = Class.forName(componentClassName.toString())
                val componentConstructor = componentClass.getConstructor(JAWTRenderer::class.java)
                component = componentConstructor.newInstance(this) as Component
            } catch (cnfe: Exception) {
                when (cnfe) {
                    is ClassNotFoundException,
                    is NoSuchMethodException,
                    is InvocationTargetException,
                    is InstantiationException,
                    is IllegalAccessException,
                    -> {
                        throw RuntimeException(cnfe)
                    }
                }
            }

            // Make sure to have non-zero height and width because actual video
            // frames may have not been processed yet.
            component!!.setSize(
                DEFAULT_COMPONENT_HEIGHT_OR_WIDTH,
                DEFAULT_COMPONENT_HEIGHT_OR_WIDTH)
            // XXX The component has not been exposed outside of this instance
            // yet so it seems relatively safe to set its properties outside the
            // AWT event dispatching thread.
            reflectInputFormatOnComponentInEventDispatchThread()
        }
        return component!!
    }

    /**
     * Gets the `Object` which synchronizes the access to the handle to the native
     * counterpart of this `JAWTRenderer`.
     *
     * @return the `Object` which synchronizes the access to the handle to the native
     * counterpart of this `JAWTRenderer`
     */
    val handleLock: Any
        get() = this

    /**
     * Gets the human-readable name of this `PlugIn`.
     *
     * @return the human-readable name of this `PlugIn`
     */
    override fun getName(): String {
        return PLUGIN_NAME
    }

    /**
     * Gets the list of input `Format`s supported by this `Renderer`.
     *
     * @return an array of `Format` elements which represent the input `Format`s
     * supported by this `Renderer`
     */
    override fun getSupportedInputFormats(): Array<Format> {
        return SUPPORTED_INPUT_FORMATS.clone()
    }

    /**
     * Opens this `PlugIn` and acquires the resources that it needs to operate. The input
     * format of this `Renderer` has to be set before `open` is called. Buffers
     * should not be passed into this `PlugIn` without first calling `open`.
     *
     * @throws ResourceUnavailableException if there is a problem during opening
     */
    @Throws(ResourceUnavailableException::class)
    override fun open() {
        var addNotify: Boolean
        val component: Component?
        synchronized(this) {
            if (handle == 0L) {
                // If this JAWTRenderer gets opened after its visual/video
                // Component has been created, send addNotify to the Component
                // once this JAWTRenderer gets opened so that the Component may
                // use the handle if it needs to.
                addNotify = this.component != null && this.component!!.parent != null
                component = getComponent()
                handle = open(component)
                if (handle == 0L) {
                    throw ResourceUnavailableException("Failed to open the native JAWTRenderer.")
                }
            }
            else {
                addNotify = false
                component = null
            }
        }
        // The #addNotify() invocation, if any, should happen outside the synchronized block in order to avoid a deadlock.
        if (addNotify) {
            SwingUtilities.invokeLater { component!!.addNotify() }
        }
    }

    /**
     * Processes the data provided in a specific `Buffer` and renders it to the output
     * device represented by this `Renderer`.
     *
     * @param buffer a `Buffer` containing the data to be processed and rendered
     * @return `BUFFER_PROCESSED_OK` if the processing is successful; otherwise, the other
     * possible return codes defined in the `PlugIn` interface
     */
    @Synchronized
    override fun process(buffer: Buffer): Int {
        if (buffer.isDiscard) return BUFFER_PROCESSED_OK

        val bufLen = buffer.length
        if (bufLen == 0) return BUFFER_PROCESSED_OK

        val format = buffer.format
        if (format != null
                && format != inputFormat
                && format != inputFormat
                && setInputFormat(format) == null) {
            return BUFFER_PROCESSED_FAILED
        }

        return if (handle == 0L) BUFFER_PROCESSED_FAILED
        else {
            var size: Dimension? = null
            if (format != null) size = (format as VideoFormat).size
            if (size == null) {
                size = inputFormat!!.size
                if (size == null) return BUFFER_PROCESSED_FAILED
            }

            // XXX If the size of the video frame to be displayed is tiny enough
            // to crash sws_scale, then it may cause issues with other
            // functionality as well. Stay on the safe side.
            if (size.width >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH && size.height >= SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH) {
                val component = getComponent()
                val repaint = process(handle, component, buffer.data as IntArray,
                    buffer.offset, bufLen, size.width, size.height)
                if (repaint) component.repaint()
            }
            BUFFER_PROCESSED_OK
        }
    }

    /**
     * Sets properties of the AWT `Component` of this `Renderer` which depend on the
     * properties of the `inputFormat` of this `Renderer`. Makes sure that the
     * procedure is executed on the AWT event dispatching thread because an AWT `Component`'s
     * properties (such as `preferredSize`) should be accessed in the AWT event dispatching thread.
     */
    private fun reflectInputFormatOnComponent() {
        if (SwingUtilities.isEventDispatchThread()) {
            reflectInputFormatOnComponentInEventDispatchThread()
        }
        else {
            SwingUtilities.invokeLater(reflectInputFormatOnComponentInEventDispatchThread)
        }
    }

    /**
     * Sets properties of the AWT `Component` of this `Renderer` which depend on the
     * properties of the `inputFormat` of this `Renderer`. The invocation is presumed
     * to be performed on the AWT event dispatching thread.
     */
    private fun reflectInputFormatOnComponentInEventDispatchThread() {
        // Reflect the width and height of the input onto the prefSize of our
        // AWT Component (if necessary).
        if (component != null && width > 0 && height > 0) {
            var prefSize = component!!.preferredSize

            // Apart from the simplest of cases in which the component has no
            // prefSize, it is also necessary to reflect the width and height of
            // the input onto the prefSize when the ratio of the input is
            // different than the ratio of the prefSize. It may also be argued
            // that the component needs to know of the width and height of the
            // input if its prefSize is with the same ratio but is smaller.
            if ((prefSize == null || prefSize.width < 1 || prefSize.height < 1
                            || !VideoLayout.areAspectRatiosEqual(prefSize, width, height)) || prefSize.width < width || prefSize.height < height) {
                component!!.preferredSize = Dimension(width, height)
            }

            // If the component does not have a size, it looks strange given
            // that we know a prefSize for it. However, if the component has
            // already been added into a Container, the Container will dictate
            // the size as part of its layout logic.
            if (component!!.isPreferredSizeSet && component!!.parent == null) {
                val size = component!!.size
                prefSize = component!!.preferredSize
                if (size.width < 1 || size.height < 1
                        || !VideoLayout.areAspectRatiosEqual(size, prefSize.width,
                            prefSize.height)) {
                    component!!.setSize(prefSize.width, prefSize.height)
                }
            }
        }
    }

    /**
     * Sets the region in the component of this `VideoRenderer` where the video is to be
     * rendered. `JAWTRenderer` always uses the entire component and, consequently, the
     * method does nothing.
     *
     * @param bounds the region in the component of this `VideoRenderer` where the video is to be
     * rendered; `null` if the entire component is to be used
     */
    override fun setBounds(bounds: Rectangle) {}

    /**
     * Sets the AWT `Component` into which this `VideoRenderer` is to draw.
     * `JAWTRenderer` cannot draw into any other AWT `Component` but its own so it
     * always returns `false`.
     *
     * @param component the AWT `Component` into which this `VideoRenderer` is to draw
     * @return `true` if this `VideoRenderer` accepted the specified
     * `component` as the AWT `Component` into which it is to draw; `false`, otherwise
     */
    override fun setComponent(component: Component): Boolean {
        return false
    }

    /**
     * Sets the `Format` of the input to be processed by this `Renderer`.
     *
     * @param format the `Format` to be set as the `Format` of the input to be processed by
     * this `Renderer`
     * @return the `Format` of the input to be processed by this `Renderer` if the
     * specified `format` is supported or `null` if the specified
     * `format` is not supported by this `Renderer`. Typically, it is the
     * supported input `Format` which most closely matches the specified `Format`.
     */
    @Synchronized
    override fun setInputFormat(format: Format): Format? {
        val oldInputFormat = inputFormat
        val newInputFormat = super.setInputFormat(format)

        // Short-circuit because we will be calculating a lot and we do not want
        // to do that unless necessary.
        if (oldInputFormat == inputFormat)
            return newInputFormat

        Timber.log(TimberLog.FINER, "%s %08x set to input in %s", javaClass.name, hashCode(), inputFormat)

        // Know the width and height of the input because we'll be depicting it
        // and we may want, for example, to report them as the preferred size of
        // our AWT Component. More importantly, know them because they determine
        // certain arguments to be passed to the native counterpart of this
        // JAWTRenderer i.e. handle.
        val size = inputFormat!!.size
        if (size == null) {
            height = 0
            width = 0
        }
        else {
            width = size.width
            height = size.height
        }

        reflectInputFormatOnComponent()
        return newInputFormat!!
    }

    /**
     * Starts the rendering process. Begins rendering any data available in the internal buffers of this `Renderer`.
     */
    override fun start() {}

    /**
     * Stops the rendering process.
     */
    override fun stop() {}

    companion object {
        /**
         * The default, initial height and width to set on the `Component`s of
         * `JAWTRenderer`s before video frames with actual sizes are processed. Introduced to
         * mitigate multiple failures to realize the actual video frame size and/or to properly scale
         * the visual/video `Component`s.
         */
        private const val DEFAULT_COMPONENT_HEIGHT_OR_WIDTH = 16

        /**
         * The human-readable `PlugIn` name of the `JAWTRenderer` instances.
         */
        private const val PLUGIN_NAME = "JAWT Renderer"

        /**
         * The array of supported input formats.
         */
        private val SUPPORTED_INPUT_FORMATS = arrayOf<Format>(
            if (OSUtils.IS_LINUX) YUVFormat(
                null /* size */,
                Format.NOT_SPECIFIED /* maxDataLength */,
                Format.intArray,
                Format.NOT_SPECIFIED /* frameRate */.toFloat(),
                YUVFormat.YUV_420,
                Format.NOT_SPECIFIED /* strideY */,
                Format.NOT_SPECIFIED /* strideUV */,
                Format.NOT_SPECIFIED /* offsetY */,
                Format.NOT_SPECIFIED /* offsetU */,
                Format.NOT_SPECIFIED /* offsetV */)
            else if (OSUtils.IS_ANDROID) RGBFormat(
                null,
                Format.NOT_SPECIFIED,
                Format.intArray,
                Format.NOT_SPECIFIED.toFloat(),
                32,
                0x000000ff, 0x0000ff00, 0x00ff0000)
            else RGBFormat(
                null,
                Format.NOT_SPECIFIED,
                Format.intArray,
                Format.NOT_SPECIFIED.toFloat(),
                32,
                0x00ff0000, 0x0000ff00, 0x000000ff)
        )

        init {
            System.loadLibrary("jnawtrenderer")
        }

        external fun addNotify(handle: Long, component: Component?)

        /**
         * Closes the native counterpart of a `JAWTRenderer` specified by its handle as returned
         * by [.open] and rendering into a specific AWT `Component`. Releases
         * the resources which the specified native counterpart has retained during its execution and
         * its handle is considered to be invalid afterwards.
         *
         * @param handle the handle to the native counterpart of a `JAWTRenderer` as returned by
         * [.open] which is to be closed
         * @param component the AWT `Component` into which the `JAWTRenderer` and its native
         * counterpart are drawing. The platform-specific info of `component` is not guaranteed to be valid.
         */
        private external fun close(handle: Long, component: Component?)

        /**
         * Opens a handle to a native counterpart of a `JAWTRenderer` which is to draw into a
         * specific AWT `Component`.
         *
         * @param component the AWT `Component` into which a `JAWTRenderer` and the native
         * counterpart to be opened are to draw. The platform-specific info of `component`
         * is not guaranteed to be valid.
         * @return a handle to a native counterpart of a `JAWTRenderer` which is to draw into
         * the specified AWT `Component`
         * @throws ResourceUnavailableException if there is a problem during opening
         */
        @Throws(ResourceUnavailableException::class)
        private external fun open(component: Component?): Long

        /**
         * Paints a specific `Component` which is the AWT `Component` of a
         * `JAWTRenderer` specified by the handle to its native counterpart.
         *
         * @param handle the handle to the native counterpart of a `JAWTRenderer` which is to draw into
         * the specified AWT `Component`
         * @param component the AWT `Component` into which the `JAWTRenderer` and its native
         * counterpart specified by `handle` are to draw. The platform-specific info of
         * `component` is guaranteed to be valid only during the execution of `paint`.
         * @param g the `Graphics` context into which the drawing is to be performed
         * @param zOrder
         * @return `true` if the native counterpart of a `JAWTRenderer` wants to continue
         * receiving the `paint` calls on the AWT `Component`; otherwise, false.
         * For example, after the native counterpart has been able to acquire the native handle
         * of the AWT `Component`, it may be able to determine when the native handle
         * needs painting without waiting for AWT to call `paint` on the
         * `Component`. In such a scenario, the native counterpart may indicate with
         * `false` that it does not need further `paint` deliveries.
         */
        external fun paint(handle: Long, component: Component?, g: Graphics?, zOrder: Int): Boolean

        /**
         * Processes the data provided in a specific `int` array with a specific offset and
         * length and renders it to the output device represented by a `JAWTRenderer` specified
         * by the handle to it native counterpart.
         *
         * @param handle the handle to the native counterpart of a `JAWTRenderer` to process the
         * specified data and render it
         * @param component the `AWT` component into which the specified `JAWTRenderer` and its
         * native counterpart draw
         * @param data an `int` array which contains the data to be processed and rendered
         * @param offset the index in `data` at which the data to be processed and rendered starts
         * @param length the number of elements in `data` starting at `offset` which represent
         * the data to be processed and rendered
         * @param width the width of the video frame in `data`
         * @param height the height of the video frame in `data`
         * @return `true` if data has been successfully processed
         */
        external fun process(
                handle: Long, component: Component?, data: IntArray?, offset: Int,
                length: Int, width: Int, height: Int,
        ): Boolean

        external fun removeNotify(handle: Long, component: Component?)
        private external fun sysctlbyname(name: String): String?
    }
}