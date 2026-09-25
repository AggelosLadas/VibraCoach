package com.aggelosladas.vibracoach.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aggelosladas.vibracoach.domain.ConnectionState
import com.aggelosladas.vibracoach.domain.CustomCommand
import com.aggelosladas.vibracoach.domain.PredeterminedPattern

private val AppBackground = Color(0xFF121212)
private val CardSurface = Color(0xFF1E1E1E)
private val CardBorder = Color(0xFF2C2C2C)
private val PrimaryAccent = Color(0xFF4CAF50)
private val SecondaryAccent = Color(0xFF2196F3)
private val DangerAccent = Color(0xFFF44336)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFAAAAAA)

/**
 * Three simple width buckets replace the old fixed 155dp adaptive grid.
 * A coach's phone is usually held in one hand (2 columns, big targets);
 * a tablet on a sideline stand gets more room to spread the 8 cubes out.
 */
private enum class DashboardSize { COMPACT, MEDIUM, EXPANDED }

private data class DashboardMetrics(
    val columns: Int,
    val cubeHeight: Dp,
    val cubeTitleSize: androidx.compose.ui.unit.TextUnit,
    val cubeLabelSize: androidx.compose.ui.unit.TextUnit,
    val cubeIconSize: Dp,
    val gridSpacing: Dp,
    val horizontalPadding: Dp,
    val topTitleSize: androidx.compose.ui.unit.TextUnit
)

