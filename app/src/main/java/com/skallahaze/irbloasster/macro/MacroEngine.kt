package com.skallahaze.irbloasster.macro

import com.skallahaze.irbloasster.data.DiagnosticsLog
import com.skallahaze.irbloasster.model.MacroProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MacroEngine(
    private val log: DiagnosticsLog
) {
    data class Step(
        val label: String,
        val delayAfterMillis: Long = 0,
        val action: suspend () -> Result<Unit>
    )

    private val _progress = MutableStateFlow(MacroProgress())
    val progress: StateFlow<MacroProgress> = _progress.asStateFlow()

    suspend fun run(name: String, steps: List<Step>): Result<Unit> {
        if (_progress.value.running) {
            return Result.failure(IllegalStateException("Another scene is already running"))
        }

        _progress.value = MacroProgress(
            running = true,
            macroName = name,
            totalSteps = steps.size
        )
        log.info("Scene", "Starting scene: $name")

        for ((index, step) in steps.withIndex()) {
            _progress.value = _progress.value.copy(
                stepLabel = step.label,
                completedSteps = index
            )
            log.info("Scene", "${index + 1}/${steps.size}: ${step.label}")

            val result = runCatching { step.action() }.getOrElse { Result.failure(it) }
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "Unknown scene error"
                _progress.value = _progress.value.copy(
                    running = false,
                    lastError = message,
                    completedSteps = index
                )
                log.error("Scene", "$name failed at '${step.label}': $message")
                return Result.failure(result.exceptionOrNull() ?: IllegalStateException(message))
            }

            _progress.value = _progress.value.copy(completedSteps = index + 1)
            if (step.delayAfterMillis > 0) delay(step.delayAfterMillis)
        }

        _progress.value = _progress.value.copy(
            running = false,
            stepLabel = null,
            completedSteps = steps.size,
            lastError = null
        )
        log.info("Scene", "Scene completed: $name")
        return Result.success(Unit)
    }
}
