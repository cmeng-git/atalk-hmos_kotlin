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

import java.awt.Color
import java.awt.Component
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/**
 * Implements a `Container` for video/visual `Component`s.
 * `VideoContainer` uses [VideoLayout] to layout the video/visual
 * `Component`s it contains. A specific `Component` can be
 * displayed by default at [VideoLayout.CENTER_REMOTE].
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
class VideoContainer(noVideoComponent: Component, conference: Boolean) : TransparentPanel() {
    /**
     * The number of times that `add` or `remove` methods are
     * currently being executed on this instance. Decreases the number of
     * unnecessary invocations to [.doLayout], [.repaint] and
     * [.validate].
     */
    private var inAddOrRemove = 0

    /**
     * The `Component` to be displayed by this `VideoContainer`
     * at [VideoLayout.CENTER_REMOTE] when no other `Component` has
     * been added to it to be displayed there. For example, the avatar of the
     * remote peer may be displayed in place of the remote video when the remote  video is not available.
     */
    private val noVideoComponent: Component
    private val propertyChangeListener = PropertyChangeListener { ev -> propertyChange(ev) }
    private val syncRoot = Any()

    /**
     * The indicator which determines whether this instance is aware that
     * [.doLayout], [.repaint] and/or [.validate] are to
     * be invoked (as soon as [.inAddOrRemove] decreases from a positive number to zero).
     */
    private var validateAndRepaint = false

    /**
     * Initializes a new `VideoContainer` with a specific
     * `Component` to be displayed when no remote video is available.
     *
     * noVideoComponent he component to be displayed when no remote video is available
     * conference true` to dedicate the new instance to a telephony conferencing user interface; otherwise, `false`
     */
    init {
        layout = VideoLayout(conference)
        this.noVideoComponent = noVideoComponent
        if (DEFAULT_BACKGROUND_COLOR != null) background = DEFAULT_BACKGROUND_COLOR
        addContainerListener(
                object : ContainerListener {
                    override fun componentAdded(ev: ContainerEvent) {
                        onContainerEvent(ev)
                    }

                    override fun componentRemoved(ev: ContainerEvent) {
                        onContainerEvent(ev)
                    }
                }
        )
        if (this.noVideoComponent != null) add(this.noVideoComponent, VideoLayout.CENTER_REMOTE, -1)
    }

    /**
     * Adds the given component at the [VideoLayout.CENTER_REMOTE]
     * position in the default video layout.
     *
     * @param comp
     * the component to add
     * @return the added component
     */
    override fun add(comp: Component): Component {
        add(comp, VideoLayout.CENTER_REMOTE)
        return comp
    }

    override fun add(comp: Component, index: Int): Component {
        add(comp, null, index)
        return comp
    }

    override fun add(comp: Component, constraints: Any) {
        add(comp, constraints, -1)
    }

    /**
     * Overrides the default behavior of add in order to be sure to remove the
     * default "no video" component when a remote video component is added.
     *
     * @param comp
     * the component to add
     * @param constraints
     * @param index
     */
    override fun add(comp: Component, constraints: Any?, index: Int) {
        enterAddOrRemove()
        try {
            if ((VideoLayout.CENTER_REMOTE == constraints && noVideoComponent != null
                            && noVideoComponent != comp) || comp == noVideoComponent && noVideoComponent.parent != null) {
                remove(noVideoComponent)
            }
            super.add(comp, constraints, index)
        } finally {
            exitAddOrRemove()
        }
    }

    private fun enterAddOrRemove() {
        synchronized(syncRoot) {
            if (inAddOrRemove == 0) validateAndRepaint = false
            inAddOrRemove++
        }
    }

    private fun exitAddOrRemove() {
        synchronized(syncRoot) {
            inAddOrRemove--
            if (inAddOrRemove < 1) {
                inAddOrRemove = 0
                if (validateAndRepaint) {
                    validateAndRepaint = false
                    if (isDisplayable) {
                        if (isValid) doLayout() else validate()
                        repaint()
                    } else doLayout()
                }
            }
        }
    }

    /**
     * Notifies this instance that a specific `Component` has been added
     * to or removed from this `Container`.
     *
     * @param ev
     * a `ContainerEvent` which details the specifics of the
     * notification such as the `Component` that has been added or
     * removed
     */
    private fun onContainerEvent(ev: ContainerEvent) {
        try {
            val component = ev.child
            when (ev.id) {
                ContainerEvent.COMPONENT_ADDED -> component.addPropertyChangeListener(
                        PREFERRED_SIZE_PROPERTY_NAME, propertyChangeListener)
                ContainerEvent.COMPONENT_REMOVED -> component.removePropertyChangeListener(
                        PREFERRED_SIZE_PROPERTY_NAME, propertyChangeListener)
            }

            /*
			 * If an explicit background color is to be displayed by this
             * Component, make sure that its opaque property i.e. transparency
             * does not interfere with that display.
             */
            if (DEFAULT_BACKGROUND_COLOR != null) {
                var componentCount = componentCount
                if (componentCount == 1 && getComponent(0) === noVideoComponent) {
                    componentCount = 0
                }
                isOpaque = componentCount > 0
            }
        } finally {
            synchronized(syncRoot) { if (inAddOrRemove != 0) validateAndRepaint = true }
        }
    }

    /**
     * Notifies this instance about a change in the value of a property of a
     * `Component` contained by this `Container`. Since the
     * `VideoLayout` of this `Container` sizes the contained
     * `Component`s based on their `preferredSize`s, this
     * `Container` invokes [.doLayout], [.repaint] and/or
     * [.validate] upon changes in the values of the property in
     * question.
     *
     * @param ev
     * a `PropertyChangeEvent` which details the specifics of
     * the notification such as the name of the property whose value changed and
     * the `Component` which fired the notification
     */
    private fun propertyChange(ev: PropertyChangeEvent) {
        if (PREFERRED_SIZE_PROPERTY_NAME == ev.propertyName && SwingUtilities.isEventDispatchThread()) {
            /*
             * The goal is to invoke doLayout, repaint and/or validate. These
             * methods and the specifics with respect to avoiding unnecessary
             * calls to them are already dealt with by enterAddOrRemove,
             * exitAddOrRemove and validateAndRepaint.
             */
            synchronized(syncRoot) {
                enterAddOrRemove()
                validateAndRepaint = true
                exitAddOrRemove()
            }
        }
    }

    /**
     * Overrides the default remove behavior in order to add the default no
     * video component when the remote video is removed.
     *
     * @param comp
     * the component to remove
     */
    override fun remove(comp: Component) {
        enterAddOrRemove()
        try {
            super.remove(comp)
            val components = components
            val videoLayout = layout as VideoLayout
            var hasComponentsAtCenterRemote = false
            for (c in components) {
                if (c != noVideoComponent && VideoLayout.CENTER_REMOTE == videoLayout.getComponentConstraints(c)) {
                    hasComponentsAtCenterRemote = true
                    break
                }
            }
            if (!hasComponentsAtCenterRemote && noVideoComponent != null
                    && noVideoComponent != comp) {
                add(noVideoComponent, VideoLayout.CENTER_REMOTE)
            }
        } finally {
            exitAddOrRemove()
        }
    }

    /**
     * Ensures noVideoComponent is displayed even when the clients of the
     * videoContainer invoke its #removeAll() to remove their previous visual
     * Components representing video. Just adding noVideoComponent upon
     * ContainerEvent#COMPONENT_REMOVED when there is no other Component left in
     * the Container will cause an infinite loop because Container#removeAll()
     * will detect that a new Component has been added while dispatching the
     * event and will then try to remove the new Component.
     */
    override fun removeAll() {
        enterAddOrRemove()
        try {
            super.removeAll()
            if (noVideoComponent != null) add(noVideoComponent, VideoLayout.CENTER_REMOTE)
        } finally {
            exitAddOrRemove()
        }
    }

    companion object {
        /**
         * Serial version UID.
         */
        private const val serialVersionUID = 0L

        /**
         * The default background color of `VideoContainer` when it contains
         * `Component` instances other than [.noVideoComponent].
         */
        val DEFAULT_BACKGROUND_COLOR = Color.BLACK
        private const val PREFERRED_SIZE_PROPERTY_NAME = "preferredSize"
    }
}