package com.aggelosladas.vibracoach.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aggelosladas.vibracoach.domain.ConnectionState
import com.aggelosladas.vibracoach.domain.CustomCommand
import com.aggelosladas.vibracoach.domain.PlayerBox
import com.aggelosladas.vibracoach.domain.PredeterminedPattern

private val AppBackground = Color(0xFFF8F9FA)
private val CardSurface = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE2E8F0)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)

private val PresetColors = listOf(
    0xFF059669, // Forest Emerald
    0xFF2563EB, // Royal Blue
    0xFFEA580C, // Coral Amber
    0xFFC026D3, // Mulberry Magenta
    0xFF7C3AED, // Deep Violet
    0xFF0E7490, // Slate Teal
    0xFFDC2626, // Crimson Ruby
    0xFF334155  // Midnight Slate
)

private data class DashboardMetrics(
    val cols: Int,
    val isTablet: Boolean,
    val connectionBarPaddingV: Dp,
    val titleFontSize: TextUnit,
    val sectionHeaderFontSize: TextUnit,
    val commandTitleFontSize: TextUnit,
    val commandSubFontSize: TextUnit,
    val commandPaddingV: Dp,
    val commandPaddingH: Dp,
    val playerCardHeight: Dp,
    val playerNumberFontSize: TextUnit,
    val editIconSize: Dp,
    val gridSpacing: Dp,
    val outerPadding: Dp
)

private fun getMetrics(maxWidth: Dp, maxHeight: Dp): DashboardMetrics {
    val isLandscape = maxWidth > maxHeight
    val width = maxWidth

    return when {
        width >= 840.dp -> DashboardMetrics(
            cols = 4,
            isTablet = true,
            connectionBarPaddingV = 8.dp,
            titleFontSize = 18.sp,
            sectionHeaderFontSize = 13.sp,
            commandTitleFontSize = 16.sp,
            commandSubFontSize = 11.sp,
            commandPaddingV = 8.dp,
            commandPaddingH = 10.dp,
            playerCardHeight = 165.dp,
            playerNumberFontSize = 52.sp,
            editIconSize = 22.dp,
            gridSpacing = 8.dp,
            outerPadding = 12.dp
        )
        width >= 600.dp -> DashboardMetrics(
            cols = 4,
            isTablet = true,
            connectionBarPaddingV = 6.dp,
            titleFontSize = 16.sp,
            sectionHeaderFontSize = 12.sp,
            commandTitleFontSize = 15.sp,
            commandSubFontSize = 10.sp,
            commandPaddingV = 6.dp,
            commandPaddingH = 8.dp,
            playerCardHeight = 140.dp,
            playerNumberFontSize = 44.sp,
            editIconSize = 20.dp,
            gridSpacing = 6.dp,
            outerPadding = 10.dp
        )
        else -> DashboardMetrics(
            cols = if (isLandscape) 4 else 2,
            isTablet = false,
            connectionBarPaddingV = 3.dp,
            titleFontSize = 14.sp,
            sectionHeaderFontSize = 10.sp,
            commandTitleFontSize = if (isLandscape) 13.sp else 12.sp,
            commandSubFontSize = 9.sp,
            commandPaddingV = 3.dp,
            commandPaddingH = 6.dp,
            playerCardHeight = if (isLandscape) 110.dp else 105.dp,
            playerNumberFontSize = 34.sp,
            editIconSize = 18.dp,
            gridSpacing = 4.dp,
            outerPadding = 8.dp
        )
    }
}

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isVibrating by viewModel.isVibrating.collectAsState()
    val commands by viewModel.commands.collectAsState()
    val activePlayers by viewModel.activePlayers.collectAsState()
    val benchPlayers by viewModel.benchPlayers.collectAsState()
    val selectedPlayerIds by viewModel.selectedPlayerIds.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isConnected = connectionState == ConnectionState.CONNECTED

    var editingCommand by remember { mutableStateOf<CustomCommand?>(null) }
    var editingPlayer by remember { mutableStateOf<PlayerBox?>(null) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            viewModel.connect()
        } else {
            Toast.makeText(context, "Bluetooth permissions required to connect to ESP32", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleToggleConnection() {
        if (isConnected || connectionState == ConnectionState.SCANNING || connectionState == ConnectionState.CONNECTING) {
            viewModel.disconnect()
        } else {
            val hasPermissions = requiredPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermissions) {
                viewModel.connect()
            } else {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }

    LaunchedEffect(isVibrating) {
        if (isVibrating) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Surface(
        color = AppBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            val metrics = getMetrics(maxWidth, maxHeight)
            val commandRows = commands.chunked(metrics.cols)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(metrics.outerPadding),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                CompactConnectionBar(
                    connectionState = connectionState,
                    isVibrating = isVibrating,
                    metrics = metrics,
                    onToggleConnection = { handleToggleConnection() }
                )

                Spacer(modifier = Modifier.height(metrics.gridSpacing))

                Text(
                    text = "TACTICAL SIGNALS (8 CUBES)",
                    color = TextSecondary,
                    fontSize = metrics.sectionHeaderFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(metrics.gridSpacing / 2))

                Column(
                    verticalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
                    modifier = Modifier.weight(1f)
                ) {
                    commandRows.forEach { rowCommands ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            rowCommands.forEach { command ->
                                CommandCubeCard(
                                    command = command,
                                    enabled = isConnected,
                                    metrics = metrics,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onClick = { viewModel.sendCommand(command) },
                                    onEditClick = { editingCommand = command }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(metrics.gridSpacing))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE PLAYERS ON COURT (5)",
                        color = TextSecondary,
                        fontSize = metrics.sectionHeaderFontSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Tap to select · tap pencil to edit",
                        color = TextSecondary,
                        fontSize = (metrics.sectionHeaderFontSize.value * 0.9f).sp
                    )
                }

                Spacer(modifier = Modifier.height(metrics.gridSpacing / 2))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.playerCardHeight)
                ) {
                    activePlayers.forEach { player ->
                        val isSelected = player.id in selectedPlayerIds
                        PlayerCubeCard(
                            player = player,
                            isSelected = isSelected,
                            metrics = metrics,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onToggle = { viewModel.togglePlayerSelection(player.id) },
                            onEditClick = { editingPlayer = player }
                        )
                    }
                }
            }
        }
    }

    editingCommand?.let { cmd ->
        EditCommandDialog(
            command = cmd,
            onDismiss = { editingCommand = null },
            onSave = { updatedName, updatedPattern, updatedColorHex ->
                viewModel.updateCommand(cmd.id, updatedName, updatedPattern, updatedColorHex)
                editingCommand = null
            }
        )
    }

    editingPlayer?.let { player ->
        EditPlayerDialog(
            player = player,
            benchPlayers = benchPlayers,
            onDismiss = { editingPlayer = null },
            onSaveNameNumberAndColor = { updatedName, updatedNumber, updatedColorHex ->
                viewModel.updatePlayer(player.id, updatedName, updatedNumber, updatedColorHex)
                editingPlayer = null
            },
            onSubstitute = { newBenchPlayerId ->
                viewModel.replaceActivePlayer(player.id, newBenchPlayerId)
                editingPlayer = null
            }
        )
    }
}

