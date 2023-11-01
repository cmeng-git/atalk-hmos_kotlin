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
package org.atalk.hmos.gui.util

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.text.TextUtils
import java.util.*

/**
 * Implementation of LocaleHelper to support proper Locale setting for Application/Activity classes.
 *
 * @author Eng Chong Meng
 */
object LocaleHelper {
    // Default to system locale language; get init from DB by aTalkApp first call
    var language = ""

    /**
     * Set aTalk Locale to the current mLanguage
     *
     * @param ctx Context
     */
    fun setLocale(ctx: Context): Context {
        return wrap(ctx, language)
    }

    /**
     * Set the locale as per specified language; must use Application instance
     *
     * @param ctx Base Context
     * @param language the new UI language
     */
    fun setLocale(ctx: Context, language: String): Context {
        LocaleHelper.language = language
        return wrap(ctx, language)
    }

    /**
     * Update the app local as per specified language.
     *
     * @param context Base Context (ContextImpl)
     * @param language the new UI language
     * #return The new ContextImpl for use by caller
     */
    fun wrap(context: Context, language: String): Context {
        val config = context.resources.configuration
        val locale: Locale
        locale = if (TextUtils.isEmpty(language)) {
            // System default
            Resources.getSystem().configuration.locale
        } else if (language.length == 5 && language[2] == '_') {
            // language is in the form: en_US
            Locale(language.substring(0, 2), language.substring(3))
        } else {
            Locale(language)
        }
        Locale.setDefault(locale)
        config.setLayoutDirection(locale)
        config.setLocale(locale)

        // Timber.d(new Exception(), "set locale: %s: %s", language, context);
        return context.createConfigurationContext(config)
    }
}