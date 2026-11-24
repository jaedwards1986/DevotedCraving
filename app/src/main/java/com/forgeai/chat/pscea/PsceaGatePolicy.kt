package com.forgeai.chat.pscea

/**
 * Preregistered PSCEA engineering ceilings for the Pixel 9 Pro XL evaluation profile.
 *
 * These are acceptance limits, not claims about measured device performance. They
 * must be reviewed against repeated on-device baseline runs before release claims.
 * Sustained decode speed is a hard floor; the quick-response target is informational.
 */
object PsceaGateThresholds {
    const val MIN_SUSTAINED_TOKENS_PER_SECOND = 7.0
    const val QUICK_RESPONSE_TARGET_TOKENS_PER_SECOND = 15.0
    const val MAX_TIME_TO_FIRST_TOKEN_MS = 30_000L
    const val MAX_PROCESS_PSS_BYTES = 8L * 1024L * 1024L * 1024L
    val MAX_THERMAL_STATUS = PsceaThermalStatus.MODERATE
}

data class PsceaGateEvaluation(
    val survival: PsceaGateState,
    val stability: PsceaGateState,
    val performance: PsceaGateState,
    val reasons: Map<PsceaGate, String>
)

/**
 * Missing inputs remain NOT_MEASURED. A known threshold violation fails its gate.
 * This policy does not infer token counts or decode throughput from text chunks.
 */
object PsceaGatePolicy {
    fun evaluate(
        rawOutputText: String,
        measurements: PsceaRuntimeMeasurements
    ): PsceaGateEvaluation {
        val reasons = linkedMapOf<PsceaGate, String>()
        val survival = if (rawOutputText.isNotBlank()) {
            PsceaGateState.PASS
        } else {
            reasons[PsceaGate.SURVIVAL] = "Generation produced no usable output."
            PsceaGateState.FAIL
        }

        val pss = measurements.processPssBytes
        val thermal = measurements.thermalSnapshot
        val stability = when {
            pss != null && pss > PsceaGateThresholds.MAX_PROCESS_PSS_BYTES -> {
                reasons[PsceaGate.STABILITY] = "Process PSS exceeded the 8 GiB ceiling."
                PsceaGateState.FAIL
            }
            thermal?.throttlingObserved == true -> {
                reasons[PsceaGate.STABILITY] = "Thermal throttling was reported by the platform."
                PsceaGateState.FAIL
            }
            thermal != null && thermal.status.ordinal > PsceaGateThresholds.MAX_THERMAL_STATUS.ordinal -> {
                reasons[PsceaGate.STABILITY] = "Thermal status exceeded MODERATE."
                PsceaGateState.FAIL
            }
            pss == null || thermal == null -> {
                reasons[PsceaGate.STABILITY] = "PSS and thermal status are both required."
                PsceaGateState.NOT_MEASURED
            }
            else -> PsceaGateState.PASS
        }

        val ttft = measurements.timeToFirstTokenMs
        val throughput = measurements.sustainedTokensPerSecond
        val performance = when {
            ttft != null && ttft > PsceaGateThresholds.MAX_TIME_TO_FIRST_TOKEN_MS -> {
                reasons[PsceaGate.PERFORMANCE] = "Time to first token exceeded 30 seconds."
                PsceaGateState.FAIL
            }
            throughput != null && throughput < PsceaGateThresholds.MIN_SUSTAINED_TOKENS_PER_SECOND -> {
                reasons[PsceaGate.PERFORMANCE] = "Sustained decode speed fell below 7 tokens/second."
                PsceaGateState.FAIL
            }
            ttft == null || throughput == null -> {
                reasons[PsceaGate.PERFORMANCE] = "TTFT and measured sustained decode speed are required."
                PsceaGateState.NOT_MEASURED
            }
            else -> PsceaGateState.PASS
        }

        return PsceaGateEvaluation(survival, stability, performance, reasons)
    }
}
