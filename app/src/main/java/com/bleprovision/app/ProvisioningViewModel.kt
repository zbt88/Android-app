package com.bleprovision.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bleprovision.app.ble.BleClient
import com.bleprovision.app.ble.BleDevice
import com.bleprovision.app.ble.scanDevices
import com.bleprovision.app.protocol.JsonProvisioningProtocol
import com.bleprovision.app.protocol.ProtocolConfig
import com.bleprovision.app.protocol.ProtocolEvent
import com.bleprovision.app.protocol.ProvisioningProtocol
import com.bleprovision.app.protocol.WifiApInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class Step { SCAN, CONFIG, DONE }

data class UiState(
    val step: Step = Step.SCAN,
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val connectingName: String? = null,
    val scanError: String? = null,
    val devices: List<BleDevice> = emptyList(),
    val connectedDevice: BleDevice? = null,
    val wifiLoading: Boolean = false,
    val wifiError: String? = null,
    val wifiList: List<WifiApInfo> = emptyList(),
    val selectedSsid: String? = null,
    val provisioning: Boolean = false,
    val result: Boolean? = null,
    val provisionError: String? = null
)

class ProvisioningViewModel(application: Application) : AndroidViewModel(application) {

    private val client = BleClient(application)

    /** 以后更换协议：把 JsonProvisioningProtocol 换成你自己的 ProvisioningProtocol 实现即可 */
    private val protocol: ProvisioningProtocol = JsonProvisioningProtocol()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var notifyJob: Job? = null
    private var timeoutJob: Job? = null
    private val receiveBuffer = mutableListOf<Byte>()

    fun startScan() {
        if (_state.value.scanning) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true, scanError = null, devices = emptyList()) }
            try {
                withTimeoutOrNull(ProtocolConfig.SCAN_DURATION_MS) {
                    scanDevices(getApplication()).collect { dev ->
                        _state.update { st ->
                            val list = st.devices.filterNot { it.address == dev.address } + dev
                            st.copy(devices = list.sortedByDescending { it.rssi })
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(scanError = e.message ?: "扫描失败") }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = false) }
    }

    fun connectDevice(device: BleDevice) {
        if (_state.value.connecting || _state.value.connectedDevice != null) return
        scanJob?.cancel()
        notifyJob?.cancel()
        receiveBuffer.clear()
        viewModelScope.launch {
            _state.update { it.copy(connecting = true, connectingName = device.name, scanError = null) }
            try {
                client.connect(device)
                startNotifyLoop()
                _state.update {
                    it.copy(
                        connecting = false,
                        connectingName = null,
                        connectedDevice = device,
                        step = Step.CONFIG,
                        wifiLoading = true
                    )
                }
                requestWifiList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(connecting = false, connectingName = null, scanError = e.message ?: "连接失败")
                }
            }
        }
    }

    fun selectWifi(ap: WifiApInfo) {
        _state.update { it.copy(selectedSsid = ap.ssid) }
    }

    fun sendCredentials(ssid: String, password: String) {
        if (_state.value.provisioning) return
        armResultTimeout()
        _state.update { it.copy(provisioning = true, provisionError = null) }
        if (!client.write(protocol.encodeSetWifi(ssid, password))) {
            timeoutJob?.cancel()
            _state.update {
                it.copy(
                    provisioning = false,
                    result = false,
                    provisionError = "发送失败，蓝牙可能已断开",
                    step = Step.DONE
                )
            }
        }
    }

    /** 从完成页返回重新扫描 */
    fun resetToScan() {
        timeoutJob?.cancel()
        notifyJob?.cancel()
        receiveBuffer.clear()
        client.disconnect()
        _state.value = UiState()
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        notifyJob?.cancel()
        client.disconnect()
        super.onCleared()
    }

    private fun startNotifyLoop() {
        notifyJob?.cancel()
        receiveBuffer.clear()
        notifyJob = viewModelScope.launch {
            launch {
                client.connectionLost.collect {
                    timeoutJob?.cancel()
                    _state.update {
                        it.copy(
                            wifiLoading = false,
                            provisioning = false,
                            result = false,
                            provisionError = "蓝牙连接已断开",
                            step = Step.DONE
                        )
                    }
                }
            }
            client.notify.collect { chunk ->
                receiveBuffer.addAll(chunk.toList())
                var message = protocol.tryExtractMessage(receiveBuffer.toByteArray())
                while (message != null) {
                    receiveBuffer.subList(0, message.consumedBytes).clear()
                    handleEvent(message.event)
                    message = protocol.tryExtractMessage(receiveBuffer.toByteArray())
                }
            }
        }
    }

    private fun handleEvent(event: ProtocolEvent) {
        when (event) {
            is ProtocolEvent.WifiList -> {
                timeoutJob?.cancel()
                _state.update { it.copy(wifiLoading = false, wifiError = null, wifiList = event.list) }
            }
            is ProtocolEvent.Result -> {
                timeoutJob?.cancel()
                _state.update {
                    it.copy(
                        provisioning = false,
                        result = event.result.success,
                        provisionError = if (event.result.success) {
                            null
                        } else {
                            event.result.message ?: "设备返回错误码 ${event.result.code}"
                        },
                        step = Step.DONE
                    )
                }
            }
        }
    }

    private fun requestWifiList() {
        armWifiListTimeout()
        if (!client.write(protocol.encodeGetWifiList())) {
            timeoutJob?.cancel()
            _state.update { it.copy(wifiLoading = false, wifiError = "发送请求失败，蓝牙可能已断开") }
        }
    }

    private fun armWifiListTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(ProtocolConfig.WIFI_LIST_TIMEOUT_MS)
            _state.update {
                it.copy(
                    wifiLoading = false,
                    wifiError = "设备未返回热点列表，请确认固件已实现 get_wifi_list 命令并按协议返回"
                )
            }
        }
    }

    private fun armResultTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(ProtocolConfig.RESULT_TIMEOUT_MS)
            _state.update {
                it.copy(
                    provisioning = false,
                    result = false,
                    provisionError = "设备未在 ${ProtocolConfig.RESULT_TIMEOUT_MS / 1000} 秒内返回结果，请检查设备端",
                    step = Step.DONE
                )
            }
        }
    }
}
