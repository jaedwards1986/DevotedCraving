package com.forgeai.chat.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeai.chat.domain.settings.NatashaSettingDimension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    @Test
    fun simultaneousChangesOnSameRealDataStoreAreMergedWithoutLostUpdates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileName = "natasha_personality_concurrency_test_${UUID.randomUUID()}"
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile(fileName) }
        )
        val service = UndoService(dataStore)

        // Both callers wait at the same gate, then race through the real DataStore.edit API.
        // There is deliberately no Mutex or other test-side serialization around service calls.
        val readyA = CompletableDeferred<Unit>()
        val readyB = CompletableDeferred<Unit>()
        val startGate = CompletableDeferred<Unit>()

        val affectionUpdate = async(Dispatchers.IO) {
            readyA.complete(Unit)
            startGate.await()
            service.applyChange(
                dimension = NatashaSettingDimension.AFFECTION,
                newValue = 0.8,
                originalRequest = "set affection to 0.8"
            )
        }
        val initiativeUpdate = async(Dispatchers.IO) {
            readyB.complete(Unit)
            startGate.await()
            service.applyChange(
                dimension = NatashaSettingDimension.INITIATIVE,
                newValue = 0.8,
                originalRequest = "set initiative to 0.8"
            )
        }

        readyA.await()
        readyB.await()
        startGate.complete(Unit)
        listOf(affectionUpdate, initiativeUpdate).awaitAll()

        val snapshot = service.getCurrentSnapshot()
        assertEquals(2L, snapshot.version)
        assertEquals(0.8, snapshot.value(NatashaSettingDimension.AFFECTION), 0.000001)
        assertEquals(0.8, snapshot.value(NatashaSettingDimension.INITIATIVE), 0.000001)

        val history = service.getRecentHistory(limit = 10)
        assertEquals(2, history.size)
        assertEquals(
            setOf(
                NatashaSettingDimension.AFFECTION.key,
                NatashaSettingDimension.INITIATIVE.key
            ),
            history.map { it.dimension }.toSet()
        )
        assertEquals(2, service.getAuditLog().size)

        // Both successful transactions must remain independently undoable.
        assertTrue(service.undoLastChange() is UndoResult.Restored)
        assertTrue(service.undoLastChange() is UndoResult.Restored)
        val restored = service.getCurrentSnapshot()
        NatashaSettingDimension.entries.forEach { dimension ->
            assertEquals(dimension.defaultValue, restored.value(dimension), 0.000001)
        }
        assertEquals(4L, restored.version)
    }

}
