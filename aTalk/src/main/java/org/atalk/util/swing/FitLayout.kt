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
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.math.roundToInt

/**
 * Represents a `LayoutManager` which centers the first
 * `Component` within its `Container` and, if the preferred size
 * of the `Component` is larger than the size of the `Container`,
 * scales the former within the bounds of the latter while preserving the aspect
 * ratio. `FitLayout` is appropriate for `Container`s which
 * display a single image or video `Component` in its entirety for which
 * preserving the aspect ratio is important.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
open class FitLayout : LayoutManager {
    /**
     * {@inheritDoc}
     *
     *
     * Does nothing because this `LayoutManager` lays out only the first
     * `Component` of the parent `Container` and thus doesn't need
     * any `String` associations.
     */
    open fun addLayoutComponent(name: String?, comp: Component) {}

    /**
     * Gets the first `Component` of a specific `Container` if
     * there is such a `Component`.
     *
     * @param parent
     * the `Container` to retrieve the first
     * `Component` of
     * @return the first `Component` of a specific `Container` if
     * there is such a `Component`; otherwise, `null`
     */
    protected open fun getComponent(parent: Container): Component? {
        val components = parent.components
        return if (components.isNotEmpty()) components[0] else null
    }

    protected fun layoutComponent(
            component: Component,
            bounds: Rectangle,
            alignmentX: Float, alignmentY: Float,
    ) {
        var size = Dimension(0, 0)

        /*
		 * XXX The following (mostly) represents a quick and dirty hack for the
         * purposes of video conferencing which adds transparent JPanels to
         * VideoContainer and does not want them fitted because they contain
         * VideoContainers themselves and the videos get fitted in them.
         */
        if (((component is JPanel
                        && !component.isOpaque()) && (component as Container).componentCount > 1
                        || component is VideoContainer) || component.preferredSize.also { size = it } == null) {
            size = bounds.size
        }
        else {
            var scale = false
            val widthRatio: Double
            val heightRatio: Double
            if (size.width != bounds.width && size.width > 0) {
                scale = true
                widthRatio = bounds.width / size.width.toDouble()
            }
            else widthRatio = 1.0
            if (size.height != bounds.height && size.height > 0) {
                scale = true
                heightRatio = bounds.height / size.height.toDouble()
            }
            else heightRatio = 1.0
            if (scale) {
                val ratio = Math.min(widthRatio, heightRatio)
                size.width = (size.width * ratio).toInt()
                size.height = (size.height * ratio).toInt()
            }
        }

        // Respect the maximumSize of the component.
        if (component.isMaximumSizeSet) {
            val maxSize = component.maximumSize
            if (size.width > maxSize.width) size.width = maxSize.width
            if (size.height > maxSize.height) size.height = maxSize.height
        }

        /*
		 * Why would one fit a Component into a rectangle with zero width and
         * height?
         */
        if (size.height < 1) size.height = 1
        if (size.width < 1) size.width = 1
        component.setBounds(
            bounds.x + ((bounds.width - size.width) * alignmentX).roundToInt(),
            bounds.y + ((bounds.height - size.height) * alignmentY).roundToInt(),
            size.width,
            size.height)
    }

    /*
	 * Scales the first Component if its preferred size is larger than the size
	 * of its parent Container in order to display the Component in its entirety
	 * and then centers it within the display area of the parent.
	 */
    open fun layoutContainer(parent: Container) {
        layoutContainer(parent, Component.CENTER_ALIGNMENT)
    }

    protected fun layoutContainer(parent: Container, componentAlignmentX: Float) {
        val component = getComponent(parent)
        if (component != null) {
            layoutComponent(
                component,
                Rectangle(parent.size),
                componentAlignmentX, Component.CENTER_ALIGNMENT)
        }
    }

    /*
	 * Since this LayoutManager lays out only the first Component of the
	 * specified parent Container, the minimum size of the Container is the
	 * minimum size of the mentioned Component.
	 */
    fun minimumLayoutSize(parent: Container): Dimension {
        val component = getComponent(parent)
        return if (component != null) component.minimumSize else Dimension(DEFAULT_HEIGHT_OR_WIDTH, DEFAULT_HEIGHT_OR_WIDTH)
    }

    /**
     * {@inheritDoc}
     *
     *
     * Since this `LayoutManager` lays out only the first
     * `Component` of the specified parent `Container`, the
     * preferred size of the `Container` is the preferred size of the
     * mentioned `Component`.
     */
    open fun preferredLayoutSize(parent: Container): Dimension? {
        val component = getComponent(parent)
        return if (component != null) component.preferredSize else Dimension(DEFAULT_HEIGHT_OR_WIDTH, DEFAULT_HEIGHT_OR_WIDTH)
    }

    /**
     * {@inheritDoc}
     *
     *
     * Does nothing because this `LayoutManager` lays out only the first
     * `Component` of the parent `Container` and thus doesn't need
     * any `String` associations.
     */
    open fun removeLayoutComponent(comp: Component) {}

    companion object {
        /**
         * The default height and width to be used by `FitLayout` and its
         * extenders in order to avoid falling back to zero height and/or width.
         * Introduced to mitigate issues arising from the fact that a
         * `Component` zero height and/or width.
         */
        const val DEFAULT_HEIGHT_OR_WIDTH = 16
    }
}