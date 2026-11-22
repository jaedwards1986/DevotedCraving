package com.forgeai.chat

import android.app.Application
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.preferences.SettingsDataStore
import com.forgeai.chat.data.remote.OpenAiStreamClient
import com.forgeai.chat.data.repository.ChatRepository
import com.forgeai.chat.data.repository.DefaultChatRepository

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

    val chatRepository: ChatRepository by lazy {
        DefaultChatRepository(
            chatDao = database.chatDao(),
            settingsRepository = settingsDataStore,
            streamClient = streamClient
        )
    }
}

class ForgeAiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
