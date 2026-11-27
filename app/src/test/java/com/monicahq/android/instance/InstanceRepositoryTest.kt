package com.monicahq.android.instance

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstanceRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the default instance is used when none was saved`() =
        runTest {
            val repository = InstanceRepository(FakePreferencesDataStore())

            assertEquals(DEFAULT_INSTANCE_URL, repository.url.first())
        }

    @Test
    fun `a saved address is written to the file and read back`() =
        runTest {
            val file = File(folder.root, "settings.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val repository = InstanceRepository(dataStore)

            repository.setUrl("https://monica.example.com")

            assertEquals("https://monica.example.com", repository.url.first())
            assert(file.length() > 0)
        }
}
