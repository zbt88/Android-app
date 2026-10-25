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

    /**
     * 优先排列的设备名前缀：名称以它开头（如 "DVT_"）的设备排在扫描列表最前面
     * （组内仍按信号强度排序），其余设备排在后面；"" 表示关闭该功能。
     */
    const val PRIORITY_NAME_PREFIX = "DVT_"

    /** 点击了非本设备（没有配网服务/特征的设备）时，屏幕显示的提示信息 */
    const val UNMATCHED_DEVICE_MSG = "非指定设备，请选择正确设备蓝牙名称连接。"

    /** 设备没有按时返回热点列表时，屏幕显示的提示信息 */
    const val WIFI_LIST_TIMEOUT_MSG = "设备未返回热点列表，请重新获取"

    /** 扫描时长（毫秒） */
    const val SCAN_DURATION_MS = 10_000L

    /**
     * 蓝牙连接超时（毫秒）：
     * 点击设备后，完成「连接 -> 发现服务 -> MTU 协商 -> 订阅通知」整条流程的最大等待时间。
     */
    const val CONNECT_TIMEOUT_MS = 10_000L

    /**
     * 等待设备返回热点列表的超时（毫秒）：
     * App 发出 get_wifi_list 命令后，等待设备扫描周围 WiFi 并回传列表的最大时间。
     * 设备扫描慢（如需要扫 2~3 个信道）就调大这个值。
     */
    const val WIFI_LIST_TIMEOUT_MS = 15_000L

    /**
     * 等待设备连接热点并返回结果的超时（毫秒）：
     * App 发出 set_wifi 命令（SSID + 密码）后，等待设备连接路由器并回传配网结果的最大时间。
     * 设备连热点慢或路由器响应慢就调大这个值。
     */
    const val RESULT_TIMEOUT_MS = 20_000L
}
