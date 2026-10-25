# 蓝牙配网 App（BleProvision）

通过 BLE 给硬件设备配网的安卓 App：扫描蓝牙 → 选择设备 → 设备回传热点列表 → 输入密码 → 通过蓝牙下发 WiFi 信息 → 显示配网结果。

## 打包方式（无需本地安装任何环境）

项目已配置 GitHub Actions（`.github/workflows/build.yml`），推送到 GitHub 后自动编译，在 Actions 页面运行记录底部的 Artifacts 中下载 `app-debug-apk`（zip 压缩包，解压后是 APK）。

## 后期修改 UUID / 参数

全部集中在 `app/src/main/java/com/bleprovision/app/protocol/ProtocolConfig.kt`，文件内有中文注释说明每一项含义：服务/写/通知三个 UUID、MTU、设备名前缀过滤、各类超时。

## 更换自定义协议

协议通过 `ProvisioningProtocol` 接口抽象（`protocol/ProvisioningProtocol.kt`），当前默认实现为 `JsonProvisioningProtocol`。要换成自己的二进制协议：写一个新类实现该接口的三个方法，然后在 `ProvisioningViewModel.kt` 里把 `JsonProvisioningProtocol()` 换成你的实现即可，UI 和 BLE 层不用改。

## 当前 JSON 协议（固件端按此实现即可对接）

App → 设备（写到写特征）：

```json
{"cmd":"get_wifi_list"}
{"cmd":"set_wifi","ssid":"MyWiFi","password":"12345678"}
```

设备 → App（通知特征发 Notify）：

```json
{"event":"wifi_list","list":[{"ssid":"HomeWiFi","rssi":-58,"auth":true}]}
{"event":"result","code":0}
{"event":"result","code":1,"message":"密码错误"}
```

- `code` 为 0 表示配网成功，非 0 失败；失败时可附带 `message` 显示给用户。
- 每条通知为一个完整 JSON；多条连发也可以，App 按大括号配对自动切分。
- 超过 MTU 时 App 会自动分片发送；MTU 协商失败自动回退 20 字节分片。
- App 发出 `get_wifi_list` 后 15 秒、发出 `set_wifi` 后 20 秒收不到回复会提示超时。

## UI 流程

分步向导式（扫描 → 配置 → 完成），顶部有进度指示。权限在 Android 12+ 申请 BLUETOOTH_SCAN/CONNECT，12 以下申请定位权限。
