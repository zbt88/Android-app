package com.bleprovision.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.bleprovision.app.protocol.ProtocolConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 扫描到的蓝牙设备 */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

/**
 * 扫描附近的 BLE 设备。
 * 按 ProtocolConfig.DEVICE_NAME_PREFIX 过滤（为空则显示所有有名字的设备），
 * 收集 ProtocolConfig.SCAN_DURATION_MS 后自动结束。
 */
@SuppressLint("MissingPermission")
fun scanDevices(context: Context): Flow<BleDevice> = callbackFlow {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val scanner = manager.adapter?.bluetoothLeScanner
    if (scanner == null) {
        close(IllegalStateException("蓝牙不可用，请检查蓝牙是否已开启"))
        return@callbackFlow
    }

    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name.isNullOrEmpty()) return
            val prefix = ProtocolConfig.DEVICE_NAME_PREFIX
            if (prefix.isNotBlank() && !name.startsWith(prefix)) return
            trySend(BleDevice(name, result.device.address, result.rssi))
        }

        override fun onScanFailed(errorCode: Int) {
            close(IllegalStateException("蓝牙扫描失败，错误码: $errorCode"))
        }
    }

    val filters = if (ProtocolConfig.DEVICE_NAME_PREFIX.isNotBlank()) {
        listOf(ScanFilter.Builder().setDeviceName(ProtocolConfig.DEVICE_NAME_PREFIX).build())
    } else {
        emptyList()
    }

    scanner.startScan(
        filters,
        ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
        callback
    )

    awaitClose {
        runCatching { scanner.stopScan(callback) }
    }
}
