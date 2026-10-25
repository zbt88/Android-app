package com.bleprovision.app.protocol

/** 设备扫描到的热点 */
data class WifiApInfo(
    val ssid: String,
    val rssi: Int,
    val authRequired: Boolean
)

/** 配网结果 */
data class ProvisionResult(
    val success: Boolean,
    val code: Int,
    val message: String?
)

/** 设备通过蓝牙发回的事件 */
sealed class ProtocolEvent {
    data class WifiList(val list: List<WifiApInfo>) : ProtocolEvent()
    data class Result(val result: ProvisionResult) : ProtocolEvent()
}

/** 一次从接收缓冲中提取出的完整消息；consumedBytes 表示需从缓冲区头部丢弃的字节数 */
class ExtractedMessage(val consumedBytes: Int, val event: ProtocolEvent)

/**
 * 配网协议接口。
 *
 * App 的 UI 和蓝牙通信层只依赖这个接口。
 * 以后要换成你自己的二进制协议时，写一个新类实现本接口，
 * 并把 ProvisioningViewModel 里的 JsonProvisioningProtocol 换成你的实现即可，
 * 其他代码一行不用改。
 */
interface ProvisioningProtocol {
    /** 构造"请求设备扫描热点列表"命令 */
    fun encodeGetWifiList(): ByteArray

    /** 构造"下发 WiFi SSID 和密码"命令 */
    fun encodeSetWifi(ssid: String, password: String): ByteArray

    /**
     * 尝试从接收缓冲区中提取一条完整消息。
     * 成功返回 ExtractedMessage（含事件和已消费字节数）；
     * 数据还不完整则返回 null，等待下一包 notify 数据继续拼接。
     */
    fun tryExtractMessage(buffer: ByteArray): ExtractedMessage?
}
