package com.aggelosladas.vibracoach.domain

data class VibrationPulse(
    val durationMs: Long,
    val pauseAfterMs: Long
)

data class VibrationPattern(
    val id: String,
    val name: String,
    val pulses: List<VibrationPulse>
)

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED
}

enum class PredeterminedPattern(
    val id: String,
    val displayName: String,
    val description: String,
    val keyword: String,
    val pulseCount: Int,
    val vibrationPattern: VibrationPattern
) {
    PATTERN_1(
        id = "p1",
        displayName = "Pattern 1",
        description = "1 Short Pulse",
        keyword = "pattern1",
        pulseCount = 1,
        vibrationPattern = VibrationPattern("p1", "Pattern 1", listOf(VibrationPulse(300, 0)))
    ),
    PATTERN_2(
        id = "p2",
        displayName = "Pattern 2",
        description = "2 Double Pulses",
        keyword = "pattern2",
        pulseCount = 2,
        vibrationPattern = VibrationPattern("p2", "Pattern 2", listOf(VibrationPulse(200, 150), VibrationPulse(200, 0)))
    ),
    PATTERN_3(
        id = "p3",
        displayName = "Pattern 3",
        description = "3 Triple Pulses",
        keyword = "pattern3",
        pulseCount = 3,
        vibrationPattern = VibrationPattern("p3", "Pattern 3", listOf(VibrationPulse(200, 100), VibrationPulse(200, 100), VibrationPulse(200, 0)))
    ),
    PATTERN_4(
        id = "p4",
        displayName = "Pattern 4",
        description = "4 Rapid Pulses",
        keyword = "pattern4",
        pulseCount = 4,
        vibrationPattern = VibrationPattern("p4", "Pattern 4", listOf(VibrationPulse(150, 80), VibrationPulse(150, 80), VibrationPulse(150, 80), VibrationPulse(150, 0)))
    )
}

data class CustomCommand(
    val id: Int,
    val name: String,
    val pattern: PredeterminedPattern
)

data class PlayerBox(
    val id: Int,
    val name: String,
    val number: String
)

object BasketballEvents {
    val WHISTLE = VibrationPattern(
        id = "evt_whistle",
        name = "Whistle",
        pulses = listOf(
            VibrationPulse(400, 100),
            VibrationPulse(400, 0)
        )
    )

    val FOUL = VibrationPattern(
        id = "evt_foul",
        name = "Foul",
        pulses = listOf(
            VibrationPulse(200, 150),
            VibrationPulse(200, 150),
            VibrationPulse(200, 0)
        )
    )

    val TIMEOUT = VibrationPattern(
        id = "evt_timeout",
        name = "Timeout",
        pulses = listOf(
            VibrationPulse(800, 200),
            VibrationPulse(800, 0)
        )
    )
}
