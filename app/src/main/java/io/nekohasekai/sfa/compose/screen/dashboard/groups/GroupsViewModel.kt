package io.nekohasekai.sfa.compose.screen.dashboard.groups

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.ScreenEvent
import io.nekohasekai.sfa.compose.model.Group
import io.nekohasekai.sfa.compose.model.GroupItem
import io.nekohasekai.sfa.compose.model.toList
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ServerSelectionMode {
    SELECT,
    AUTO,
}

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = false,
    val expandedGroups: Set<String> = emptySet(),
    val showCloseConnectionsSnackbar: Boolean = false,
    val serverSelectionMode: Map<String, ServerSelectionMode> = emptyMap(),
)

sealed class GroupsEvent : ScreenEvent {
    data class GroupSelected(val groupTag: String, val itemTag: String) : GroupsEvent()
    data class OpenServerEditor(val groupTag: String, val serverTag: String) : GroupsEvent()
}

class GroupsViewModel(private val sharedCommandClient: CommandClient? = null) :
    BaseViewModel<GroupsUiState, GroupsEvent>(),
    CommandClient.Handler {
    private val commandClient: CommandClient
    private val isUsingSharedClient: Boolean

    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus = _serviceStatus.asStateFlow()
    private var lastServiceStatus: Status = Status.Stopped

    init {
        if (sharedCommandClient != null) {
            commandClient = sharedCommandClient
            isUsingSharedClient = true
            commandClient.addHandler(this)
        } else {
            commandClient =
                CommandClient(
                    viewModelScope,
                    CommandClient.ConnectionType.Groups,
                    this,
                )
            isUsingSharedClient = false
        }

        viewModelScope.launch {
            AppLifecycleObserver.isForeground.collect { foreground ->
                if (lastServiceStatus != Status.Started) return@collect
                if (foreground) {
                    if (isUsingSharedClient) {
                        commandClient.addHandler(this@GroupsViewModel)
                    } else {
                        updateState { copy(isLoading = true) }
                        commandClient.connect()
                    }
                } else {
                    if (isUsingSharedClient) {
                        commandClient.removeHandler(this@GroupsViewModel)
                    } else {
                        commandClient.disconnect()
                    }
                }
            }
        }
    }

    override fun createInitialState() = GroupsUiState()

    override fun onCleared() {
        super.onCleared()
        if (isUsingSharedClient) {
            commandClient.removeHandler(this)
        } else {
            commandClient.disconnect()
        }
    }

    private fun handleServiceStatusChange(status: Status) {
        if (status == Status.Started) {
            if (!isUsingSharedClient && AppLifecycleObserver.isForeground.value) {
                updateState { copy(isLoading = true) }
                commandClient.connect()
            }
        } else {
            if (!isUsingSharedClient) {
                commandClient.disconnect()
            }
            updateState {
                copy(
                    groups = emptyList(),
                    isLoading = false,
                )
            }
        }
    }

    fun updateServiceStatus(status: Status) {
        if (status == lastServiceStatus) {
            return
        }
        lastServiceStatus = status
        viewModelScope.launch {
            _serviceStatus.emit(status)
            handleServiceStatusChange(status)
        }
    }

    fun toggleGroupExpand(groupTag: String) {
        val newExpanded = !uiState.value.expandedGroups.contains(groupTag)
        updateState {
            val newExpandedGroups = if (newExpanded) {
                expandedGroups + groupTag
            } else {
                expandedGroups - groupTag
            }
            copy(expandedGroups = newExpandedGroups)
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                Libbox.newStandaloneCommandClient().setGroupExpand(groupTag, newExpanded)
            }
        }
    }

    fun toggleAllGroups() {
        val groups = uiState.value.groups
        val allCollapsed = uiState.value.expandedGroups.isEmpty()
        val newExpanded = allCollapsed

        updateState {
            if (allCollapsed) {
                copy(expandedGroups = groups.map { it.tag }.toSet())
            } else {
                copy(expandedGroups = emptySet())
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            groups.forEach { group ->
                runCatching {
                    Libbox.newStandaloneCommandClient().setGroupExpand(group.tag, newExpanded)
                }
            }
        }
    }

    fun selectGroupItem(groupTag: String, itemTag: String) {
        val currentGroup = uiState.value.groups.find { it.tag == groupTag }
        if (currentGroup?.selected == itemTag) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().selectOutbound(groupTag, itemTag)

                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            groups =
                            groups.map { group ->
                                if (group.tag == groupTag) {
                                    group.copy(selected = itemTag)
                                } else {
                                    group
                                }
                            },
                            showCloseConnectionsSnackbar = true,
                        )
                    }
                    sendEvent(GroupsEvent.GroupSelected(groupTag, itemTag))
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun closeConnections() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().closeConnections()
                withContext(Dispatchers.Main) {
                    dismissCloseConnectionsSnackbar()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dismissCloseConnectionsSnackbar()
                }
                sendError(e)
            }
        }
    }

    fun dismissCloseConnectionsSnackbar() {
        updateState {
            copy(showCloseConnectionsSnackbar = false)
        }
    }

    fun getProfileId(): Long = io.nekohasekai.sfa.database.Settings.selectedProfile

    fun urlTestAndSelectBest(groupTag: String, profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = Libbox.newStandaloneCommandClient()

                client.urlTest(groupTag)

                var retries = 0
                val maxRetries = 30
                val retryDelayMs = 300L

                var bestItemTag: String? = null
                var bestLatency: Int? = null
                var lastTestTime = 0L

                while (retries < maxRetries) {
                    val group = uiState.value.groups.find { it.tag == groupTag }
                    if (group != null) {
                        var foundResults = false
                        group.items.forEach { item ->
                            if (item.urlTestTime > 0) {
                                foundResults = true
                                if (item.urlTestDelay > 0) {
                                    if (bestLatency == null || item.urlTestDelay < bestLatency) {
                                        bestLatency = item.urlTestDelay
                                        bestItemTag = item.tag
                                    }
                                }
                            }
                        }

                        if (foundResults && bestItemTag != null) {
                            if (bestItemTag != group.selected) {
                                client.selectOutbound(groupTag, bestItemTag)
                                withContext(Dispatchers.Main) {
                                    updateState {
                                        copy(
                                            groups =
                                            groups.map { g ->
                                                if (g.tag == groupTag) {
                                                    g.copy(selected = bestItemTag!!)
                                                } else {
                                                    g
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                            break
                        }
                    }

                    retries++
                    if (retries < maxRetries) {
                        kotlinx.coroutines.delay(retryDelayMs)
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun urlTest(groupTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().urlTest(groupTag)
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun deleteServer(groupTag: String, serverTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profileId = getProfileId()
                val profile = ProfileManager.get(profileId) ?: run {
                    withContext(Dispatchers.Main) {
                        sendError(Exception("Profile not found"))
                    }
                    return@launch
                }

                val configFile = File(profile.typed.path)
                if (!configFile.exists()) {
                    withContext(Dispatchers.Main) {
                        sendError(Exception("Config file not found"))
                    }
                    return@launch
                }

                val configJson = JSONObject(configFile.readText())

                val outbounds = configJson.optJSONArray("outbounds") ?: run {
                    withContext(Dispatchers.Main) {
                        sendError(Exception("No outbounds found in config"))
                    }
                    return@launch
                }

                val newOutbounds = JSONArray()
                var found = false

                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)
                    val tag = outbound.optString("tag", "")

                    if (tag == serverTag) {
                        found = true
                    } else {
                        newOutbounds.put(outbound)
                    }
                }

                if (!found) {
                    withContext(Dispatchers.Main) {
                        sendError(Exception("Server not found in config"))
                    }
                    return@launch
                }

                configJson.put("outbounds", newOutbounds)

                configFile.writeText(configJson.toString(2))

                withContext(Dispatchers.Main) {
                    Libbox.newStandaloneCommandClient().serviceReload()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    sendError(e)
                }
            }
        }
    }

    fun openServerEditor(groupTag: String, serverTag: String) {
        sendEvent(GroupsEvent.OpenServerEditor(groupTag, serverTag))
    }

    suspend fun getServerConfig(groupTag: String, serverTag: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val profileId = getProfileId()
            val profile = ProfileManager.get(profileId) ?: return@withContext null

            val configFile = File(profile.typed.path)
            if (!configFile.exists()) return@withContext null

            val configJson = JSONObject(configFile.readText())

            val outbounds = configJson.optJSONArray("outbounds") ?: return@withContext null

            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.getJSONObject(i)
                val tag = outbound.optString("tag", "")

                if (tag == serverTag) {
                    return@withContext outbound
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    groups = emptyList(),
                    isLoading = false,
                )
            }
        }
    }

    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentGroups = uiState.value.groups
            val newGroupsMap = newGroups.associateBy { it.tag }

            val mergedGroups =
                if (currentGroups.isEmpty()) {
                    newGroups.map(::Group)
                } else {
                    currentGroups.map { existingGroup ->
                        val newGroupData = newGroupsMap[existingGroup.tag]
                        if (newGroupData != null) {
                            val newItems = newGroupData.items.toList()
                            val hasStructuralChange =
                                existingGroup.items.size != newItems.size ||
                                    existingGroup.selected != newGroupData.selected ||
                                    existingGroup.type != newGroupData.type ||
                                    existingGroup.selectable != newGroupData.selectable

                            if (hasStructuralChange) {
                                Group(newGroupData)
                            } else {
                                val updatedItems =
                                    existingGroup.items.mapIndexed { index, item ->
                                        val newItemData = newItems.getOrNull(index)
                                        if (newItemData != null &&
                                            item.tag == newItemData.tag &&
                                            item.type == newItemData.type
                                        ) {
                                            if (item.urlTestDelay != newItemData.urlTestDelay ||
                                                item.urlTestTime != newItemData.urlTestTime
                                            ) {
                                                GroupItem(newItemData)
                                            } else {
                                                item
                                            }
                                        } else {
                                            if (newItemData != null) {
                                                GroupItem(newItemData)
                                            } else {
                                                item
                                            }
                                        }
                                    }
                                existingGroup.copy(items = updatedItems)
                            }
                        } else {
                            existingGroup
                        }
                    } +
                        newGroups.filter { newGroup ->
                            currentGroups.none { it.tag == newGroup.tag }
                        }.map(::Group)
                }

            withContext(Dispatchers.Main) {
                updateState {
                    val subscriptionGroupTags = newGroups
                        .filter { it.items.toList().size > 5 }
                        .map { it.tag }
                        .toSet()

                    val initialExpandedGroups = if (expandedGroups.isEmpty() && currentGroups.isEmpty()) {
                        mergedGroups.filter { it.isExpand }.map { it.tag }.toSet() + subscriptionGroupTags
                    } else {
                        expandedGroups + subscriptionGroupTags
                    }
                    copy(
                        groups = mergedGroups,
                        expandedGroups = initialExpandedGroups,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun toggleSelectionMode(groupTag: String) {
        val currentMode = uiState.value.serverSelectionMode[groupTag] ?: ServerSelectionMode.SELECT
        val newMode = if (currentMode == ServerSelectionMode.SELECT) {
            ServerSelectionMode.AUTO
        } else {
            ServerSelectionMode.SELECT
        }

        updateState {
            copy(
                serverSelectionMode = serverSelectionMode + (groupTag to newMode),
            )
        }
    }

    fun updateServerConfig(groupTag: String, serverTag: String, editedConfig: ServerEditState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profileId = getProfileId()
                val profile = ProfileManager.get(profileId) ?: return@launch
                val configFile = File(profile.typed.path)

                val configJson = JSONObject(configFile.readText())
                val outbounds = configJson.getJSONArray("outbounds")

                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)

                    if (outbound.optString("tag") == serverTag) {
                        when (editedConfig.protocolType) {
                            "vmess" -> {
                                outbound.put("server", editedConfig.server)
                                outbound.put("server_port", editedConfig.server_port)
                                outbound.put("uuid", editedConfig.uuid)
                                outbound.put("security", editedConfig.security)
                                outbound.put("alter_id", editedConfig.alter_id)
                                outbound.put("network", editedConfig.network)

                                if (editedConfig.tlsEnabled) {
                                    val tls = JSONObject().apply {
                                        put("enabled", true)
                                        if (editedConfig.tlsServerName.isNotEmpty()) {
                                            put("server_name", editedConfig.tlsServerName)
                                        }
                                        if (editedConfig.tlsInsecure) {
                                            put("insecure", true)
                                        }
                                    }
                                    outbound.put("tls", tls)
                                } else {
                                    outbound.remove("tls")
                                }

                                if (editedConfig.network == "ws") {
                                    val transport = JSONObject().apply {
                                        put("type", "ws")
                                        if (editedConfig.transportHost.isNotEmpty()) {
                                            put("host", editedConfig.transportHost)
                                        }
                                        if (editedConfig.transportPath.isNotEmpty()) {
                                            put("path", editedConfig.transportPath)
                                        }
                                    }
                                    outbound.put("transport", transport)
                                } else {
                                    outbound.remove("transport")
                                }
                            }
                            "vless" -> {
                                outbound.put("server", editedConfig.server)
                                outbound.put("server_port", editedConfig.server_port)
                                outbound.put("uuid", editedConfig.uuid)
                                outbound.put("network", editedConfig.network)

                                if (editedConfig.tlsEnabled) {
                                    val tls = JSONObject().apply {
                                        put("enabled", true)
                                        if (editedConfig.tlsServerName.isNotEmpty()) {
                                            put("server_name", editedConfig.tlsServerName)
                                        }
                                        if (editedConfig.tlsInsecure) {
                                            put("insecure", true)
                                        }
                                    }
                                    outbound.put("tls", tls)
                                } else {
                                    outbound.remove("tls")
                                }

                                if (editedConfig.network == "ws") {
                                    val transport = JSONObject().apply {
                                        put("type", "ws")
                                        if (editedConfig.transportHost.isNotEmpty()) {
                                            put("host", editedConfig.transportHost)
                                        }
                                        if (editedConfig.transportPath.isNotEmpty()) {
                                            put("path", editedConfig.transportPath)
                                        }
                                    }
                                    outbound.put("transport", transport)
                                } else {
                                    outbound.remove("transport")
                                }
                            }
                        }
                        break
                    }
                }

                configFile.writeText(configJson.toString(2))

                Libbox.checkConfig(configJson.toString())
                Libbox.newStandaloneCommandClient().serviceReload()
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }
}
