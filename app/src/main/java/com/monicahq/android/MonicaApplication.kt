package com.monicahq.android

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.monicahq.android.instance.InstanceRepository

private val Context.settings by preferencesDataStore(name = "settings")

/**
 * Creates the app's long-lived objects once. A plain container is enough while there are only a
 * few of them; Hilt comes when several screens share several dependencies.
 */
class MonicaApplication : Application() {
    val instanceRepository: InstanceRepository by lazy { InstanceRepository(settings) }
}
