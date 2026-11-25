package com.forgeai.chat.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeai.chat.domain.settings.NatashaSettingDimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UndoServiceDataStoreIntegrationTest {

    @Test
    fun corruptStoredPayloadIsRejectedAndPreservedAcrossServiceOperations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "natasha_personality_corruption_test_${UUID.randomUUID()}"
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile(fileName) }
        )
        val service = UndoService(dataStore)
        val poisonedPayload = """{"snapshot":{"values":"""

        // Poison the actual Preferences DataStore before the service sees it.
        dataStore.edit { preferences ->
            preferences[PERSONALITY_STATE_KEY] = poisonedPayload
        }
        assertEquals(poisonedPayload, dataStore.data.first()[PERSONALITY_STATE_KEY])

        val readError = runCatching { service.getCurrentSnapshot() }.exceptionOrNull()
        assertTrue(
            "A corrupt persisted payload must fail closed with a typed persistence exception.",
            readError is PersonalityStatePersistenceException
        )

        val applyError = runCatching {
            service.applyChange(
                dimension = NatashaSettingDimension.AFFECTION,
                newValue = 0.7,
                originalRequest = "make yourself more affectionate"
            )
        }.exceptionOrNull()
        assertTrue(
            "Applying a change must not overwrite corrupt state with defaults.",
            applyError is PersonalityStatePersistenceException
        )

        val undoResult = service.undoLastChange()
        assertTrue(
            "Undo must report failure when the persisted state cannot be decoded.",
            undoResult is UndoResult.Failed
        )

        // Verify the exact original value is still present after all failed operations.
        assertEquals(poisonedPayload, dataStore.data.first()[PERSONALITY_STATE_KEY])
    }
}
