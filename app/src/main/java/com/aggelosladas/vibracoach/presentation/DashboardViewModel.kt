package com.aggelosladas.vibracoach.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aggelosladas.vibracoach.bluetooth.Esp32BleDevice
import com.aggelosladas.vibracoach.domain.ConnectionState
import com.aggelosladas.vibracoach.domain.CustomCommand
import com.aggelosladas.vibracoach.domain.PlayerBox
import com.aggelosladas.vibracoach.domain.PredeterminedPattern
import com.aggelosladas.vibracoach.domain.WearableDevice
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val device: WearableDevice
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = device.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)

    val isVibrating: StateFlow<Boolean> = device.isVibrating
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    private val allRoster = (1..12).map { id ->
        PlayerBox(id = id, name = "Player $id", number = "$id")
    }

    private val _allPlayers = MutableStateFlow(allRoster)
    private val _activePlayerIds = MutableStateFlow(listOf(1, 2, 3, 4, 5))
    private val _selectedPlayerIds = MutableStateFlow<Set<Int>>(emptySet())

    val activePlayers: StateFlow<List<PlayerBox>> = combine(_allPlayers, _activePlayerIds) { roster, activeIds ->
        activeIds.mapNotNull { id -> roster.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val benchPlayers: StateFlow<List<PlayerBox>> = combine(_allPlayers, _activePlayerIds) { roster, activeIds ->
        roster.filter { it.id !in activeIds }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedPlayerIds: StateFlow<Set<Int>> = _selectedPlayerIds.asStateFlow()

    fun togglePlayerSelection(playerId: Int) {
        _selectedPlayerIds.update { current ->
            if (playerId in current) current - playerId else current + playerId
        }
    }

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

    fun updatePlayer(id: Int, newName: String, newNumber: String) {
        _allPlayers.update { list ->
            list.map { p ->
                if (p.id == id) {
                    p.copy(
                        name = newName.ifBlank { "Player $id" },
                        number = newNumber.ifBlank { "$id" }
                    )
                } else {
                    p
                }
            }
        }
    }

    fun replaceActivePlayer(currentActiveId: Int, newBenchPlayerId: Int) {
        _activePlayerIds.update { activeList ->
            activeList.map { id ->
                if (id == currentActiveId) newBenchPlayerId else id
            }
        }
        _selectedPlayerIds.update { current ->
            (current - currentActiveId) + newBenchPlayerId
        }
    }

    fun sendCommand(command: CustomCommand) {
        viewModelScope.launch {
            device.sendCustomCommand(command.name, command.pattern)
            _selectedPlayerIds.value = emptySet()
        }
    }

    fun connect() {
        viewModelScope.launch {
            device.connect()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            device.disconnect()
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
