package com.bleprovision.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bleprovision.app.ui.DeviceAdapter
import com.bleprovision.app.ui.StepIndicatorView
import com.bleprovision.app.ui.WifiAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: ProvisioningViewModel by viewModels()

    private lateinit var stepIndicator: StepIndicatorView
    private lateinit var stepScan: View
    private lateinit var stepConfig: View
    private lateinit var stepDone: View
    private lateinit var scanHint: TextView
    private lateinit var scanError: TextView
    private lateinit var scanProgress: ProgressBar
    private lateinit var rescanButton: Button
    private lateinit var deviceList: RecyclerView
    private lateinit var connectedLabel: TextView
    private lateinit var wifiProgress: ProgressBar
    private lateinit var wifiError: TextView
    private lateinit var wifiList: RecyclerView
    private lateinit var selectedSsid: TextView
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordEdit: TextInputEditText
    private lateinit var sendButton: Button
    private lateinit var doneIcon: TextView
    private lateinit var doneMessage: TextView
    private lateinit var doneButton: Button

    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var wifiAdapter: WifiAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.all { it }) {
                viewModel.startScan()
            } else {
                Toast.makeText(this, "未授予所需权限，无法扫描蓝牙设备", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        deviceAdapter = DeviceAdapter { viewModel.connectDevice(it) }
        deviceList.layoutManager = LinearLayoutManager(this)
        deviceList.adapter = deviceAdapter

        wifiAdapter = WifiAdapter { viewModel.selectWifi(it) }
        wifiList.layoutManager = LinearLayoutManager(this)
        wifiList.adapter = wifiAdapter

        rescanButton.setOnClickListener { requestPermissionsThen { viewModel.startScan() } }
        sendButton.setOnClickListener { onSendClicked() }
        doneButton.setOnClickListener {
            viewModel.resetToScan()
            viewModel.startScan()
        }

        observeState()

        // 首次进入自动申请权限并开始扫描
        requestPermissionsThen { viewModel.startScan() }
    }

    private fun bindViews() {
        stepIndicator = findViewById(R.id.stepIndicator)
        stepScan = findViewById(R.id.stepScan)
        stepConfig = findViewById(R.id.stepConfig)
        stepDone = findViewById(R.id.stepDone)
        scanHint = findViewById(R.id.scanHint)
        scanError = findViewById(R.id.scanError)
        scanProgress = findViewById(R.id.scanProgress)
        rescanButton = findViewById(R.id.rescanButton)
        deviceList = findViewById(R.id.deviceList)
        connectedLabel = findViewById(R.id.connectedLabel)
        wifiProgress = findViewById(R.id.wifiProgress)
        wifiError = findViewById(R.id.wifiError)
        wifiList = findViewById(R.id.wifiList)
        selectedSsid = findViewById(R.id.selectedSsid)
        passwordLayout = findViewById(R.id.passwordLayout)
        passwordEdit = findViewById(R.id.passwordEdit)
        sendButton = findViewById(R.id.sendButton)
        doneIcon = findViewById(R.id.doneIcon)
        doneMessage = findViewById(R.id.doneMessage)
        doneButton = findViewById(R.id.doneButton)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(s: UiState) {
        stepIndicator.step = when (s.step) {
            Step.SCAN -> 1
            Step.CONFIG -> 2
            Step.DONE -> 3
        }
        stepScan.visibility = if (s.step == Step.SCAN) View.VISIBLE else View.GONE
        stepConfig.visibility = if (s.step == Step.CONFIG) View.VISIBLE else View.GONE
        stepDone.visibility = if (s.step == Step.DONE) View.VISIBLE else View.GONE

        // 步骤 1：扫描
        scanProgress.visibility = if (s.scanning || s.connecting) View.VISIBLE else View.GONE
        scanHint.text = when {
            s.connecting -> "正在连接 ${s.connectingName ?: "设备"} …"
            s.scanning -> "正在扫描附近的设备…"
            s.devices.isEmpty() && s.scanError == null -> "未发现设备，点击下方按钮重新扫描"
            else -> "点击设备开始配网"
        }
        scanError.visibility = if (s.scanError != null) View.VISIBLE else View.GONE
        scanError.text = s.scanError
        rescanButton.isEnabled = !s.scanning && !s.connecting
        deviceAdapter.submit(s.devices)

        // 步骤 2：配置
        val dev = s.connectedDevice
        connectedLabel.text = if (dev != null) "已连接：${dev.name.ifEmpty { dev.address }}" else ""
        wifiProgress.visibility = if (s.wifiLoading) View.VISIBLE else View.GONE
        wifiError.visibility = if (s.wifiError != null) View.VISIBLE else View.GONE
        wifiError.text = s.wifiError
        wifiAdapter.submit(s.wifiList, s.selectedSsid)
        selectedSsid.visibility = if (s.selectedSsid != null) View.VISIBLE else View.GONE
        selectedSsid.text = "已选热点：${s.selectedSsid}"
        sendButton.isEnabled = !s.provisioning && s.selectedSsid != null
        sendButton.text = if (s.provisioning) "正在配网…" else getString(R.string.connect_device)
        passwordLayout.isEnabled = !s.provisioning

        // 步骤 3：完成
        if (s.result == true) {
            doneIcon.text = "✓"
            doneIcon.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            doneMessage.text = "设备已成功联网！"
            doneButton.text = "再配一台"
        } else {
            doneIcon.text = "✗"
            doneIcon.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            doneMessage.text = s.provisionError ?: "配网失败"
            doneButton.text = getString(R.string.retry)
        }
    }

    private fun onSendClicked() {
        val s = viewModel.state.value
        val ssid = s.selectedSsid
        val password = passwordEdit.text?.toString().orEmpty()
        when {
            ssid == null -> Toast.makeText(this, "请先选择要连接的热点", Toast.LENGTH_SHORT).show()
            password.isEmpty() -> Toast.makeText(this, "请输入 WiFi 密码", Toast.LENGTH_SHORT).show()
            else -> viewModel.sendCredentials(ssid, password)
        }
    }

    private fun requestPermissionsThen(action: () -> Unit) {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
