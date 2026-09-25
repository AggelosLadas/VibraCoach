package com.aggelosladas.vibracoach.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.aggelosladas.vibracoach.domain.ConnectionState
import com.aggelosladas.vibracoach.domain.PredeterminedPattern
import com.aggelosladas.vibracoach.domain.VibrationPattern
import com.aggelosladas.vibracoach.domain.WearableDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class Esp32BleDevice(
    private val context: Context
) : WearableDevice {

    companion object {
        private const val TAG = "Esp32BleDevice"
        val SERVICE_UUID: UUID = UUID.fromString("19B10000-E0C5-4515-A513-00592D0B64F0")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("19B10001-E0C5-4515-A513-00592D0B64F0")
        private const val DEVICE_NAME = "VibraCoach"
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var vibrationJob: Job? = null
    private var scanTimeoutJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isVibrating = MutableStateFlow(false)
    override val isVibrating: StateFlow<Boolean> = _isVibrating.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val deviceName = try { device.name } catch (_: SecurityException) { null }

            Log.d(TAG, "Discovered device: ${device.address} - $deviceName")

            if (deviceName == DEVICE_NAME || hasMatchingService(result)) {
                stopScan()
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with code: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun hasMatchingService(result: ScanResult?): Boolean {
        val uuids = result?.scanRecord?.serviceUuids ?: return false
        return uuids.contains(ParcelUuid(SERVICE_UUID))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTING
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                targetCharacteristic = null
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(SERVICE_UUID)
                targetCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

                if (targetCharacteristic != null) {
                    Log.d(TAG, "GATT connected & service ready")
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    Log.e(TAG, "Target characteristic not found")
                    disconnectInternal()
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                disconnectInternal()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled or not available")
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.SCANNING
        ) {
            return
        }

        _connectionState.value = ConnectionState.SCANNING

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }

            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            Log.d(TAG, "Starting BLE scan...")
            scanner.startScan(filters, settings, scanCallback)

            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(10000)
                if (_connectionState.value == ConnectionState.SCANNING) {
                    Log.d(TAG, "Scan with filter timed out. Trying scan without filter...")
                    stopScan()
                    try {
                        scanner.startScan(null, settings, scanCallback)
                        delay(10000)
                        if (_connectionState.value == ConnectionState.SCANNING) {
                            Log.w(TAG, "Scan timed out without finding device.")
                            stopScan()
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Permission missing for BLE scan", e)
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth permissions", e)
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        scanTimeoutJob?.cancel()
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to stop scan", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.CONNECTING
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to connect Gatt", e)
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun disconnect() {
        disconnectInternal()
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal() {
        stopScan()
        stopVibrationInternal()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission on disconnect", e)
        } finally {
            bluetoothGatt = null
            targetCharacteristic = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun sendCustomCommand(commandName: String, pattern: PredeterminedPattern) {
        val payload = "$commandName|${pattern.keyword}"
        writePayloadToGatt(payload, pattern.vibrationPattern)
    }

    override suspend fun sendEvent(pattern: VibrationPattern) {
        val payload = pattern.name
        writePayloadToGatt(payload, pattern)
    }

    @SuppressLint("MissingPermission")
    private fun writePayloadToGatt(payload: String, pattern: VibrationPattern) {
        val gatt = bluetoothGatt
        val chara = targetCharacteristic

        if (_connectionState.value != ConnectionState.CONNECTED || gatt == null || chara == null) {
            Log.w(TAG, "Cannot send event: Not connected to ESP32")
            return
        }

        val payloadBytes = payload.toByteArray(Charsets.UTF_8)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    chara,
                    payloadBytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
            } else {
                @Suppress("DEPRECATION")
                chara.value = payloadBytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(chara)
            }
            Log.d(TAG, "Sent payload to ESP32: $payload")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to write characteristic", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing payload to characteristic", e)
        }

        stopVibrationInternal()
        vibrationJob = scope.launch {
            val totalDurationMs = pattern.pulses.sumOf { it.durationMs + it.pauseAfterMs }
            _isVibrating.value = true
            delay(totalDurationMs)
            _isVibrating.value = false
        }
    }

    override suspend fun stopVibration() {
        stopVibrationInternal()
    }

    private fun stopVibrationInternal() {
        vibrationJob?.cancel()
        _isVibrating.value = false
    }
}
