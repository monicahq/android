package com.monicahq.android.instance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The instance the person chose, kept on the device. Source of truth for the instance address. */
class InstanceRepository(
    private val dataStore: DataStore<Preferences>,
) {
    /** The saved address, or [DEFAULT_INSTANCE_URL] when the person never chose one. */
    val url: Flow<String> = dataStore.data.map { it[URL_KEY] ?: DEFAULT_INSTANCE_URL }

    /** Saves [url], which must already be normalised (see [normalizeInstanceUrl]). */
    suspend fun setUrl(url: String) {
        dataStore.edit { it[URL_KEY] = url }
    }

    private companion object {
        val URL_KEY = stringPreferencesKey("instance_url")
    }
}
