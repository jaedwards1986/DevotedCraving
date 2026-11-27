package ai.davoted.craving.ai

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.Locale

@Serializable
enum class ArchiveAuthenticityStatus { VERIFIED, UNVERIFIED, REJECTED }

@Serializable
enum class ArchiveAccessLevel { PUBLIC, LICENSED, USER_PROVIDED, UNKNOWN }

@Serializable
enum class ArchiveCategory {
    PHOTOGRAPH, VIDEO, INTERVIEW, CAREER_HISTORY, PUBLIC_APPEARANCE,
    EDITORIAL, AUDIO, BEHIND_THE_SCENES, OTHER
}

@Serializable
enum class ArchiveTheme { NEUTRAL, INTIMATE, DOMINANCE, CASUAL, CONFIDENT, PLAYFUL }

@Serializable
data class ArchiveSource(
    val sourceId: String,
    val url: String,
    val publisher: String? = null,
    val capturedAtMillis: Long,
    val accessLevel: ArchiveAccessLevel = ArchiveAccessLevel.UNKNOWN,
    val usageLicense: String = "unknown",
    val authenticityStatus: ArchiveAuthenticityStatus,
    val verifiedAtMillis: Long? = null
)

@Serializable
data class ArchiveFactClaim(
    val text: String,
    val sourceLocator: String,
    val topicTags: List<String> = emptyList()
)

@Serializable
data class ArchiveFactualContext(
    val summary: String = "",
    val claims: List<ArchiveFactClaim> = emptyList()
)

@Serializable
data class UserArchiveFraming(
    val theme: ArchiveTheme = ArchiveTheme.NEUTRAL,
    val direction: String = "",
    val userAuthored: Boolean = true
)

@Serializable
data class ArchiveContext(
    val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    val archiveItemId: String,
    val subject: String = "Natasha Nice",
    val title: String,
    val category: ArchiveCategory,
    val source: ArchiveSource,
    val factualContext: ArchiveFactualContext = ArchiveFactualContext(),
    val userFraming: UserArchiveFraming = UserArchiveFraming(),
    val selectedAtMillis: Long
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = "1.0.0"
    }
}

/** Structured exclusion keys should be supplied by Memory Service; strings are treated as literal veto terms. */
data class ArchiveMemoryState(
    val stateId: String,
    val confirmedPreferences: List<String> = emptyList(),
    val exclusions: List<String> = emptyList()
)

/** Persistent, model-independent controls. Defaults intentionally use the approved high-saturation baseline. */
data class ExperienceControls(
    val intensity: Int = 5,
    val intimacy: Int = 5,
    val playfulness: Int = 5,
    val initiative: Int = 5,
    val confidence: Int = 5
) {
    init {
        require(listOf(intensity, intimacy, playfulness, initiative, confidence).all { it in 0..5 })
    }
}

data class PresentationParameters(
    val intensity: Int,
    val intimacy: Int,
    val playfulness: Int,
    val initiative: Int,
    val confidence: Int
)

data class VerifiedArchiveFact(
    val text: String,
    val sourceLocator: String,
    val topicTags: List<String>
)

data class ArchiveProvenance(
    val archiveItemId: String,
    val sourceId: String,
    val sourceUrl: String,
    val publisher: String?,
    val capturedAtMillis: Long,
    val verifiedAtMillis: Long?,
    val usageLicense: String,
    val authenticityStatus: ArchiveAuthenticityStatus
)

data class AssembledArchiveExperience(
    val archiveItemId: String,
    val archiveFacts: List<VerifiedArchiveFact>,
    val provenance: ArchiveProvenance?,
    val userFraming: UserArchiveFraming?,
    val confirmedPreferences: List<String>,
    val exclusions: List<String>,
    val presentation: PresentationParameters,
    val characterState: ExperienceState,
    val memoryStateId: String,
    val isArchiveContextAvailable: Boolean = true,
    val framingVetoApplied: Boolean = false
)

sealed interface ArchiveTransformResult {
    data class Success(val experience: AssembledArchiveExperience) : ArchiveTransformResult
    data class Rejected(val reason: String) : ArchiveTransformResult
    data class NoArchiveContext(val reason: String) : ArchiveTransformResult
}

/**
 * Deterministic Archive Service -> PersonaOrchestrator transformation.
 * Only verified, source-located claims enter factual grounding. User framing remains
 * a creative instruction and cannot modify or become a factual claim about the performer.
 */
