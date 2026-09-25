package com.aggelosladas.vibracoach.domain

import kotlinx.coroutines.flow.StateFlow

interface WearableDevice {
    val connectionState: StateFlow<ConnectionState>
    val isVibrating: StateFlow<Boolean>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendEvent(pattern: VibrationPattern)
    suspend fun sendCustomCommand(commandName: String, pattern: PredeterminedPattern)
    suspend fun stopVibration()
}
