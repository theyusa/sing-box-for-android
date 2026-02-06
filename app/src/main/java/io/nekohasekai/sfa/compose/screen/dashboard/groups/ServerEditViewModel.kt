package io.nekohasekai.sfa.compose.screen.dashboard.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.database.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ServerEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val serverConfig: ServerEditState? = null,
)

sealed class ServerEditEvent {
    object ConfigSaved : ServerEditEvent()
    data class Error(val message: String) : ServerEditEvent()
}

class ServerEditViewModel : BaseViewModel<ServerEditUiState, ServerEditEvent>() {

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ServerEditViewModel() as T
        }
    }

    override fun createInitialState() = ServerEditUiState()

    private fun isIPAddress(address: String): Boolean {
        val ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        val ipv6Pattern = "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$"
        return address.matches(ipv4Pattern.toRegex()) || address.matches(ipv6Pattern.toRegex())
    }

    private fun resolveDomain(domain: String): String? = try {
        val addresses = java.net.InetAddress.getAllByName(domain)
        addresses.firstOrNull()?.hostAddress
    } catch (e: Exception) {
        android.util.Log.w("ServerEditViewModel", "Failed to resolve domain: $domain", e)
        null
    }

    private fun resolveDomainsInConfig(configJson: String): String {
        return try {
            val jsonObject = JSONObject(configJson)
            val outbounds = jsonObject.optJSONArray("outbounds") ?: return configJson

            val skipTypes = setOf("selector", "urltest", "direct", "block", "dns", "reject", "blackhole", "loopback")

            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.getJSONObject(i)
                val server = outbound.optString("server", "")
                val type = outbound.optString("type", "")

                if (type !in skipTypes && server.isNotEmpty() && !isIPAddress(server)) {
                    val resolvedIP = resolveDomain(server)
                    if (resolvedIP != null) {
                        outbound.put("server", resolvedIP)
                        android.util.Log.d("ServerEditViewModel", "Resolved $server -> $resolvedIP")
                    }
                }
            }

            jsonObject.toString(2)
        } catch (e: Exception) {
            android.util.Log.e("ServerEditViewModel", "Error resolving domains", e)
            configJson
        }
    }

    fun loadServerConfig(groupTag: String, serverTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(isLoading = true) }

                val profileId = io.nekohasekai.sfa.database.Settings.selectedProfile
                if (profileId == 0L) return@launch
                val profile = io.nekohasekai.sfa.database.ProfileManager.get(profileId) ?: return@launch
                val configFile = File(profile.typed.path)

                if (!configFile.exists()) {
                    withContext(Dispatchers.Main) {
                        sendEvent(ServerEditEvent.Error("Config file not found"))
                    }
                    return@launch
                }

                val configJson = JSONObject(configFile.readText())
                val outbounds = configJson.optJSONArray("outbounds") ?: return@launch

                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)
                    if (outbound.optString("tag") == serverTag) {
                        val type = outbound.optString("type")

                        val editState = if (profile.typed.forceResolve) {
                            var server = outbound.optString("server", "")
                            if (server.isNotEmpty() && !isIPAddress(server)) {
                                val resolvedIP = resolveDomain(server)
                                if (resolvedIP != null) {
                                    server = resolvedIP
                                    android.util.Log.d("ServerEditViewModel", "Resolved $server -> $resolvedIP")
                                }
                            }
                            val modifiedOutbound = JSONObject(outbound.toString())
                            modifiedOutbound.put("server", server)
                            parseOutboundToState(modifiedOutbound, type)
                        } else {
                            parseOutboundToState(outbound, type)
                        }

                        withContext(Dispatchers.Main) {
                            updateState {
                                copy(
                                    isLoading = false,
                                    serverConfig = editState,
                                )
                            }
                        }
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    sendEvent(ServerEditEvent.Error("Server not found"))
                }
            } catch (e: Exception) {
                Log.e("ServerEditViewModel", "Error loading server config", e)
                withContext(Dispatchers.Main) {
                    sendEvent(ServerEditEvent.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun saveServerConfig(editedConfig: ServerEditState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(isSaving = true) }

                val profileId = io.nekohasekai.sfa.database.Settings.selectedProfile
                if (profileId == 0L) return@launch
                val profile = ProfileManager.get(profileId) ?: return@launch
                val configFile = File(profile.typed.path)

                val configJson = JSONObject(configFile.readText())
                val outbounds = configJson.getJSONArray("outbounds")

                var found = false
                val newOutbounds = JSONArray()

                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)

                    if (outbound.optString("tag") == editedConfig.tag) {
                        found = true
                        newOutbounds.put(updateOutboundFromState(editedConfig))
                    } else {
                        newOutbounds.put(outbound)
                    }
                }

                if (!found) {
                    withContext(Dispatchers.Main) {
                        sendEvent(ServerEditEvent.Error("Server not found in config"))
                        updateState { copy(isSaving = false) }
                    }
                    return@launch
                }

                configJson.put("outbounds", newOutbounds)

                Libbox.checkConfig(configJson.toString())

                val finalContent = if (profile.typed.forceResolve) {
                    resolveDomainsInConfig(configJson.toString())
                } else {
                    configJson.toString(2)
                }
                configFile.writeText(finalContent)

                Libbox.newStandaloneCommandClient().serviceReload()

                withContext(Dispatchers.Main) {
                    updateState { copy(isSaving = false) }
                    sendEvent(ServerEditEvent.ConfigSaved)
                }
            } catch (e: Exception) {
                Log.e("ServerEditViewModel", "Error saving server config", e)
                withContext(Dispatchers.Main) {
                    sendEvent(ServerEditEvent.Error(e.message ?: "Failed to save"))
                    updateState { copy(isSaving = false) }
                }
            }
        }
    }

    private fun parseOutboundToState(outbound: JSONObject, type: String): ServerEditState = when (type) {
        "vmess" -> {
            val tls = outbound.optJSONObject("tls")
            val transport = outbound.optJSONObject("transport")
            ServerEditState(
                tag = outbound.optString("tag"),
                server = outbound.optString("server"),
                server_port = outbound.optInt("server_port", 443),
                uuid = outbound.optString("uuid"),
                security = outbound.optString("security", "auto"),
                alter_id = outbound.optInt("alter_id", 0),
                network = outbound.optString("network", "tcp"),
                tlsEnabled = tls != null && tls.optBoolean("enabled", false),
                tlsServerName = tls?.optString("server_name") ?: "",
                tlsInsecure = tls?.optBoolean("insecure") ?: false,
                transportType = transport?.optString("type") ?: "",
                transportPath = transport?.optString("path") ?: "",
                transportHost = transport?.optString("host") ?: "",
                protocolType = "vmess",
            )
        }
        "vless" -> {
            val tls = outbound.optJSONObject("tls")
            val transport = outbound.optJSONObject("transport")
            ServerEditState(
                tag = outbound.optString("tag"),
                server = outbound.optString("server"),
                server_port = outbound.optInt("server_port", 443),
                uuid = outbound.optString("uuid"),
                network = outbound.optString("network", "tcp"),
                tlsEnabled = tls != null && tls.optBoolean("enabled", false),
                tlsServerName = tls?.optString("server_name") ?: "",
                tlsInsecure = tls?.optBoolean("insecure") ?: false,
                transportType = transport?.optString("type") ?: "",
                transportPath = transport?.optString("path") ?: "",
                transportHost = transport?.optString("host") ?: "",
                protocolType = "vless",
            )
        }
        else -> ServerEditState(protocolType = type)
    }

    private fun updateOutboundFromState(state: ServerEditState): JSONObject = when (state.protocolType) {
        "vmess" -> {
            val outbound = JSONObject().apply {
                put("type", "vmess")
                put("tag", state.tag)
                put("server", state.server)
                put("server_port", state.server_port)
                put("uuid", state.uuid)
                put("security", state.security)
                put("alter_id", state.alter_id)
                put("network", state.network)
                put("global_padding", false)
                put("authenticated_length", true)
            }

            if (state.tlsEnabled) {
                outbound.put(
                    "tls",
                    JSONObject().apply {
                        put("enabled", true)
                        if (state.tlsServerName.isNotEmpty()) {
                            put("server_name", state.tlsServerName)
                        }
                        if (state.tlsInsecure) {
                            put("insecure", true)
                        }
                    },
                )
            }

            if (state.network == "ws" && (state.transportHost.isNotEmpty() || state.transportPath.isNotEmpty())) {
                outbound.put(
                    "transport",
                    JSONObject().apply {
                        put("type", "ws")
                        if (state.transportHost.isNotEmpty()) put("host", state.transportHost)
                        if (state.transportPath.isNotEmpty()) put("path", state.transportPath)
                    },
                )
            }

            outbound
        }
        "vless" -> {
            val outbound = JSONObject().apply {
                put("type", "vless")
                put("tag", state.tag)
                put("server", state.server)
                put("server_port", state.server_port)
                put("uuid", state.uuid)
                put("network", state.network)
                put("packet_encoding", "xudp")
            }

            if (state.tlsEnabled) {
                outbound.put(
                    "tls",
                    JSONObject().apply {
                        put("enabled", true)
                        if (state.tlsServerName.isNotEmpty()) {
                            put("server_name", state.tlsServerName)
                        }
                        if (state.tlsInsecure) {
                            put("insecure", true)
                        }
                    },
                )
            }

            if (state.network == "ws" && (state.transportHost.isNotEmpty() || state.transportPath.isNotEmpty())) {
                outbound.put(
                    "transport",
                    JSONObject().apply {
                        put("type", "ws")
                        if (state.transportHost.isNotEmpty()) put("host", state.transportHost)
                        if (state.transportPath.isNotEmpty()) put("path", state.transportPath)
                    },
                )
            }

            outbound
        }
        else -> JSONObject()
    }
}