@Composable
private fun CompactConnectionBar(
    connectionState: ConnectionState,
    isVibrating: Boolean,
    metrics: DashboardMetrics,
    onToggleConnection: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = metrics.connectionBarPaddingV),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "VibraCoach",
                    color = TextPrimary,
                    fontSize = metrics.titleFontSize,
                    fontWeight = FontWeight.ExtraBold
                )

                val (statusText, statusBg) = when (connectionState) {
                    ConnectionState.CONNECTED -> Pair("CONNECTED", Color(0xFF059669))
                    ConnectionState.CONNECTING -> Pair("CONNECTING...", Color(0xFFD97706))
                    ConnectionState.SCANNING -> Pair("SCANNING...", Color(0xFFD97706))
                    else -> Pair("DISCONNECTED", Color(0xFF64748B))
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = if (metrics.isTablet) 11.sp else 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (isVibrating) {
                    Surface(
                        color = Color(0xFF0284C7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "VIBRATING",
                            color = Color.White,
                            fontSize = if (metrics.isTablet) 11.sp else 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Button(
                onClick = onToggleConnection,
                contentPadding = PaddingValues(
                    horizontal = if (metrics.isTablet) 14.dp else 10.dp,
                    vertical = 2.dp
                ),
                modifier = Modifier.height(if (metrics.isTablet) 34.dp else 28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(0xFF0F172A) else Color(0xFF2563EB),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Disconnect"
                        ConnectionState.CONNECTING, ConnectionState.SCANNING -> "Cancel"
                        else -> "Connect"
                    },
                    fontSize = if (metrics.isTablet) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandCubeCard(
    command: CustomCommand,
    enabled: Boolean,
    metrics: DashboardMetrics,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    val haptic = LocalHapticFeedback.current
    val cardColor = Color(command.colorHex)

    Surface(
        color = cardColor.copy(alpha = alpha),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (enabled) 3.dp else 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEditClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.commandPaddingH, vertical = metrics.commandPaddingV),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (metrics.isTablet) 26.dp else 20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(onClick = onEditClick)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit ${command.name}",
                    tint = Color.White,
                    modifier = Modifier.size(metrics.editIconSize)
                )
            }

            Text(
                text = command.name,
                color = Color.White,
                fontSize = metrics.commandTitleFontSize,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerCubeCard(
    player: PlayerBox,
    isSelected: Boolean,
    metrics: DashboardMetrics,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onEditClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val cardColor = Color(player.colorHex)

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (isSelected) 6.dp else 2.dp,
        border = if (isSelected) BorderStroke(4.dp, Color(0xFF0F172A)) else null,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onToggle,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEditClick()
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (metrics.isTablet) 40.dp else 34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(onClick = onEditClick)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit Player #${player.number}",
                    tint = Color.White,
                    modifier = Modifier.size(metrics.editIconSize)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                    )
                }
                Text(
                    text = "#${player.number}",
                    color = Color.White,
                    fontSize = metrics.playerNumberFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    selectedColorHex: Long,
    onColorSelected: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PresetColors.forEach { colorHex ->
            val color = Color(colorHex)
            val isSelected = colorHex == selectedColorHex

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color(0xFF0F172A) else CardBorder,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorHex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCommandDialog(
    command: CustomCommand,
    onDismiss: () -> Unit,
    onSave: (String, PredeterminedPattern, Long) -> Unit
) {
    var commandName by remember(command) { mutableStateOf(command.name) }
    var selectedPattern by remember(command) { mutableStateOf(command.pattern) }
    var selectedColorHex by remember(command) { mutableStateOf(command.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(
                text = "Customize Cube #${command.id}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Button Color",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                ColorPickerRow(
                    selectedColorHex = selectedColorHex,
                    onColorSelected = { selectedColorHex = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Command Name",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = commandName,
                    onValueChange = { commandName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(selectedColorHex),
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Assign Vibration Pattern (1 of 4)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                ) {
                    PredeterminedPattern.entries.forEach { pattern ->
                        val isSelected = pattern == selectedPattern

                        Surface(
                            color = if (isSelected) Color(0xFFF1F5F9) else Color(0xFFFAFAFA),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(selectedColorHex) else CardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(
                                    selected = isSelected,
                                    onClick = { selectedPattern = pattern },
                                    role = Role.RadioButton
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(selectedColorHex))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = pattern.displayName,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = pattern.description,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Surface(
                                    color = Color(selectedColorHex).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${pattern.pulseCount} ${if (pattern.pulseCount == 1) "Pulse" else "Pulses"}",
                                        color = Color(selectedColorHex),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(commandName.trim(), selectedPattern, selectedColorHex) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(selectedColorHex),
                    contentColor = Color.White
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditPlayerDialog(
    player: PlayerBox,
    benchPlayers: List<PlayerBox>,
    onDismiss: () -> Unit,
    onSaveNameNumberAndColor: (String, String, Long) -> Unit,
    onSubstitute: (Int) -> Unit
) {
    var playerName by remember(player) { mutableStateOf(player.name) }
    var playerNumber by remember(player) { mutableStateOf(player.number) }
    var selectedColorHex by remember(player) { mutableStateOf(player.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(
                text = "Edit Active Player #${player.id}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Player Button Color",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                ColorPickerRow(
                    selectedColorHex = selectedColorHex,
                    onColorSelected = { selectedColorHex = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Player Name",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(selectedColorHex),
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Jersey Number",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = playerNumber,
                    onValueChange = { playerNumber = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(selectedColorHex),
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = CardBorder)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Replace / Substitute Player",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select a bench player to swap into this slot:",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (benchPlayers.isEmpty()) {
                        Text(
                            text = "No bench players available",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        benchPlayers.forEach { benchPlayer ->
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSubstitute(benchPlayer.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = Color(benchPlayer.colorHex),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "#${benchPlayer.number}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = benchPlayer.name,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Text(
                                        text = "Swap",
                                        color = Color(0xFF0284C7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveNameNumberAndColor(playerName.trim(), playerNumber.trim(), selectedColorHex) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(selectedColorHex),
                    contentColor = Color.White
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("Cancel")
            }
        }
    )
}
