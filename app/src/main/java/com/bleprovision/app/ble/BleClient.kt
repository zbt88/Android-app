package com.bleprovision.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import com.bleprovision.app.protocol.ProtocolConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "BleClient"
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

/**
 * BLE 客户端：负责连接、MTU 协商、订阅通知、按 MTU 分片写入。
 * 服务/特征 UUID 等参数见 ProtocolConfig。
 */
class BleClient(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private var writer: ChunkedWriter? = null

    private val _notify = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** 设备发来的通知数据（原始字节，由协议层负责解析） */
    val notify: SharedFlow<ByteArray> = _notify

    private val _connectionLost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 设备意外断开（主动断开时上层订阅已取消，不会误报） */
    val connectionLost: SharedFlow<Unit> = _connectionLost

    /**
     * 连接设备并完成：发现服务 -> MTU 协商 -> 订阅通知。
     * 成功返回协商后的 MTU；失败抛异常并自动断开。
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BleDevice): Int {
        disconnect()

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: throw IllegalStateException("蓝牙不可用，请检查蓝牙是否已开启")
        val remote = adapter.getRemoteDevice(device.address)

        val connected = CompletableDeferred<Unit>()
        val discovered = CompletableDeferred<Unit>()
        val mtuResult = CompletableDeferred<Int>()
        val descriptorWritten = CompletableDeferred<Unit>()
        var writerRef: ChunkedWriter? = null

        val callback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> connected.complete(Unit)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        val e = IllegalStateException(
                            if (status == 0) "设备连接已断开" else "连接失败（状态码 $status）"
                        )
                        connected.completeExceptionally(e)
                        discovered.completeExceptionally(e)
                        mtuResult.completeExceptionally(e)
                        descriptorWritten.completeExceptionally(e)
                        _connectionLost.tryEmit(Unit)
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    discovered.complete(Unit)
                } else {
                    discovered.completeExceptionally(
                        IllegalStateException("发现服务失败（状态码 $status）")
                    )
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                mtuResult.complete(if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23)
            }

            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    descriptorWritten.complete(Unit)
                } else {
                    descriptorWritten.completeExceptionally(
                        IllegalStateException("订阅通知失败（状态码 $status）")
                    )
                }
            }

            // Android 13+ 系统调用新回调
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                dispatchNotify(characteristic, value)
            }

            // Android 12 及以下系统调用旧回调
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (Build.VERSION.SDK_INT < 33) {
                    @Suppress("DEPRECATION")
                    dispatchNotify(characteristic, characteristic.value)
                }
            }

            // 写完成回调：只覆写旧签名即可。API 33+ 新回调的默认实现会向下委托到旧回调，
            // 因此旧回调在所有版本都会被调用（参考 Google 官方 platform-samples 的写法）
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                writerRef?.onWriteCompleted(status)
            }

            private fun dispatchNotify(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid.toString().equals(ProtocolConfig.NOTIFY_CHAR_UUID, ignoreCase = true)) {
                    _notify.tryEmit(value)
                }
            }
        }

        val g = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (g == null) {
            throw IllegalStateException("无法发起连接，请检查蓝牙是否已开启")
        }
        gatt = g

        try {
            withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }
            g.discoverServices()
            withTimeout(CONNECT_TIMEOUT_MS) { discovered.await() }

            val service = g.getService(UUID.fromString(ProtocolConfig.SERVICE_UUID))
                ?: throw IllegalStateException("设备上没有配网服务，请检查 ProtocolConfig.SERVICE_UUID 是否与固件一致")
            val notifyChar = service.getCharacteristic(UUID.fromString(ProtocolConfig.NOTIFY_CHAR_UUID))
                ?: throw IllegalStateException("设备上没有通知特征，请检查 ProtocolConfig.NOTIFY_CHAR_UUID")
            val writeChar = service.getCharacteristic(UUID.fromString(ProtocolConfig.WRITE_CHAR_UUID))
                ?: throw IllegalStateException("设备上没有写入特征，请检查 ProtocolConfig.WRITE_CHAR_UUID")

            g.requestMtu(ProtocolConfig.REQUEST_MTU)
            val mtu = withTimeout(CONNECT_TIMEOUT_MS) { mtuResult.await() }

            @Suppress("DEPRECATION")
            g.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(UUID.fromString(CCC_DESCRIPTOR_UUID))
                ?: throw IllegalStateException("通知特征缺少 CCCD 描述符，无法订阅通知")
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            if (!g.writeDescriptor(cccd)) {
                throw IllegalStateException("订阅通知请求发送失败")
            }
            withTimeout(CONNECT_TIMEOUT_MS) { descriptorWritten.await() }

            writerRef = ChunkedWriter(g, writeChar, (mtu - 3).coerceAtLeast(ProtocolConfig.MIN_CHUNK_SIZE))
            writer = writerRef
            return mtu
        } catch (e: TimeoutCancellationException) {
            disconnect()
            throw IllegalStateException("连接超时，请确保设备处于广播状态且未被其他手机占用")
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    /** 写数据（内部按 MTU 自动分片），返回 false 表示未连接或发送失败 */
    fun write(data: ByteArray): Boolean {
        val w = writer ?: return false
        return w.enqueue(data)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        writer = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
    }
}

/** 按 MTU 分片的写入器：Android GATT 要求上一笔写的完成回调返回后才能发起下一笔 */
private class ChunkedWriter(
    private val gatt: BluetoothGatt,
    private val characteristic: BluetoothGattCharacteristic,
    private val chunkSize: Int
) {
    private val queue = ArrayDeque<ByteArray>()
    private var sending = false

    @Synchronized
    fun enqueue(data: ByteArray): Boolean {
        var offset = 0
        do {
            val len = minOf(chunkSize, data.size - offset)
            queue.addLast(data.copyOfRange(offset, offset + len))
            offset += len
        } while (offset < data.size)
        return sendNext()
    }

    @Synchronized
    fun onWriteCompleted(status: Int) {
        sending = false
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "BLE 写入失败，状态码 $status，丢弃剩余分片")
            queue.clear()
        } else {
            sendNext()
        }
    }

    @Synchronized
    private fun sendNext(): Boolean {
        if (sending) return true
        val chunk = queue.removeFirstOrNull() ?: return true
        @Suppress("DEPRECATION")
        characteristic.value = chunk
        @Suppress("DEPRECATION")
        val ok = gatt.writeCharacteristic(characteristic)
        if (!ok) {
            Log.e(TAG, "writeCharacteristic 返回 false")
            queue.clear()
            return false
        }
        sending = true
        return true
    }
}
