package com.forgeai.chat

import android.app.Application
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.preferences.SettingsDataStore
import com.forgeai.chat.data.remote.OpenAiStreamClient
import com.forgeai.chat.data.repository.ChatRepository
import com.forgeai.chat.data.repository.DefaultChatRepository
import com.forgeai.chat.maintenance.MaintenanceScheduler
import com.forgeai.chat.nativebridge.GGUFManager
import com.forgeai.chat.nativebridge.LocalGenerationFlowAdapter
import com.forgeai.chat.nativebridge.LocalModelManager

class AppContainer(application: Application) {
    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(application)
    }

    private val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(application)
    }

    private val streamClient: OpenAiStreamClient by lazy {
        OpenAiStreamClient()
    }

    private val ggufManager: GGUFManager by lazy {
        GGUFManager()
    }

    private val localGenerationAdapter: LocalGenerationFlowAdapter by lazy {
        LocalGenerationFlowAdapter(ggufManager)
    }

    val localModelManager: LocalModelManager by lazy {
        LocalModelManager(application, ggufManager)
    }

    val chatRepository: ChatRepository by lazy {
        DefaultChatRepository(
            chatDao = database.chatDao(),
            settingsRepository = settingsDataStore,
            streamClient = streamClient,
            memoryDao = database.memoryDao(),
            localGenerationAdapter = localGenerationAdapter
        )
    }
}

class ForgeAiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MaintenanceScheduler.schedule(this)
    }
}
