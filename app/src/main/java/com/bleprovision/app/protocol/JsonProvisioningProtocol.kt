package com.bleprovision.app.protocol

import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * 默认 JSON 协议实现（设备固件可按此格式实现）：
 *
 * App -> 设备（写入 WRITE_CHAR_UUID）：
 *   {"cmd":"get_wifi_list"}
 *   {"cmd":"set_wifi","ssid":"MyWiFi","password":"12345678"}
 *
 * 设备 -> App（NOTIFY_CHAR_UUID 发通知）：
 *   {"event":"wifi_list","list":[{"ssid":"MyWiFi","rssi":-58,"auth":true}]}
 *   {"event":"result","code":0}                          // 0 表示配网成功
 *   {"event":"result","code":1,"message":"密码错误"}
 *
 * 传输方式：超过 MTU 的消息由 App 自动分片发送；
 * 设备返回的每条通知需是一个完整 JSON（多条连发也可以，App 会按大括号配对自动切分）。
 */
class JsonProvisioningProtocol : ProvisioningProtocol {

    override fun encodeGetWifiList(): ByteArray =
        JSONObject().put("cmd", "get_wifi_list").toString().toByteArray(StandardCharsets.UTF_8)

    override fun encodeSetWifi(ssid: String, password: String): ByteArray =
        JSONObject()
            .put("cmd", "set_wifi")
            .put("ssid", ssid)
            .put("password", password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

    override fun tryExtractMessage(buffer: ByteArray): ExtractedMessage? {
        // 按字节扫描大括号配对（'{'/'}' 是 ASCII 值，UTF-8 多字节字符不含这类字节，扫描安全）
        var depth = 0
        var start = -1
        for (i in buffer.indices) {
            when (buffer[i].toInt()) {
                0x7B -> {          // '{'
                    if (depth == 0) start = i
                    depth++
                }
                0x7D -> {          // '}'
                    depth--
                    if (depth < 0) {
                        depth = 0
                        start = -1
                    } else if (depth == 0 && start >= 0) {
                        val candidate = String(buffer, start, i + 1 - start, StandardCharsets.UTF_8)
                        val event = parseEvent(candidate)
                        if (event != null) {
                            return ExtractedMessage(i + 1, event)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseEvent(json: String): ProtocolEvent? = try {
        val obj = JSONObject(json)
        when (obj.optString("event")) {
            "wifi_list" -> {
                val arr = obj.optJSONArray("list")
                val list = mutableListOf<WifiApInfo>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            WifiApInfo(
                                ssid = item.optString("ssid"),
                                rssi = item.optInt("rssi", -100),
                                authRequired = item.optBoolean("auth", true)
                            )
                        )
                    }
                }
                ProtocolEvent.WifiList(list)
            }
            "result" -> ProtocolEvent.Result(
                ProvisionResult(
                    success = obj.optInt("code", -1) == 0,
                    code = obj.optInt("code", -1),
                    message = obj.optString("message").ifEmpty { null }
                )
            )
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
