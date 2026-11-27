package ai.davoted.craving.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

enum class LocalModelStatus {
    UNAVAILABLE,
    UNLOADED,
    LOADING,
    READY,
    GENERATING,
    FAILED
}

data class ConversationMessage(
    val role: String,
    val content: String
)

data class LocalChatTurn(
    val assistantText: String,
    val experienceState: ExperienceState,
    val stateWasModelProposed: Boolean,
    val contextFingerprint: String = ""
)

data class LocalModelConfiguration(
    val contextSize: Int = 4096,
    val threadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
    val maxTokens: Int = 512,
    val temperature: Float = 0.8f
)

/**
 * Local-only inference adapter. It has no HTTP client, API key, or remote fallback.
 * All native model operations run on Dispatchers.IO and are serialized per adapter instance.
 */
class LlamaCppAdapter(
    private val runtime: LlamaCppRuntime = JniLlamaCppRuntime(),
    private val controller: ExperienceStateController = ExperienceStateController(),
    private val configuration: LocalModelConfiguration = LocalModelConfiguration()
) : AutoCloseable {
    private val operationMutex = Mutex()
    private val promptContextAssembler = PromptContextAssembler()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Volatile
    var status: LocalModelStatus = if (runtime.isAvailable) {
        LocalModelStatus.UNLOADED
    } else {
        LocalModelStatus.UNAVAILABLE
    }
        private set

    @Volatile
    var experienceState: ExperienceState = ExperienceState()
        private set

    suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            if (!runtime.isAvailable) {
                status = LocalModelStatus.UNAVAILABLE
                return@withLock Result.failure(
                    IllegalStateException("llama.cpp native library is not packaged in this build.")
                )
            }
            status = LocalModelStatus.LOADING
            runCatching {
                require(modelPath.isNotBlank()) { "Model path must not be blank." }
                check(runtime.loadModel(modelPath, configuration.contextSize, configuration.threadCount)) {
                    "llama.cpp could not load the GGUF model."
                }
                status = LocalModelStatus.READY
            }.onFailure {
                status = LocalModelStatus.FAILED
            }
        }
    }

    suspend fun generate(
        messages: List<ConversationMessage>,
        confirmedPreferences: List<String> = emptyList(),
        exclusions: List<String> = emptyList(),
        archiveExperience: AssembledArchiveExperience? = null,
        relationshipContext: List<String> = emptyList()
    ): Result<LocalChatTurn> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (status != LocalModelStatus.READY) {
                    return@withLock Result.failure(
                        IllegalStateException("Local model is not ready; current status is $status.")
                    )
                }
                status = LocalModelStatus.GENERATING
                try {
                    require(messages.isNotEmpty()) { "At least one conversation message is required." }
                    val assembled = promptContextAssembler.assemble(
                        context = ConversationContext(
                            personaState = experienceState,
                            confirmedPreferences = confirmedPreferences,
                            exclusions = exclusions,
                            relationshipContext = relationshipContext,
                            archiveExperience = archiveExperience
                        ),
                        messages = messages
                    )
                    val raw = runtime.generate(
                        prompt = assembled.serializedPrompt,
                        maxTokens = configuration.maxTokens,
                        temperature = configuration.temperature
                    )
                    val parsed = parseModelOutput(raw, experienceState)
                    experienceState = controller.apply(experienceState, parsed.proposal)
                    status = LocalModelStatus.READY
                    Result.success(
                        LocalChatTurn(
                            assistantText = parsed.assistantText,
                            experienceState = experienceState,
                            stateWasModelProposed = parsed.stateWasModelProposed,
                            contextFingerprint = assembled.contextFingerprint
                        )
                    )
                } catch (cancelled: CancellationException) {
                    status = LocalModelStatus.READY
                    throw cancelled
                } catch (failure: Throwable) {
                    status = LocalModelStatus.FAILED
                    Result.failure(failure)
                }
            }
        }

    fun restoreExperienceState(
        mood: String?,
        intensity: Int?,
        pacing: String?,
        phase: String?
    ) {
        if (mood == null || intensity == null || pacing == null || phase == null) return
        runCatching {
            experienceState = ExperienceState(
                mood = ExperienceMood.valueOf(mood),
                intensity = intensity.coerceIn(0, 5),
                pacing = ExperiencePacing.valueOf(pacing),
                phase = ScenarioPhase.valueOf(phase)
            )
        }
    }

    fun cancelGeneration() {
        runtime.cancel()
    }

    override fun close() {
        runtime.close()
        status = LocalModelStatus.UNLOADED
    }

    private fun parseModelOutput(
        raw: String,
        fallback: ExperienceState
    ): ParsedModelOutput {
        val startTag = "<dc_state>"
        val endTag = "</dc_state>"
        val start = raw.lastIndexOf(startTag)
        val end = if (start >= 0) raw.indexOf(endTag, start + startTag.length) else -1
        if (start < 0 || end < 0) {
            return ParsedModelOutput(raw.trim(), fallback.toProposal(), false)
        }

        val jsonText = raw.substring(start + startTag.length, end).trim()
        val proposal = try {
            json.decodeFromString<ExperienceSignalProposal>(jsonText)
                .takeIf { it.schemaVersion == ExperienceSignalProposal.CURRENT_SCHEMA_VERSION }
                ?: fallback.toProposal()
        } catch (_: SerializationException) {
            fallback.toProposal()
        } catch (_: IllegalArgumentException) {
            fallback.toProposal()
        }

        val assistantText = (raw.removeRange(start, end + endTag.length)).trim()
        return ParsedModelOutput(assistantText, proposal, proposal != fallback.toProposal())
    }

    private fun ExperienceState.toProposal() = ExperienceSignalProposal(
        mood = mood,
        intensity = intensity,
        pacing = pacing,
        phase = phase
    )

    private data class ParsedModelOutput(
        val assistantText: String,
        val proposal: ExperienceSignalProposal,
        val stateWasModelProposed: Boolean
    )
}
