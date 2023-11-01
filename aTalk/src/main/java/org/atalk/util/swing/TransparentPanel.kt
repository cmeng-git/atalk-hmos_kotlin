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

import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * Represents a `JPanel` which sets its `opaque` property to `false` during its initialization.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
open class TransparentPanel : JPanel {
    /**
     * Initializes a new `TransparentPanel` instance.
     */
    constructor() {
        isOpaque = false
    }

    /**
     * Initializes a new `TransparentPanel` instance which is to use a specific `LayoutManager`.
     *
     * @param layout the `LayoutManager` to be used by the new instance
     */
    constructor(layout: LayoutManager?) : super(layout) {
        isOpaque = false
    }

    companion object {
        private const val serialVersionUID = 0L
    }
}