package io.nekohasekai.sfa.utils

import android.util.Base64
import org.json.JSONObject

data class VmessConfig(
    val add: String,
    val port: Int,
    val id: String,
    val aid: Int,
    val net: String = "tcp",
    val type: String = "none",
    val host: String = "",
    val path: String = "",
    val tls: String = "",
)

data class VlessConfig(
    val uuid: String,
    val server: String,
    val port: Int,
    val encryption: String = "none",
    val security: String = "",
    val type: String = "tcp",
    val host: String = "",
    val path: String = "",
    val name: String = "",
)

class V2RayUrlParser {

    fun parseVmessUrl(url: String): VmessConfig? {
        if (!url.startsWith("vmess://")) return null

        try {
            val encoded = url.substring(8)
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
            val json = JSONObject(decoded)

            return VmessConfig(
                add = json.optString("add"),
                port = json.optInt("port", 443),
                id = json.optString("id"),
                aid = json.optInt("aid", 0),
                net = json.optString("net", "tcp"),
                type = json.optString("type", "none"),
                host = json.optString("host", ""),
                path = json.optString("path", ""),
                tls = json.optString("tls", ""),
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun parseVlessUrl(url: String): VlessConfig? {
        if (!url.startsWith("vless://")) return null

        try {
            val parts = url.substring(8).split("?", "#")
            if (parts.isEmpty()) return null

            val authParts = parts[0].split("@")
            if (authParts.size != 2) return null

            val uuid = authParts[0]
            val serverPort = authParts[1].split(":")
            if (serverPort.size != 2) return null

            val server = serverPort[0]
            val port = serverPort[1].toIntOrNull() ?: 443

            val queryParams: Map<String, String> = if (parts.size > 1) {
                parts[1].split("&").associateNotNull { param ->
                    val keyValue = param.split("=", limit = 2)
                    if (keyValue.size == 2) {
                        keyValue[0] to keyValue[1]
                    } else {
                        null
                    }
                }
            } else {
                emptyMap()
            }

            val name: String = if (parts.size > 2) {
                parts[2]
            } else {
                "$server:$port"
            }

            return VlessConfig(
                uuid = uuid,
                server = server,
                port = port,
                encryption = queryParams["encryption"] ?: "none",
                security = queryParams["security"] ?: "",
                type = queryParams["type"] ?: "tcp",
                host = queryParams["host"] ?: "",
                path = queryParams["path"] ?: "",
                name = name
            )
        } catch (e: Exception) {
            return null
        }
    }

    private inline fun <K, V> Map<String, String>.associateNotNull(transform: (Map.Entry<String, String>) -> Pair<K, V>?): Map<K, V> {
        val destination = mutableMapOf<K, V>()
        for (element in this) {
            val pair = transform(element)
            if (pair != null) {
                destination[pair.first] = pair.second
            }
        }
        return destination
    }

    private inline fun <K, V> List<String>.associateNotNull(transform: (String) -> Pair<K, V>?): Map<K, V> {
        val destination = mutableMapOf<K, V>()
        for (element in this) {
            val pair = transform(element)
            if (pair != null) {
                destination[pair.first] = pair.second
            }
        }
        return destination
    }
}
