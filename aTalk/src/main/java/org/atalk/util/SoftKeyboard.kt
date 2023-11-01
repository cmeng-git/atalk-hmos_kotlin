package org.atalk.util

import android.os.Handler
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Author: Felipe Herranz (felhr85@gmail.com)
 * Contributors:Francesco Verheye (verheye.francesco@gmail.com)
 * 		Israel Dominguez (dominguez.israel@gmail.com)
 */
class SoftKeyboard(private val layout: ViewGroup, imm: InputMethodManager) : View.OnFocusChangeListener {
    private var layoutBottom = 0
    private val imm: InputMethodManager
    private val coords: IntArray
    private var isKeyboardShow: Boolean
    private val softKeyboardThread: SoftKeyboardChangesThread
    private var editTextList: MutableList<EditText>? = null

    // reference to a focused EditText
    private var tempView: View? = null
    fun openSoftKeyboard() {
        if (!isKeyboardShow) {
            layoutBottom = layoutCoordinates
            imm.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT)
            softKeyboardThread.keyboardOpened()
            isKeyboardShow = true
        }
    }

    fun closeSoftKeyboard() {
        if (isKeyboardShow) {
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
            isKeyboardShow = false
        }
    }

    fun setSoftKeyboardCallback(mCallback: SoftKeyboardChanged?) {
        softKeyboardThread.setCallback(mCallback)
    }

    fun unRegisterSoftKeyboardCallback() {
        softKeyboardThread.stopThread()
    }

    interface SoftKeyboardChanged {
        fun onSoftKeyboardHide()
        fun onSoftKeyboardShow()
    }

    private val layoutCoordinates: Int
        private get() {
            layout.getLocationOnScreen(coords)
            return coords[1] + layout.height
        }

    private fun keyboardHideByDefault() {
        layout.isFocusable = true
        layout.isFocusableInTouchMode = true
    }

    /*
	 * InitEditTexts now handles EditTexts in nested views
	 * Thanks to Francesco Verheye (verheye.francesco@gmail.com)
	 */
    private fun initEditTexts(viewgroup: ViewGroup) {
        if (editTextList == null) editTextList = ArrayList()
        val childCount = viewgroup.childCount
        for (i in 0..childCount - 1) {
            val v = viewgroup.getChildAt(i)
            if (v is ViewGroup) {
                initEditTexts(v)
            }
            if (v is EditText) {
                val editText = v
                editText.onFocusChangeListener = this
                editText.isCursorVisible = true
                editTextList!!.add(editText)
            }
        }
    }

    /*
	 * OnFocusChange does update tempView correctly now when keyboard is still shown
	 * Thanks to Israel Dominguez (dominguez.israel@gmail.com)
	 */
    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (hasFocus) {
            tempView = v
            if (!isKeyboardShow) {
                layoutBottom = layoutCoordinates
                softKeyboardThread.keyboardOpened()
                isKeyboardShow = true
            }
        }
    }

    // This handler will clear focus of selected EditText
    private val mHandler = object : Handler() {
        override fun handleMessage(m: Message) {
            when (m.what) {
                CLEAR_FOCUS -> if (tempView != null) {
                    tempView!!.clearFocus()
                    tempView = null
                }
            }
        }
    }

    init {
        keyboardHideByDefault()
        initEditTexts(layout)
        this.imm = imm
        coords = IntArray(2)
        isKeyboardShow = false
        softKeyboardThread = SoftKeyboardChangesThread()
        softKeyboardThread.start()
    }

    private inner class SoftKeyboardChangesThread : Thread() {
        private val started = AtomicBoolean(true)
        private var mCallback: SoftKeyboardChanged? = null

        fun setCallback(mCallback: SoftKeyboardChanged?) {
            this.mCallback = mCallback
        }

        override fun run() {
            while (started.get()) {
                // Wait until keyboard is requested to open
                synchronized(this) {
                    try {
                        (this as Object).wait()
                    } catch (e: InterruptedException) {
                        Timber.w("Exception in starting keyboard thread: %s", e.message)
                    }
                }
                var currentBottomLocation = layoutCoordinates
                // Timber.d("SoftKeyboard Current Bottom Location #1: %s (%s)", currentBottomLocation, layoutBottom);

                // There is some lag between open soft-keyboard function and when it really appears.
                while (currentBottomLocation == layoutBottom && started.get()) {
                    currentBottomLocation = layoutCoordinates
                }
                // Timber.d("SoftKeyboard Current Bottom Location #2: %s (%s)", currentBottomLocation, layoutBottom);
                if (started.get()) mCallback!!.onSoftKeyboardShow()

                // When keyboard is opened from EditText, initial bottom location is greater than
                // layoutBottom and at some moment later <= layoutBottom.
                // That broke the previous logic, so I added this new loop to handle this.
                while (currentBottomLocation >= layoutBottom && started.get()) {
                    currentBottomLocation = layoutCoordinates
                }

                // Now Keyboard is shown, keep checking layout dimensions until keyboard is gone
                while (currentBottomLocation != layoutBottom && started.get()) {
                    // Timber.d("SoftKeyboard Current Bottom Location #3x %s (%s)", currentBottomLocation, layoutBottom);
                    synchronized(this) {
                        try {
                            (this as Object).wait(500)
                        } catch (e: InterruptedException) {
                            Timber.w("Exception in waiting for keyboard hide: %s", e.message)
                        }
                    }
                    currentBottomLocation = layoutCoordinates
                }
                if (started.get()) mCallback!!.onSoftKeyboardHide()

                // if keyboard has been opened clicking and EditText.
                if (isKeyboardShow && started.get()) isKeyboardShow = false

                // if an EditText is focused, remove its focus (on UI thread)
                if (started.get()) mHandler.obtainMessage(CLEAR_FOCUS).sendToTarget()
            }
        }

        fun keyboardOpened() {
            synchronized(this) { (this as Object).notify() }
        }

        fun stopThread() {
            synchronized(this) {
                started.set(false)
                (this as Object).notify()
            }
        }
    }

    companion object {
        private const val CLEAR_FOCUS = 0
    }
}