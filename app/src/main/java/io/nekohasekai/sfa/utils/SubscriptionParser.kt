package io.nekohasekai.sfa.utils

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SubscriptionParser(private val urlParser: V2RayUrlParser) {

    suspend fun parseSubscriptionUrl(subscriptionUrl: String): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val content = subscriptionUrl

            val decoded = try {
                String(Base64.decode(content, Base64.DEFAULT))
            } catch (e: Exception) {
                String(Base64.decode(content.trim(), Base64.NO_WRAP))
            }

            val lines = decoded.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            val outbounds = mutableListOf<JSONObject>()

            for (line in lines) {
                when {
                    line.startsWith("vmess://") -> {
                        urlParser.parseVmessUrl(line)?.let { config ->
                            outbounds.add(createVmessOutbound(config))
                        }
                    }
                    line.startsWith("vless://") -> {
                        urlParser.parseVlessUrl(line)?.let { config ->
                            outbounds.add(createVlessOutbound(config))
                        }
                    }
                }
            }

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
}
