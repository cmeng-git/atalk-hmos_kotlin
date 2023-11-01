/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.hmos.plugin.audioservice

import android.content.*
import android.graphics.Canvas
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.util.AttributeSet
import android.view.View

/**
 * This class draws a colorful graphical level indicator similar to an LED VU bar graph.
 *
 * This is a user defined View UI element that contains a ShapeDrawable, which means it can be
 * placed using in the XML UI configuration and updated dynamically at runtime.
 *
 * To set the level, use setLevel(level). Level should be in the range [0.0 ; 1.0].
 *
 * To change the number of segments or colors, change the segmentColors array.
 *
 * @author Trausti Kristjansson
 * @author Eng Chong Meng
 */
class SoundMeter : View {
    private var mLevel = 0.1
    val segmentColors = intArrayOf(
            -0xaaaa01,
            -0xaaaa01,
            -0xff0100,
            -0xff0100,
            -0xff0100,
            -0xff0100,
            -0xff0100,
            -0x100,
            -0x100,
            -0x100,
            -0x100,
            -0x10000,
            -0x10000,
            -0x10000)
    val segmentOffColor = -0xaaaaab

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        initBarLevelDrawable()
    }

    constructor(context: Context?) : super(context) {
        initBarLevelDrawable()
    }

    /**
     * Set the bar level. The level should be in the range [0.0 ; 1.0], i.e. 0.0 gives no lit LEDs
     * and 1.0 gives full scale.
     *
     * @param level the LED level in the range [0.0 ; 1.0].
     */
    var level: Double
        get() = mLevel
        set(level) {
            mLevel = level
            invalidate()
        }

    private fun initBarLevelDrawable() {
        mLevel = 0.1
    }

    private fun drawBar(canvas: Canvas) {
        val padding = 5 // Padding on both sides.
        var x = 0
        val y = 10
        val width = Math.floor((width / segmentColors.size).toDouble()).toInt() - 2 * padding
        val height = 50
        val mDrawable = ShapeDrawable(RectShape())
        for (i in segmentColors.indices) {
            x = x + padding
            if (mLevel * segmentColors.size > i + 0.5) {
                mDrawable.paint.color = segmentColors[i]
            } else {
                mDrawable.paint.color = segmentOffColor
            }
            mDrawable.setBounds(x, y, x + width, y + height)
            mDrawable.draw(canvas)
            x = x + width + padding
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawBar(canvas)
    }
}