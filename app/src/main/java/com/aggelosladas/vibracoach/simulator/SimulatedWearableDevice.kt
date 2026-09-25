package com.aggelosladas.vibracoach.simulator

import com.aggelosladas.vibracoach.domain.ConnectionState
import com.aggelosladas.vibracoach.domain.PredeterminedPattern
import com.aggelosladas.vibracoach.domain.VibrationPattern
import com.aggelosladas.vibracoach.domain.WearableDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimulatedWearableDevice : WearableDevice {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var vibrationJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isVibrating = MutableStateFlow(false)
    override val isVibrating: StateFlow<Boolean> = _isVibrating.asStateFlow()

    override suspend fun connect() {
        _connectionState.value = ConnectionState.CONNECTING
        delay(1000)
        _connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        stopVibration()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendEvent(pattern: VibrationPattern) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        playPattern(pattern)
    }

    override suspend fun sendCustomCommand(commandName: String, pattern: PredeterminedPattern) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        playPattern(pattern.vibrationPattern)
    }

    private suspend fun playPattern(pattern: VibrationPattern) {
        stopVibration()

        vibrationJob = scope.launch {
            for (pulse in pattern.pulses) {
                if (!isActive) break

                _isVibrating.value = true
                delay(pulse.durationMs)

                if (!isActive) break

                _isVibrating.value = false
                if (pulse.pauseAfterMs > 0) {
                    delay(pulse.pauseAfterMs)
                }
            }
            _isVibrating.value = false
        }
    }

    override suspend fun stopVibration() {
        vibrationJob?.cancelAndJoin()
        _isVibrating.value = false
    }
}