private fun metricsFor(size: DashboardSize): DashboardMetrics = when (size) {
    DashboardSize.COMPACT -> DashboardMetrics(
        columns = 2,
        cubeHeight = 150.dp,
        cubeTitleSize = 20.sp,
        cubeLabelSize = 13.sp,
        cubeIconSize = 20.dp,
        gridSpacing = 14.dp,
        horizontalPadding = 16.dp,
        topTitleSize = 22.sp
    )
    DashboardSize.MEDIUM -> DashboardMetrics(
        columns = 3,
        cubeHeight = 180.dp,
        cubeTitleSize = 24.sp,
        cubeLabelSize = 14.sp,
        cubeIconSize = 22.dp,
        gridSpacing = 18.dp,
        horizontalPadding = 24.dp,
        topTitleSize = 24.sp
    )
    DashboardSize.EXPANDED -> DashboardMetrics(
        columns = 4,
        cubeHeight = 210.dp,
        cubeTitleSize = 28.sp,
        cubeLabelSize = 16.sp,
        cubeIconSize = 26.dp,
        gridSpacing = 22.dp,
        horizontalPadding = 32.dp,
        topTitleSize = 26.sp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isVibrating by viewModel.isVibrating.collectAsState()
    val isSimulatorMode by viewModel.isSimulatorMode.collectAsState()
    val commands by viewModel.commands.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isConnected = connectionState == ConnectionState.CONNECTED

    var editingCommand by remember { mutableStateOf<CustomCommand?>(null) }

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
            if (isSimulatorMode) {
                viewModel.connect()
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
    }

    LaunchedEffect(isVibrating) {
        if (isVibrating) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "VibraCoach",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                actions = {
                    Surface(
                        color = if (isSimulatorMode) Color(0xFF263238) else Color(0xFF1B5E20),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (isSimulatorMode) {
                                    viewModel.switchToBle(context)
                                } else {
                                    viewModel.switchToSimulator()
                                }
                            }
                    ) {
                        Text(
                            text = if (isSimulatorMode) "DEMO MODE" else "ESP32 BLE",
                            color = if (isSimulatorMode) Color(0xFF81D4FA) else Color(0xFFA5D6A7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val dashboardSize = when {
                maxWidth >= 840.dp -> DashboardSize.EXPANDED
                maxWidth >= 600.dp -> DashboardSize.MEDIUM
                else -> DashboardSize.COMPACT
            }
            val metrics = metricsFor(dashboardSize)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.horizontalPadding)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                DeviceStatusCard(
                    connectionState = connectionState,
                    isVibrating = isVibrating,
                    isSimulatorMode = isSimulatorMode,
                    compact = dashboardSize == DashboardSize.COMPACT,
                    onToggleConnection = { handleToggleConnection() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signal Grid",
                        color = TextPrimary,
                        fontSize = metrics.topTitleSize,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tap to send · hold to edit",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(metrics.columns),
                    horizontalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(metrics.gridSpacing),
                    modifier = Modifier.weight(1f)
                ) {
                    items(commands, key = { it.id }) { command ->
                        CommandCubeCard(
                            command = command,
                            enabled = isConnected,
                            metrics = metrics,
                            onClick = { viewModel.sendCommand(command) },
                            onEditClick = { editingCommand = command }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    editingCommand?.let { cmd ->
        EditCommandDialog(
            command = cmd,
            onDismiss = { editingCommand = null },
            onSave = { updatedName, updatedPattern ->
                viewModel.updateCommand(cmd.id, updatedName, updatedPattern)
                editingCommand = null
            }
        )
    }
}

@Composable
private fun DeviceStatusCard(
    connectionState: ConnectionState,
    isVibrating: Boolean,
    isSimulatorMode: Boolean,
    compact: Boolean,
    onToggleConnection: () -> Unit
) {
    val isConnected = connectionState == ConnectionState.CONNECTED

    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> PrimaryAccent
            ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFFB74D)
            else -> DangerAccent
        },
        label = "status_color"
    )

    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 16.dp else 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSimulatorMode) "Simulator Wearable" else "ESP32-C3 Wearable",
                        color = TextPrimary,
                        fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "Connected"
                                ConnectionState.CONNECTING -> "Connecting..."
                                ConnectionState.SCANNING -> "Scanning for ESP32..."
                                else -> "Disconnected"
                            },
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }

                // Minimum 48dp touch target height, per accessibility guidance -
                // this is the button a coach hits mid-session, it must never be fumbled.
                FilledTonalButton(
                    onClick = onToggleConnection,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isConnected) Color(0xFF2C2C2C) else PrimaryAccent,
                        contentColor = if (isConnected) TextPrimary else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> "Disconnect"
                            ConnectionState.CONNECTING, ConnectionState.SCANNING -> "Cancel"
                            else -> "Connect"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(visible = isConnected) {
                Column {
                    HorizontalDivider(
                        color = CardBorder,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Haptic Engine",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isVibrating) "Playing signal..." else "Idle",
                            color = if (isVibrating) PrimaryAccent else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isVibrating) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * The whole cube is the tap target for sending a command - a coach glancing
 * at the sideline shouldn't have to aim for a small icon. Editing moves to a
 * long-press on the cube itself, plus a small always-visible edit affordance
 * in the corner (>=44dp touch area) for anyone who prefers tapping it directly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandCubeCard(
    command: CustomCommand,
    enabled: Boolean,
    metrics: DashboardMetrics,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    val haptic = LocalHapticFeedback.current

    Surface(
        color = CardSurface.copy(alpha = alpha),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.cubeHeight)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEditClick()
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF2C2C2C),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "#${command.id}",
                        color = SecondaryAccent.copy(alpha = alpha),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${command.name}",
                        tint = TextSecondary.copy(alpha = alpha),
                        modifier = Modifier.size(metrics.cubeIconSize * 0.8f)
                    )
                }
            }

            Text(
                text = command.name,
                color = TextPrimary.copy(alpha = alpha),
                fontSize = metrics.cubeTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = PrimaryAccent.copy(alpha = alpha),
                    modifier = Modifier.size(metrics.cubeIconSize)
                )
                Text(
                    text = "${command.pattern.displayName} (${command.pattern.pulseCount} Pulse)",
                    color = TextSecondary.copy(alpha = alpha),
                    fontSize = metrics.cubeLabelSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EditCommandDialog(
    command: CustomCommand,
    onDismiss: () -> Unit,
    onSave: (String, PredeterminedPattern) -> Unit
) {
    var commandName by remember(command) { mutableStateOf(command.name) }
    var selectedPattern by remember(command) { mutableStateOf(command.pattern) }

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
                        focusedBorderColor = PrimaryAccent,
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
                            color = if (isSelected) Color(0xFF263238) else Color(0xFF161616),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryAccent else CardBorder
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
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryAccent)
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
                                    color = PrimaryAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${pattern.pulseCount} ${if (pattern.pulseCount == 1) "Pulse" else "Pulses"}",
                                        color = PrimaryAccent,
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
                onClick = { onSave(commandName.trim(), selectedPattern) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
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