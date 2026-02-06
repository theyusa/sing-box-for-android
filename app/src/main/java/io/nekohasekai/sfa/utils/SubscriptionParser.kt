package io.nekohasekai.sfa.utils

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SubscriptionParser(private val urlParser: V2RayUrlParser) {

    suspend fun parseSubscriptionUrl(subscriptionUrl: String): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val content = subscriptionUrl

            android.util.Log.d("SubscriptionParser", "Content length: ${content.length}")

            val decoded = try {
                String(Base64.decode(content, Base64.DEFAULT))
            } catch (e: Exception) {
                String(Base64.decode(content.trim(), Base64.NO_WRAP))
            }

            android.util.Log.d("SubscriptionParser", "Decoded length: ${decoded.length}")

            val lines = decoded.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            android.util.Log.d("SubscriptionParser", "Lines count: ${lines.size}")

            val outbounds = mutableListOf<JSONObject>()

            for (line in lines) {
                android.util.Log.d("SubscriptionParser", "Line: ${line.substring(0, minOf(50, line.length))}")

                when {
                    line.startsWith("vmess://") -> {
                        urlParser.parseVmessUrl(line)?.let { config ->
                            android.util.Log.d("SubscriptionParser", "Parsed vmess: ${config.add}:${config.port}")
                            outbounds.add(createVmessOutbound(config))
                        } ?: android.util.Log.w("SubscriptionParser", "Failed to parse vmess")
                    }
                    line.startsWith("vless://") -> {
                        urlParser.parseVlessUrl(line)?.let { config ->
                            android.util.Log.d("SubscriptionParser", "Parsed vless: ${config.server}:${config.port}")
                            outbounds.add(createVlessOutbound(config))
                        } ?: android.util.Log.w("SubscriptionParser", "Failed to parse vless")
                    }
                    line.startsWith("trojan://") -> {
                        urlParser.parseTrojanUrl(line)?.let { config ->
                            android.util.Log.d("SubscriptionParser", "Parsed trojan: ${config.server}:${config.port}")
                            outbounds.add(createTrojanOutbound(config))
                        } ?: android.util.Log.w("SubscriptionParser", "Failed to parse trojan")
                    }
                    line.startsWith("ss://") -> {
                        urlParser.parseShadowsocksUrl(line)?.let { config ->
                            android.util.Log.d("SubscriptionParser", "Parsed ss: ${config.server}:${config.port}")
                            outbounds.add(createShadowsocksOutbound(config))
                        } ?: android.util.Log.w("SubscriptionParser", "Failed to parse ss")
                    }
                    else -> {
                        android.util.Log.w("SubscriptionParser", "Unknown protocol: ${line.substring(0, minOf(20, line.length))}")
                    }
                }
            }

            android.util.Log.d("SubscriptionParser", "Total outbounds: ${outbounds.size}")
            outbounds
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createVmessOutbound(config: VmessConfig): JSONObject {
        val outbound = JSONObject().apply {
            put("type", "vmess")
            put("tag", "vmess-${config.add}:${config.port}")
            put("server", config.add)
            put("server_port", config.port)
            put("uuid", config.id)
            put("security", "auto")
            put("alter_id", config.aid)
            put("network", config.net)
            put("global_padding", false)
            put("authenticated_length", true)
        }

        if (config.tls.isNotEmpty()) {
            outbound.put(
                "tls",
                JSONObject().apply {
                    put("enabled", true)
                    if (config.host.isNotEmpty()) {
                        put("server_name", config.host)
                    }
                },
            )
        }

        if (config.net == "ws" && (config.host.isNotEmpty() || config.path.isNotEmpty())) {
            outbound.put(
                "transport",
                JSONObject().apply {
                    put("type", "ws")
                    if (config.host.isNotEmpty()) put("host", config.host)
                    if (config.path.isNotEmpty()) put("path", config.path)
                },
            )
        }

        return outbound
    }

    private fun createVlessOutbound(config: VlessConfig): JSONObject {
        val outbound = JSONObject().apply {
            put("type", "vless")
            put("tag", config.name)
            put("server", config.server)
            put("server_port", config.port)
            put("uuid", config.uuid)
            put("network", config.type)
            put("packet_encoding", "xudp")
        }

        if (config.security == "tls") {
            outbound.put(
                "tls",
                JSONObject().apply {
                    put("enabled", true)
                    if (config.host.isNotEmpty()) {
                        put("server_name", config.host)
                    }
                },
            )
        }

        if (config.type == "ws" && (config.host.isNotEmpty() || config.path.isNotEmpty())) {
            outbound.put(
                "transport",
                JSONObject().apply {
                    put("type", "ws")
                    if (config.host.isNotEmpty()) put("host", config.host)
                    if (config.path.isNotEmpty()) put("path", config.path)
                },
            )
        }

        return outbound
    }

    private fun createTrojanOutbound(config: TrojanConfig): JSONObject {
        val outbound = JSONObject().apply {
            put("type", "trojan")
            put("tag", config.name)
            put("server", config.server)
            put("server_port", config.port)
            put("password", config.password)
        }

        if (config.security.isNotEmpty()) {
            outbound.put(
                "tls",
                JSONObject().apply {
                    put("enabled", true)
                    if (config.host.isNotEmpty()) {
                        put("server_name", config.host)
                    }
                },
            )
        }

        if (config.type == "ws" && (config.host.isNotEmpty() || config.path.isNotEmpty())) {
            outbound.put(
                "transport",
                JSONObject().apply {
                    put("type", "ws")
                    if (config.host.isNotEmpty()) put("host", config.host)
                    if (config.path.isNotEmpty()) put("path", config.path)
                },
            )
        }

        return outbound
    }

    private fun createShadowsocksOutbound(config: ShadowsocksConfig): JSONObject {
        val outbound = JSONObject().apply {
            put("type", "shadowsocks")
            put("tag", config.name)
            put("server", config.server)
            put("server_port", config.port)
            put("method", config.method)
            put("password", config.password)
        }

        if (config.plugin.isNotEmpty()) {
            outbound.put("plugin", config.plugin)
            if (config.pluginOpts.isNotEmpty()) {
                outbound.put("plugin_opts", config.pluginOpts)
            }
        }

        return outbound
    }
}
