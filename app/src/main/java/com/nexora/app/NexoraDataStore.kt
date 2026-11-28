package com.nexora.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedMessage(val speaker: String, val text: String)

data class CharacterCard(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val greeting: String = "",
    val exampleDialogue: String = "",
    val systemPrompt: String = ""
)

class NexoraDataStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nexora_user_data", Context.MODE_PRIVATE)

    fun loadMessages(): List<SavedMessage> {
        val json = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                SavedMessage(item.optString("speaker"), item.optString("text"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessages(messages: List<SavedMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(JSONObject().put("speaker", message.speaker).put("text", message.text))
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun loadCharacters(): List<CharacterCard> {
        val json = prefs.getString(KEY_CHARACTERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                CharacterCard(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    description = item.optString("description"),
                    personality = item.optString("personality"),
                    scenario = item.optString("scenario"),
                    greeting = item.optString("greeting"),
                    exampleDialogue = item.optString("exampleDialogue"),
                    systemPrompt = item.optString("systemPrompt")
                )
            }.filter { it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun saveCharacters(characters: List<CharacterCard>) {
        val array = JSONArray()
        characters.forEach { card ->
            array.put(
                JSONObject()
                    .put("id", card.id)
                    .put("name", card.name)
                    .put("description", card.description)
                    .put("personality", card.personality)
                    .put("scenario", card.scenario)
                    .put("greeting", card.greeting)
                    .put("exampleDialogue", card.exampleDialogue)
                    .put("systemPrompt", card.systemPrompt)
            )
        }
        prefs.edit().putString(KEY_CHARACTERS, array.toString()).apply()
    }

    private companion object {
        const val KEY_MESSAGES = "active_chat_messages"
        const val KEY_CHARACTERS = "character_cards"
    }
}
