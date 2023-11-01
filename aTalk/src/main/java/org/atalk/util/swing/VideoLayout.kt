/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util.swing

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.util.*
import javax.swing.JLabel
import kotlin.math.roundToInt

/**
 * Implements the `LayoutManager` which lays out the local and remote videos in a video `Call`.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class VideoLayout
/**
 * Creates an instance of `VideoLayout` by also indicating if this
 * video layout is dedicated to a conference interface.
 *
 * @param conference `true` if the new instance will be dedicated to
 * a conference; otherwise, `false`
 */
(
        /**
         * The indicator which determines whether this instance is dedicated to a conference.
         */
        private val conference: Boolean) : FitLayout() {
    /**
     * The video canvas.
     */
    private var canvas: Component? = null
    /**
     * Returns the local video close button.
     *
     * @return the local video close button
     */
    /**
     * The close local video button component.
     */
    private var localCloseButton: Component? = null

    /**
     * The map of component constraints.
     */
    private val constraints = HashMap<Component, Any?>()
    /**
     * Returns the local video component.
     *
     * @return the local video component
     */
    /**
     * The component containing the local video.
     */
    var local: Component? = null
        private set

    /**
     * The x coordinate alignment of the remote video.
     */
    private var remoteAlignmentX = Component.CENTER_ALIGNMENT

    /**
     * The list of `Component`s depicting remote videos.
     */
    private val remotes = LinkedList<Component>()

    /**
     * Adds the given component in this layout on the specified by name  position.
     *
     * @param name the constraint giving the position of the component in this layout
     * @param comp the component to add
     */
    override fun addLayoutComponent(name: String?, comp: Component) {
        super.addLayoutComponent(name, comp)
        synchronized(constraints) { constraints.put(comp, name) }
        when (name) {
            null, CENTER_REMOTE -> {
                if (!remotes.contains(comp)) remotes.add(comp)
                remoteAlignmentX = Component.CENTER_ALIGNMENT
            }
            EAST_REMOTE -> {
                if (!remotes.contains(comp)) remotes.add(comp)
                remoteAlignmentX = Component.RIGHT_ALIGNMENT
            }
            LOCAL -> local = comp
            CLOSE_LOCAL_BUTTON -> localCloseButton = comp
            CANVAS -> canvas = comp
        }
    }

    /**
     * Determines how may columns to use for the grid display of specific remote visual/video `Component`s.
     *
     * @param remotes the remote visual/video `Component`s to be displayed in a grid
     * @return the number of columns to use for the grid display of the
     * specified remote visual/video `Component`s
     */
    private fun calculateColumnCount(remotes: List<Component>): Int {
        val remoteCount = remotes.size
        return if (remoteCount == 1) 1 else if (remoteCount == 2 || remoteCount == 4) 2 else 3
    }

    /**
     * Returns the remote video component.
     *
     * @return the remote video component
     */
    override fun getComponent(parent: Container): Component? {
        return if (remotes.size == 1) remotes[0] else null
    }

    /**
     * Returns the constraints for the given component.
     *
     * @param c the component for which constraints we're looking for
     * @return the constraints for the given component
     */
    fun getComponentConstraints(c: Component): Any? {
        synchronized(constraints) { return constraints[c] }
    }

    /**
     * Lays out the specified `Container` (i.e. the `Component`s
     * it contains) in accord with the logic implemented by this `LayoutManager`.
     *
     * @param parent the `Container` to lay out
     */
    override fun layoutContainer(parent: Container) {
        /*
         * XXX The methods layoutContainer and preferredLayoutSize must be kept in sync.
         */
        val visibleRemotes = ArrayList<Component>()
        val remotes: MutableList<Component>
        val local = local
        for (i in this.remotes.indices) {
            if (this.remotes[i].isVisible) visibleRemotes.add(this.remotes[i])
        }

        /*
         * When there are multiple remote visual/video Components, the local one
         * will be displayed as if it is a remote one i.e. in the same grid, not
         * on top of a remote one. The same layout will be used when this
         * instance is dedicated to a telephony conference.
         */
        if ((conference || visibleRemotes.size > 1 && local != null)) {
            remotes = ArrayList(visibleRemotes)
            if (local != null) remotes.add(local)
        } else remotes = visibleRemotes
        val remoteCount = remotes.size
        val parentSize = parent.size
        if (!conference && remoteCount == 1) {
            /*
             * If the videos are to be laid out as in a one-to-one call, the
             * remote video has to fill the parent and the local video will be
             * placed on top of the remote video. The remote video will be laid
             * out now and the local video will be laid out later/further bellow.
             */
            super.layoutContainer(parent,
                    if (local == null) Component.CENTER_ALIGNMENT else remoteAlignmentX)
        } else if (remoteCount > 0) {
            val columns = calculateColumnCount(remotes)
            val columnsMinus1 = columns - 1
            val rows = (remoteCount + columnsMinus1) / columns
            val rowsMinus1 = rows - 1
            val bounds = Rectangle(0, 0,  /*
                     * HGAP is the horizontal gap between the Components
                     * being laid out by this VideoLayout so the number of
                     * HGAPs will be with one less than the number of
                     * columns and that horizontal space cannot be allocated
                     * to the bounds of the Components.
                     */
                    parentSize.width - columnsMinus1 * HGAP / columns,
                    parentSize.height / rows)
            for (i in 0 until remoteCount) {
                val column = i % columns
                val row = i / columns

                /*
                 * On the x axis, the first column starts at zero and each
                 * subsequent column starts relative to the end of its preceding column.
                 */
                if (column == 0) {
                    bounds.x = 0
                    /*
                     * Eventually, there may be empty cells in the last row.
                     * Center the non-empty cells horizontally.
                     */
                    if (row == rowsMinus1) {
                        val available = remoteCount - i
                        if (available < columns) {
                            bounds.x = parentSize.width - available * bounds.width - (available - 1) * HGAP / 2
                        }
                    }
                } else bounds.x += bounds.width + HGAP
                bounds.y = row * bounds.height
                super.layoutComponent(
                        remotes[i],
                        bounds,
                        Component.CENTER_ALIGNMENT,
                        Component.CENTER_ALIGNMENT)
            }
        }
        if (local == null) {
            /*
             * It is plain wrong to display a close button for the local video if there is no local video.
             */
            if (localCloseButton != null) localCloseButton!!.isVisible = false
        } else {
            /*
             * If the local visual/video Component is not displayed as if it is
             * a remote one, it will be placed on top of a remote one.
             */
            if (!remotes.contains(local)) {
                val remote0 = if (remotes.isEmpty()) null else remotes[0]
                val localX: Int
                val localY: Int
                val height = (parentSize.height * LOCAL_TO_REMOTE_RATIO).roundToInt()
                val width = (parentSize.width * LOCAL_TO_REMOTE_RATIO).roundToInt()
                val alignmentX: Float

                /*
                 * XXX The remote Component being a JLabel is meant to signal
                 * that there is no remote video and the remote is the photoLabel.
                 */
                if (remoteCount == 1 && remote0 is JLabel) {
                    localX = (parentSize.width - width) / 2
                    localY = parentSize.height - height
                    alignmentX = Component.CENTER_ALIGNMENT
                } else {
                    localX = (remote0?.x ?: 0) + 5
                    localY = parentSize.height - height - 5
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                super.layoutComponent(
                        local,
                        Rectangle(localX, localY, width, height),
                        alignmentX,
                        Component.BOTTOM_ALIGNMENT)
            }

            /* The closeButton has to be on top of the local video. */
            if (localCloseButton != null) {
                /*
                 * XXX We may be overwriting the visible property set by our
                 * client (who has initialized the close button) but it is wrong
                 * to display a close button for the local video if the local video is not visible.
                 */
                localCloseButton!!.isVisible = local.isVisible
                super.layoutComponent(
                        localCloseButton!!,
                        Rectangle(local.x + local.width - localCloseButton!!.width,
                                local.y,
                                localCloseButton!!.width,
                                localCloseButton!!.height),
                        Component.CENTER_ALIGNMENT,
                        Component.CENTER_ALIGNMENT)
            }
        }

        /*
         * The video canvas will get the locations of the other components to
         * paint so it has to cover the parent completely.
         */
        if (canvas != null) canvas!!.setBounds(0, 0, parentSize.width, parentSize.height)
    }

    /**
     * Returns the preferred layout size for the given container.
     *
     * @param parent the container which preferred layout size we're looking for
     * @return a Dimension containing, the preferred layout size for the given container
     */
    override fun preferredLayoutSize(parent: Container): Dimension? {
        val visibleRemotes = ArrayList<Component>()
        val remotes: MutableList<Component>
        val local = local
        for (i in this.remotes.indices) {
            if (this.remotes[i].isVisible) visibleRemotes.add(this.remotes[i])
        }

        /*
         * When there are multiple remote visual/video Components, the local one
         * will be displayed as if it is a remote one i.e. in the same grid, not
         * on top of a remote one. The same layout will be used when this
         * instance is dedicated to a telephony conference.
         */
        if (conference || visibleRemotes.size > 1 && local != null) {
            remotes = ArrayList(visibleRemotes)
            if (local != null) remotes.add(local)
        } else remotes = visibleRemotes
        val remoteCount = remotes.size
        var prefLayoutSize: Dimension?
        if (!conference && remoteCount == 1) {
            /*
             * If the videos are to be laid out as in a one-to-one call, the
             * remote video has to fill the parent and the local video will be
             * placed on top of the remote video. The remote video will be laid
             * out now and the local video will be laid out later/further bellow.
             */
            prefLayoutSize = super.preferredLayoutSize(parent)
        } else if (remoteCount > 0) {
            val columns = calculateColumnCount(remotes)
            val columnsMinus1 = columns - 1
            val rows = (remoteCount + columnsMinus1) / columns
            var i = 0
            val prefSizes = arrayOfNulls<Dimension>(columns * rows)
            for (remote in remotes) {
                val column = columnsMinus1 - i % columns
                val row = i / columns
                prefSizes[column + row * columns] = remote.preferredSize
                i++
                if (i >= remoteCount) break
            }
            var prefLayoutWidth = 0
            for (column in 0 until columns) {
                var prefColumnWidth = 0
                for (row in 0 until rows) {
                    val prefSize = prefSizes[column + row * columns]
                    if (prefSize != null) prefColumnWidth += prefSize.width
                }
                prefColumnWidth /= rows
                prefLayoutWidth += prefColumnWidth
            }
            var prefLayoutHeight = 0
            for (row in 0 until rows) {
                var prefRowHeight = 0
                for (column in 0 until columns) {
                    val prefSize = prefSizes[column + row * columns]
                    if (prefSize != null) prefRowHeight = prefSize.height
                }
                prefRowHeight /= columns
                prefLayoutHeight += prefRowHeight
            }
            prefLayoutSize = Dimension(
                    prefLayoutWidth + columnsMinus1 * HGAP,
                    prefLayoutHeight)
        } else prefLayoutSize = null
        if (local != null) {
            /*
             * If the local visual/video Component is not displayed as if it is
             * a remote one, it will be placed on top of a remote one. Then for
             * the purposes of the preferredLayoutSize method it needs to be
             * considered only if there is no remote video whatsoever.
             */
            if (!remotes.contains(local) && prefLayoutSize == null) {
                val prefSize = local.preferredSize
                if (prefSize != null) {
                    val prefHeight = (prefSize.height * LOCAL_TO_REMOTE_RATIO).roundToInt()
                    val prefWidth = (prefSize.width * LOCAL_TO_REMOTE_RATIO).roundToInt()
                    prefLayoutSize = Dimension(prefWidth, prefHeight)
                }
            }
            /*
             * The closeButton has to be on top of the local video.
             * Consequently, the preferredLayoutSize method does not have to
             * consider it. Well, maybe if does if the local video is smaller
             * than the closeButton... but that's just not cool anyway.
             */
        }

        /*
         * The video canvas will get the locations of the other components to
         * paint so it has to cover the parent completely. In other words, the
         * preferredLayoutSize method does not have to consider it.
         */
        if (prefLayoutSize == null) prefLayoutSize = super.preferredLayoutSize(parent) else if (prefLayoutSize.height < 1 || prefLayoutSize.width < 1) {
            prefLayoutSize.height = DEFAULT_HEIGHT_OR_WIDTH
            prefLayoutSize.width = DEFAULT_HEIGHT_OR_WIDTH
        }
        return prefLayoutSize
    }

    /**
     * Removes the given component from this layout.
     *
     * @param comp the component to remove from the layout
     */
    override fun removeLayoutComponent(comp: Component) {
        super.removeLayoutComponent(comp)
        synchronized(constraints) { constraints.remove(comp) }
        if (local === comp) local = null else if (localCloseButton === comp) localCloseButton = null else if (canvas === comp) canvas = null else remotes.remove(comp)
    }

    companion object {
        /**
         * The video canvas constraint.
         */
        const val CANVAS = "CANVAS"

        /**
         * The center remote video constraint.
         */
        const val CENTER_REMOTE = "CENTER_REMOTE"

        /**
         * The close local video constraint.
         */
        const val CLOSE_LOCAL_BUTTON = "CLOSE_LOCAL_BUTTON"

        /**
         * The east remote video constraint.
         */
        const val EAST_REMOTE = "EAST_REMOTE"

        /**
         * The horizontal gap between the `Component` being laid out by `VideoLayout`.
         */
        private const val HGAP = 10

        /**
         * The local video constraint.
         */
        const val LOCAL = "LOCAL"

        /**
         * The ration between the local and the remote video.
         */
        private const val LOCAL_TO_REMOTE_RATIO = 0.30f

        /**
         * Determines whether the aspect ratio of a specific `Dimension` is
         * to be considered equal to the aspect ratio of specific `width` and
         * `height`.
         *
         * @param size the `Dimension` whose aspect ratio is to be compared
         * to the aspect ratio of `width` and `height`
         * @param width the width which defines in combination with `height`
         * the aspect ratio to be compared to the aspect ratio of `size`
         * @param height the height which defines in combination with `width`
         * the aspect ratio to be compared to the aspect ratio of `size`
         * @return `true` if the aspect ratio of `size` is to be
         * considered equal to the aspect ratio of `width` and
         * `height`; otherwise, `false`
         */
        fun areAspectRatiosEqual(size: Dimension, width: Int, height: Int): Boolean {
            return if (size.height == 0 || height == 0) false else {
                val a = size.width / size.height.toDouble()
                val b = width / height.toDouble()
                val diff = a - b
                -0.01 < diff && diff < 0.01
            }
        }
    }
}