/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.device.util

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView
import timber.log.Timber

/**
 * A [TextureView] that can be adjusted to a specified aspect ratio.
 */
class AutoFitTextureView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0) : TextureView(context!!, attrs, defStyle) {
    var mRatioWidth = 0
    var mRatioHeight = 0

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width Relative horizontal size
     * @param height Relative vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        require(!(width < 0 || height < 0)) { "Size cannot be negative." }
        if (mRatioWidth == width && mRatioHeight == height) {
            return
        }
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    /**
     * onMeasure will return the container dimension and not the device display size
     *
     * @param widthMeasureSpec
     * @param heightMeasureSpec
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height)
        } else {
            setMeasuredDimension(mRatioWidth, mRatioHeight)
            //            if (width < height * mRatioWidth / mRatioHeight) {
//                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
//                Timber.d("AutoFit TextureView onMeasureW: [%s x %s] => [%s x %s]", width, height, width, width * mRatioHeight / mRatioWidth);
//            }
//            else {
//                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
//                Timber.d("AutoFit TextureView onMeasureH: [%s x %s] => [%s x %s]", width, height, height * mRatioHeight / mRatioWidth, height);
//            }
        }
        Timber.d("AutoFit TextureView onMeasureWH: [%s x %s] => [%s x %s]", width, height, mRatioWidth, mRatioHeight)
    }
}