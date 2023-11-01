package org.atalk.hmos

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import org.atalk.hmos.gui.util.LocaleHelper.setLocale
import org.atalk.hmos.gui.util.ThemeHelper.setTheme

/**
 * BaseActivity implements the support of user set Theme and locale.
 * All app activities must extend BaseActivity inorder to support Theme and locale.
 */
open class BaseActivity : AppCompatActivity() {
    /**
     * Override AppCompatActivity#onCreate() to support Theme setting
     * Must setTheme() before super.onCreate(), otherwise user selected Theme is not working
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Always call setTheme() method in base class and before super.onCreate()
        setTheme(this)
        super.onCreate(savedInstanceState)
    }

    /**
     * Override AppCompatActivity#attachBaseContext() to support Locale setting.
     * Language value is initialized in Application class with user selected language.
     */
    override fun attachBaseContext(base: Context) {
        val context = setLocale(base)
        super.attachBaseContext(context)
    }

    /**
     * Set preference title using android inbuilt toolbar
     *
     * @param resId preference tile resourceID
     */
    fun setMainTitle(resId: Int) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.displayOptions = (ActionBar.DISPLAY_SHOW_HOME
                    or ActionBar.DISPLAY_USE_LOGO
                    or ActionBar.DISPLAY_SHOW_TITLE)
            actionBar.setLogo(R.drawable.ic_icon)
            actionBar.setTitle(resId)
        }
    }
}