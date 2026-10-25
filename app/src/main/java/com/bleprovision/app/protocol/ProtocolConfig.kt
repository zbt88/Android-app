package com.bleprovision.app.protocol

/**
 * ============================================================
 *  配网协议参数集中配置（后期你只需要修改这一个文件）
 * ============================================================
 *
 * 1. 把下面三个 UUID 改成你设备固件里实际使用的值。
 * 2. DEVICE_NAME_PREFIX 改成你设备蓝牙广播名的固定前缀，
 *    App 只会显示匹配该前缀的设备；留空("")则显示所有有名字的设备。
 * 3. 超时时间可按设备实际响应速度调整。
 */
object ProtocolConfig {

    /** 设备提供的配网服务 UUID（占位值，请改成你固件的 UUID） */
    const val SERVICE_UUID = "0000FFF0-0000-1000-8000-00805F9B34FB"

    /** App -> 设备 写特征 UUID */
    const val WRITE_CHAR_UUID = "0000FFF2-0000-1000-8000-00805F9B34FB"

    /** 设备 -> App 通知特征 UUID（Notify） */
    const val NOTIFY_CHAR_UUID = "0000FFF1-0000-1000-8000-00805F9B34FB"

    /** 连接后请求的 MTU 值；设备拒绝时自动回退到最小 23 */
    const val REQUEST_MTU = 247

    /** 单包最小安全长度（MTU 23 时可用负载为 20 字节） */
    const val MIN_CHUNK_SIZE = 20

    /** 设备蓝牙广播名前缀过滤，"" 表示不过滤 */
    const val DEVICE_NAME_PREFIX = ""

    /** 扫描时长（毫秒） */
    const val SCAN_DURATION_MS = 10_000L

    /** 等待设备返回热点列表的超时（毫秒） */
    const val WIFI_LIST_TIMEOUT_MS = 15_000L

    /** 等待设备返回配网结果的超时（毫秒） */
    const val RESULT_TIMEOUT_MS = 20_000L
}
