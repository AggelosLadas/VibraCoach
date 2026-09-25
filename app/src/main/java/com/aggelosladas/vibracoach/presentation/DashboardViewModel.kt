package com.aggelosladas.vibracoach.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggelosladas.vibracoach.bluetooth.Esp32BleDevice
import com.aggelosladas.vibracoach.domain.ConnectionState
import com.aggelosladas.vibracoach.domain.CustomCommand
import com.aggelosladas.vibracoach.domain.PredeterminedPattern
import com.aggelosladas.vibracoach.domain.WearableDevice
import com.aggelosladas.vibracoach.simulator.SimulatedWearableDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    initialDevice: WearableDevice
) : ViewModel() {

    private val currentDevice = MutableStateFlow(initialDevice)

    val connectionState: StateFlow<ConnectionState> = currentDevice
        .flatMapLatest { it.connectionState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    val isVibrating: StateFlow<Boolean> = currentDevice
        .flatMapLatest { it.isVibrating }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isSimulatorMode: StateFlow<Boolean> = currentDevice
        .map { it is SimulatedWearableDevice }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialDevice is SimulatedWearableDevice)

    private val defaultCommands = listOf(
        CustomCommand(1, "Whistle", PredeterminedPattern.PATTERN_1),
        CustomCommand(2, "Foul", PredeterminedPattern.PATTERN_2),
        CustomCommand(3, "Timeout", PredeterminedPattern.PATTERN_3),
        CustomCommand(4, "Defense", PredeterminedPattern.PATTERN_4),
        CustomCommand(5, "Offense", PredeterminedPattern.PATTERN_1),
        CustomCommand(6, "Substitution", PredeterminedPattern.PATTERN_2),
        CustomCommand(7, "Full Press", PredeterminedPattern.PATTERN_3),
        CustomCommand(8, "Zone Play", PredeterminedPattern.PATTERN_4)
    )

    private val _commands = MutableStateFlow(defaultCommands)
    val commands: StateFlow<List<CustomCommand>> = _commands.asStateFlow()

    fun updateCommand(id: Int, newName: String, newPattern: PredeterminedPattern) {
        _commands.update { list ->
            list.map { cmd ->
                if (cmd.id == id) {
                    cmd.copy(name = newName.ifBlank { "Command $id" }, pattern = newPattern)
                } else {
                    cmd
                }
            }
        }
    }

    fun sendCommand(command: CustomCommand) {
        viewModelScope.launch {
            currentDevice.value.sendCustomCommand(command.name, command.pattern)
        }
    }

    fun switchToSimulator() {
        viewModelScope.launch {
            currentDevice.value.disconnect()
            currentDevice.value = SimulatedWearableDevice()
        }
    }

    fun switchToBle(context: Context) {
        viewModelScope.launch {
            currentDevice.value.disconnect()
            currentDevice.value = Esp32BleDevice(context.applicationContext)
        }
    }

    fun connect() {
        viewModelScope.launch {
            currentDevice.value.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            currentDevice.value.disconnect()
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(Esp32BleDevice(context.applicationContext)) as T
                }
            }
    }
}