class PersonaOrchestrator {
    fun transformArchiveContext(
        context: ArchiveContext?,
        memory: ArchiveMemoryState,
        controls: ExperienceControls,
        personaState: ExperienceState
    ): ArchiveTransformResult {
        if (context == null) return ArchiveTransformResult.Success(
            failSafeExperience(memory, controls, personaState)
        )
        if (context.schemaVersion != ArchiveContext.CURRENT_SCHEMA_VERSION) {
            return ArchiveTransformResult.Rejected("Unsupported ArchiveContext schema version.")
        }
        if (context.archiveItemId.isBlank() || context.title.isBlank()) {
            return ArchiveTransformResult.Rejected("Archive item identity and title are required.")
        }
        if (context.subject != "Natasha Nice") {
            return ArchiveTransformResult.Rejected("Archive subject does not match the configured subject.")
        }
        if (context.source.sourceId.isBlank() || !isHttpUrl(context.source.url)) {
            return ArchiveTransformResult.Rejected("A source identifier and valid HTTP(S) source URL are required.")
        }
        if (context.source.authenticityStatus == ArchiveAuthenticityStatus.REJECTED) {
            return ArchiveTransformResult.Rejected("Archive item has been rejected as inauthentic.")
        }
        if (context.userFraming.userAuthored.not()) {
            return ArchiveTransformResult.Rejected("Thematic framing must be explicitly user-authored.")
        }

        val exclusions = memory.exclusions.map { it.normalizeTerm() }.filter { it.isNotBlank() }.distinct().sorted()
        val verified = context.source.authenticityStatus == ArchiveAuthenticityStatus.VERIFIED
        val facts = if (verified) {
            context.factualContext.claims
                .filter { it.text.isNotBlank() && it.sourceLocator.isNotBlank() }
                .filterNot { claim ->
                    val searchable = (claim.text + " " + claim.topicTags.joinToString(" ")).normalizeTerm()
                    exclusions.any { term -> term in searchable }
                }
                .map { VerifiedArchiveFact(it.text.trim(), it.sourceLocator.trim(), it.topicTags.map { tag -> tag.trim() }.filter { tag -> tag.isNotBlank() }.distinct().sorted()) }
                .sortedWith(compareBy(VerifiedArchiveFact::sourceLocator, VerifiedArchiveFact::text))
        } else {
            emptyList()
        }

        val theme = context.userFraming.theme
        val reconciledFraming = reconcileFraming(context.userFraming, exclusions)
        val presentation = PresentationParameters(
            intensity = (controls.intensity + theme.intensityModifier()).coerceIn(0, 5),
            intimacy = (controls.intimacy + theme.intimacyModifier()).coerceIn(0, 5),
            playfulness = (controls.playfulness + theme.playfulnessModifier()).coerceIn(0, 5),
            initiative = controls.initiative.coerceIn(0, 5),
            confidence = controls.confidence.coerceIn(0, 5)
        )

        val provenance = if (verified) {
            ArchiveProvenance(
                archiveItemId = context.archiveItemId,
                sourceId = context.source.sourceId,
                sourceUrl = context.source.url,
                publisher = context.source.publisher,
                capturedAtMillis = context.source.capturedAtMillis,
                verifiedAtMillis = context.source.verifiedAtMillis,
                usageLicense = context.source.usageLicense,
                authenticityStatus = context.source.authenticityStatus
            )
        } else null

        return ArchiveTransformResult.Success(
            AssembledArchiveExperience(
                archiveItemId = context.archiveItemId,
                archiveFacts = facts,
                provenance = provenance,
                userFraming = reconciledFraming.first,
                confirmedPreferences = memory.confirmedPreferences.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
                exclusions = memory.exclusions.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
                presentation = presentation,
                characterState = personaState,
                memoryStateId = memory.stateId,
                isArchiveContextAvailable = true,
                framingVetoApplied = reconciledFraming.second
            )
        )
    }

    /**
     * Null archive selection is a supported generation state, not a transformation failure.
     * The stable sentinel is an internal state marker only; it carries no source or archive claim.
     */
    private fun failSafeExperience(
        memory: ArchiveMemoryState,
        controls: ExperienceControls,
        personaState: ExperienceState
    ): AssembledArchiveExperience = AssembledArchiveExperience(
        archiveItemId = NO_ARCHIVE_ITEM_ID,
        archiveFacts = emptyList(),
        provenance = null,
        userFraming = null,
        confirmedPreferences = memory.confirmedPreferences.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
        exclusions = memory.exclusions.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
        presentation = PresentationParameters(
            intensity = controls.intensity,
            intimacy = controls.intimacy,
            playfulness = controls.playfulness,
            initiative = controls.initiative,
            confidence = controls.confidence
        ),
        characterState = personaState,
        memoryStateId = memory.stateId,
        isArchiveContextAvailable = false
    )

    /**
     * Conflicting framing is rewritten as a deterministic veto-aware directive.
     * The selected theme and creative intent are retained; excluded terms appear only
     * as explicit boundaries, never as requested content.
     */
    private fun reconcileFraming(
        framing: UserArchiveFraming,
        normalizedExclusions: List<String>
    ): Pair<UserArchiveFraming, Boolean> {
        val normalizedDirection = framing.direction.normalizeTerm()
        val matched = normalizedExclusions.filter { term ->
            term.isNotBlank() && (" $normalizedDirection ").contains(" $term ")
        }
        if (matched.isEmpty()) return framing to false

        val boundary = matched.joinToString("; ")
        return framing.copy(
            direction = "Preserve the user's remaining creative intent and selected ${framing.theme.name.lowercase(Locale.ROOT)} tone by focusing on other themes, given the explicit constraint against: $boundary. Do not include, imply, or reintroduce those excluded topics."
        ) to true
    }

    companion object {
        const val NO_ARCHIVE_ITEM_ID = "no-archive-context"
    }

    private fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun String.normalizeTerm(): String =
        lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun ArchiveTheme.intensityModifier(): Int = when (this) {
        ArchiveTheme.DOMINANCE, ArchiveTheme.INTIMATE -> 0
        else -> 0
    }

    private fun ArchiveTheme.intimacyModifier(): Int = when (this) {
        ArchiveTheme.INTIMATE -> 1
        else -> 0
    }

    private fun ArchiveTheme.playfulnessModifier(): Int = when (this) {
        ArchiveTheme.PLAYFUL -> 1
        else -> 0
    }
}
