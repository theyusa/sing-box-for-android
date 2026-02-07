package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OutboundGroup
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.base.BaseViewModel
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.utils.AppLifecycleObserver
import io.nekohasekai.sfa.utils.CommandClient
import io.nekohasekai.sfa.utils.HTTPClient
import io.nekohasekai.sfa.utils.SubscriptionImportHandler
import io.nekohasekai.sfa.utils.SubscriptionImportResult
import io.nekohasekai.sfa.utils.SubscriptionParser
import io.nekohasekai.sfa.utils.V2RayUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.Collections
import java.util.Date

enum class CardGroup {
    ClashMode,
    UploadTraffic,
    DownloadTraffic,
    Debug,
    Connections,
    SystemProxy,
    Profiles,
}

enum class CardWidth {
    Half,
    Full,
}

data class SubscriptionServer(
    val tag: String,
    val type: String,
    val server: String,
    val port: Int,
    val uuid: String? = null,
)

data class ServerEditState(
    val originalTag: String,
    val tag: String,
    val type: String,
    val server: String,
    val port: Int,
    val uuid: String?,
)

data class DashboardUiState(
    val serviceStatus: Status = Status.Stopped,
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: Long = -1L,
    val selectedProfileName: String? = null,
    val isLoading: Boolean = false,
    val hasGroups: Boolean = false,
    val groupsCount: Int = 0,
    val connectionsCount: Int = 0,
    val serviceStartTime: Long? = null,
    val deprecatedNotes: List<DeprecatedNote> = emptyList(),
    val showDeprecatedDialog: Boolean = false,
    val showAddProfileSheet: Boolean = false,
    val showProfilePickerSheet: Boolean = false,
    val showSubscriptionGroupsSheet: Boolean = false,
    val subscriptionServers: List<SubscriptionServer> = emptyList(),
    val selectedServerTag: String? = null,
    val showServerEditDialog: Boolean = false,
    val editingServer: ServerEditState? = null,
    val updatingProfileId: Long? = null,
    val updatedProfileId: Long? = null,
    // Status
    val memory: String = "",
    val goroutines: String = "",
    val isStatusVisible: Boolean = false,
    // Traffic
    val trafficVisible: Boolean = false,
    val connectionsIn: String = "0",
    val connectionsOut: String = "0",
    val uplink: String = "0 B/s",
    val downlink: String = "0 B/s",
    val uplinkTotal: String = "0 B",
    val downlinkTotal: String = "0 B",
    val uplinkHistory: List<Float> = List(30) { 0f },
    val downlinkHistory: List<Float> = List(30) { 0f },
    // Clash Mode
    val clashModeVisible: Boolean = false,
    val clashModes: List<String> = emptyList(),
    val selectedClashMode: String = "",
    // System Proxy
    val systemProxyVisible: Boolean = false,
    val systemProxyEnabled: Boolean = false,
    val systemProxySwitching: Boolean = false,
    // Card visibility settings
    val visibleCards: Set<CardGroup> =
        setOf(
            CardGroup.ClashMode,
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.Profiles,
        ),
    val cardOrder: List<CardGroup> =
        listOf(
            CardGroup.UploadTraffic,
            CardGroup.DownloadTraffic,
            CardGroup.Debug,
            CardGroup.Connections,
            CardGroup.SystemProxy,
            CardGroup.ClashMode,
            CardGroup.Profiles,
        ),
    val cardWidths: Map<CardGroup, CardWidth> =
        mapOf(
            CardGroup.ClashMode to CardWidth.Full,
            CardGroup.UploadTraffic to CardWidth.Half,
            CardGroup.DownloadTraffic to CardWidth.Half,
            CardGroup.Debug to CardWidth.Half,
            CardGroup.Connections to CardWidth.Half,
            CardGroup.SystemProxy to CardWidth.Full,
            CardGroup.Profiles to CardWidth.Full,
        ),
    val showCardSettingsDialog: Boolean = false,
) {
    data class DeprecatedNote(val message: String, val migrationLink: String?)
}

// DashboardViewModel now only uses UiEvent for all events
// No need for DashboardEvent anymore as all events are handled globally

class DashboardViewModel :
    BaseViewModel<DashboardUiState, UiEvent>(),
    CommandClient.Handler {
    private val _serviceStatus = MutableStateFlow(Status.Stopped)
    val serviceStatus: StateFlow<Status> = _serviceStatus.asStateFlow()

    internal val commandClient =
        CommandClient(
            viewModelScope,
            listOf(
                CommandClient.ConnectionType.Status,
                CommandClient.ConnectionType.ClashMode,
                CommandClient.ConnectionType.Groups,
            ),
            this,
        )

    override fun createInitialState(): DashboardUiState {
        val savedOrder = loadItemOrder()
        val disabledItems = loadDisabledItems()

        // Calculate visible items (all items minus disabled)
        val allItems = CardGroup.values().toSet()
        val visibleCards = allItems - disabledItems

        return DashboardUiState(
            cardOrder = savedOrder,
            visibleCards = visibleCards,
        )
    }

    init {
        loadProfiles()
        ProfileManager.registerCallback(::onProfilesChanged)

        viewModelScope.launch {
            AppLifecycleObserver.isForeground.collect { foreground ->
                if (_serviceStatus.value != Status.Started) return@collect
                if (foreground) {
                    commandClient.connect()
                } else {
                    commandClient.disconnect()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ProfileManager.unregisterCallback(::onProfilesChanged)
        commandClient.disconnect()
    }

    private fun onProfilesChanged() {
        loadProfiles()
    }

    private fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = ProfileManager.list()
                val selectedId = Settings.selectedProfile

                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            profiles = profiles,
                            selectedProfileId = selectedId,
                            selectedProfileName = profiles.find { it.id == selectedId }?.name,
                        )
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    private fun checkDeprecatedNotes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if deprecated warnings are disabled
                if (Settings.disableDeprecatedWarnings) {
                    return@launch
                }

                val notes = Libbox.newStandaloneCommandClient().deprecatedNotes
                if (notes.hasNext()) {
                    val notesList = mutableListOf<DashboardUiState.DeprecatedNote>()
                    while (notes.hasNext()) {
                        val note = notes.next()
                        notesList.add(
                            DashboardUiState.DeprecatedNote(
                                message = note.message(),
                                migrationLink = note.migrationLink,
                            ),
                        )
                    }
                    withContext(Dispatchers.Main) {
                        updateState {
                            copy(
                                deprecatedNotes = notesList,
                                showDeprecatedDialog = notesList.isNotEmpty(),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun toggleService() {
        when (currentState.serviceStatus) {
            Status.Starting, Status.Started -> stopService()
            Status.Stopped -> sendGlobalEvent(UiEvent.RequestStartService)
            else -> { /* Ignore while transitioning */ }
        }
    }

    private fun stopService() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BoxService.stop()
                // Status will be updated via updateServiceStatus callback
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    fun dismissDeprecatedNote() {
        val notes = currentState.deprecatedNotes
        if (notes.isNotEmpty()) {
            updateState {
                copy(
                    deprecatedNotes = notes.drop(1),
                    showDeprecatedDialog = notes.size > 1,
                )
            }
        }
    }

    fun selectProfile(profileId: Long) {
        if (currentState.isLoading) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(isLoading = true) }
                val profile = ProfileManager.get(profileId) ?: return@launch

                Settings.selectedProfile = profileId

                // Check if service is running
                if (_serviceStatus.value == Status.Started) {
                    val restart = Settings.rebuildServiceMode()
                    if (restart) {
                        // Need full restart
                        BoxService.stop()
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                        for (i in 0 until 30) {
                            if (_serviceStatus.value == Status.Stopped) {
                                break
                            }
                            delay(100L)
                        }
                        sendGlobalEvent(UiEvent.RequestStartService)
                    } else {
                        // Just reload
                        Libbox.newStandaloneCommandClient().serviceReload()
                    }
                }

                withContext(Dispatchers.Main) {
                    loadProfiles()
                }
            } catch (e: Exception) {
                sendError(e)
            } finally {
                updateState { copy(isLoading = false) }
            }
        }
    }

    fun editProfile(profile: Profile) {
        sendGlobalEvent(UiEvent.EditProfile(profile.id))
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update UI immediately for responsiveness
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            profiles = profiles.filter { p -> p.id != profile.id },
                        )
                    }
                }
                // Then delete from database
                ProfileManager.delete(profile)
            } catch (e: Exception) {
                // Reload profiles if deletion fails
                loadProfiles()
                sendError(e)
            }
        }
    }

    fun shareProfile(profile: Profile) {
        // Handled directly in ProfilesCard
    }

    fun shareProfileURL(profile: Profile) {
        // Handled directly in ProfilesCard
    }

    fun updateProfile(profile: Profile) {
        if (profile.typed.type != TypedProfile.Type.Remote) return

        viewModelScope.launch(Dispatchers.IO) {
            // Set updating state
            withContext(Dispatchers.Main) {
                updateState { copy(updatingProfileId = profile.id) }
            }

            try {
                // Fetch remote config
                val content = HTTPClient().use { it.getString(profile.typed.remoteURL) }
                Libbox.checkConfig(content)

                // Check if content changed
                val file = File(profile.typed.path)
                var contentChanged = false
                if (!file.exists() || file.readText() != content) {
                    file.writeText(content)
                    contentChanged = true
                }

                // Update last updated time
                profile.typed.lastUpdated = Date()
                ProfileManager.update(profile)

                // Reload profiles
                loadProfiles()

                // Show success state
                withContext(Dispatchers.Main) {
                    updateState { copy(updatingProfileId = null, updatedProfileId = profile.id) }
                }

                // Clear success state after delay
                withContext(Dispatchers.Main) {
                    delay(1500)
                    updateState { copy(updatedProfileId = null) }
                }

                // Restart service if this is the selected profile and content changed
                if (contentChanged && profile.id == Settings.selectedProfile) {
                    withContext(Dispatchers.Main) {
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                    }
                }
            } catch (e: Exception) {
                sendErrorMessage("Failed to update profile: ${e.message}")
                // Clear updating state on error
                withContext(Dispatchers.Main) {
                    updateState { copy(updatingProfileId = null) }
                }
            }
        }
    }

    fun moveProfile(from: Int, to: Int) {
        val currentProfiles = currentState.profiles.toMutableList()

        if (from < to) {
            for (i in from until to) {
                Collections.swap(currentProfiles, i, i + 1)
            }
        } else {
            for (i in from downTo to + 1) {
                Collections.swap(currentProfiles, i, i - 1)
            }
        }

        // Update UI immediately
        updateState { copy(profiles = currentProfiles) }

        // Update user order in database
        viewModelScope.launch(Dispatchers.IO) {
            currentProfiles.forEachIndexed { index, profile ->
                profile.userOrder = index.toLong()
            }
            ProfileManager.update(currentProfiles)
        }
    }

    fun showAddProfileSheet() {
        updateState { copy(showAddProfileSheet = true) }
    }

    fun hideAddProfileSheet() {
        updateState { copy(showAddProfileSheet = false) }
    }

    fun showProfilePickerSheet() {
        updateState { copy(showProfilePickerSheet = true) }
    }

    fun hideProfilePickerSheet() {
        updateState { copy(showProfilePickerSheet = false) }
    }

    fun updateServiceStatus(status: Status) {
        viewModelScope.launch {
            _serviceStatus.emit(status)
            updateState {
                copy(
                    serviceStatus = status,
                    isStatusVisible = status == Status.Starting || status == Status.Started,
                )
            }
            handleServiceStatusChange(status)
        }
    }

    private fun handleServiceStatusChange(status: Status) {
        when (status) {
            Status.Started -> {
                checkDeprecatedNotes()
                if (AppLifecycleObserver.isForeground.value) {
                    commandClient.connect()
                }
                reloadSystemProxyStatus()
                reloadStartedAt()
            }

            Status.Stopped -> {
                commandClient.disconnect()
                updateState {
                    copy(
                        hasGroups = false,
                        groupsCount = 0,
                        connectionsCount = 0,
                        serviceStartTime = null,
                        clashModeVisible = false,
                        systemProxyVisible = false,
                        trafficVisible = false,
                        memory = "",
                        goroutines = "",
                        connectionsIn = "0",
                        connectionsOut = "0",
                        uplink = "0 B/s",
                        downlink = "0 B/s",
                        uplinkTotal = "0 B",
                        downlinkTotal = "0 B",
                        uplinkHistory = List(30) { 0f },
                        downlinkHistory = List(30) { 0f },
                    )
                }
            }

            else -> {}
        }
    }

    private fun reloadStartedAt() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startedAt = Libbox.newStandaloneCommandClient().startedAt
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(serviceStartTime = startedAt)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun reloadSystemProxyStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = Libbox.newStandaloneCommandClient().systemProxyStatus
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            systemProxyVisible = status.available,
                            systemProxyEnabled = status.enabled,
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun toggleSystemProxy(enabled: Boolean) {
        if (currentState.systemProxySwitching) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(systemProxySwitching = true) }
                Settings.systemProxyEnabled = enabled
                Libbox.newStandaloneCommandClient().setSystemProxyEnabled(enabled)
                delay(1000L)
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(
                            systemProxyEnabled = enabled,
                            systemProxySwitching = false,
                        )
                    }
                }
            } catch (e: Exception) {
                sendError(e)
                updateState { copy(systemProxySwitching = false) }
            }
        }
    }

    fun selectClashMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Libbox.newStandaloneCommandClient().setClashMode(mode)
                // Update UI state directly without reconnecting
                withContext(Dispatchers.Main) {
                    updateState {
                        copy(selectedClashMode = mode)
                    }
                }
            } catch (e: Exception) {
                sendError(e)
            }
        }
    }

    // CommandClient.Handler implementation
    override fun onConnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState { copy(isStatusVisible = true) }
        }
    }

    override fun onDisconnected() {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    memory = "",
                    goroutines = "",
                    isStatusVisible = false,
                )
            }
        }
    }

    override fun updateStatus(status: StatusMessage) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                // Update history by adding new values and removing old ones
                val newUplinkHistory = (uplinkHistory.drop(1) + status.uplink.toFloat())
                val newDownlinkHistory = (downlinkHistory.drop(1) + status.downlink.toFloat())

                // Format the total values
                val newUplinkTotal = Libbox.formatBytes(status.uplinkTotal)
                val newDownlinkTotal = Libbox.formatBytes(status.downlinkTotal)

                copy(
                    memory = Libbox.formatBytes(status.memory),
                    goroutines = status.goroutines.toString(),
                    // Only set trafficVisible to true, never back to false from status updates
                    trafficVisible = if (status.trafficAvailable) true else trafficVisible,
                    connectionsCount = status.connectionsIn,
                    connectionsIn = status.connectionsIn.toString(),
                    connectionsOut = status.connectionsOut.toString(),
                    uplink = "${Libbox.formatBytes(status.uplink)}/s",
                    downlink = "${Libbox.formatBytes(status.downlink)}/s",
                    // Only update total values if they've actually changed
                    uplinkTotal = if (newUplinkTotal != uplinkTotal) newUplinkTotal else uplinkTotal,
                    downlinkTotal = if (newDownlinkTotal != downlinkTotal) newDownlinkTotal else downlinkTotal,
                    uplinkHistory = newUplinkHistory,
                    downlinkHistory = newDownlinkHistory,
                )
            }
        }
    }

    override fun initializeClashMode(modeList: List<String>, currentMode: String) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(
                    clashModeVisible = modeList.size > 1,
                    clashModes = modeList,
                    selectedClashMode = currentMode,
                )
            }
        }
    }

    override fun updateClashMode(newMode: String) {
        viewModelScope.launch(Dispatchers.Main) {
            updateState {
                copy(selectedClashMode = newMode)
            }
        }
    }

    override fun updateGroups(newGroups: MutableList<OutboundGroup>) {
        viewModelScope.launch(Dispatchers.Main) {
            val hasGroups = newGroups.isNotEmpty()
            updateState {
                copy(hasGroups = hasGroups, groupsCount = newGroups.size)
            }
        }
    }

    fun toggleCardSettingsDialog() {
        updateState {
            copy(showCardSettingsDialog = !showCardSettingsDialog)
        }
    }

    fun toggleCardVisibility(cardGroup: CardGroup) {
        // Profiles card cannot be disabled
        if (cardGroup == CardGroup.Profiles) {
            return
        }

        updateState {
            val newVisibleCards =
                if (visibleCards.contains(cardGroup)) {
                    visibleCards - cardGroup
                } else {
                    visibleCards + cardGroup
                }
            // Save disabled items to settings
            saveDisabledItems(newVisibleCards)
            // Also save the current order if not already saved (indicates user has configured dashboard)
            if (Settings.dashboardItemOrder.isBlank()) {
                saveItemOrder(cardOrder)
            }
            copy(visibleCards = newVisibleCards)
        }
    }

    fun closeCardSettingsDialog() {
        updateState {
            copy(showCardSettingsDialog = false)
        }
    }

    fun reorderCards(newOrder: List<CardGroup>) {
        updateState {
            saveItemOrder(newOrder)
            copy(cardOrder = newOrder)
        }
    }

    fun resetCardOrder() {
        // Clear saved settings to restore defaults
        Settings.dashboardItemOrder = ""
        Settings.dashboardDisabledItems = emptySet()

        updateState {
            copy(
                cardOrder = getDefaultItemOrder(),
                visibleCards = CardGroup.values().toSet(),
            )
        }
    }

    // Helper functions for serialization
    private fun getDefaultItemOrder() = listOf(
        CardGroup.UploadTraffic,
        CardGroup.DownloadTraffic,
        CardGroup.Debug,
        CardGroup.Connections,
        CardGroup.SystemProxy,
        CardGroup.ClashMode,
        CardGroup.Profiles,
    )

    private fun loadItemOrder(): List<CardGroup> {
        val savedOrder = Settings.dashboardItemOrder
        if (savedOrder.isBlank()) {
            return getDefaultItemOrder()
        }

        return try {
            val jsonArray = JSONArray(savedOrder)
            val order = mutableListOf<CardGroup>()

            for (i in 0 until jsonArray.length()) {
                val itemName = jsonArray.getString(i)
                stringToCardGroup(itemName)?.let { order.add(it) }
            }

            // Add any new items that aren't in the saved order
            val allItems = CardGroup.values().toSet()
            val savedItems = order.toSet()
            val newItems = allItems - savedItems

            order.addAll(newItems)
            order
        } catch (e: JSONException) {
            getDefaultItemOrder()
        }
    }

    private fun saveItemOrder(order: List<CardGroup>) {
        val jsonArray = JSONArray()
        order.forEach { item ->
            jsonArray.put(cardGroupToString(item))
        }
        Settings.dashboardItemOrder = jsonArray.toString()
    }

    private fun loadDisabledItems(): Set<CardGroup> {
        val savedDisabled = Settings.dashboardDisabledItems
        // Filter out Profiles from disabled items (it cannot be disabled)
        return savedDisabled.mapNotNull { stringToCardGroup(it) }
            .filter { it != CardGroup.Profiles }
            .toSet()
    }

    fun refreshSubscription(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateState { copy(updatingProfileId = profileId) }

                val profile = ProfileManager.get(profileId) ?: return@launch

                val subscriptionParser = SubscriptionParser(V2RayUrlParser())
                val importHandler = SubscriptionImportHandler(Application.application, subscriptionParser)

                when (val result = importHandler.importSubscription(profile.typed.remoteURL, profileId)) {
                    is SubscriptionImportResult.Success -> {
                        android.util.Log.d("DashboardViewModel", "Import success: ${result.serverCount} servers, ${result.outbounds.size} outbounds")

                        val servers = mutableListOf<SubscriptionServer>()
                        for (outbound in result.outbounds) {
                            val type = outbound.optString("type", "")
                            if (type in listOf("vmess", "vless", "trojan", "shadowsocks")) {
                                val tag = outbound.optString("tag", "")
                                val server = outbound.optString("server", "")
                                val port = outbound.optInt("server_port", 0)
                                val uuid = when (type) {
                                    "vmess", "vless" -> outbound.optString("uuid", null)
                                    "trojan" -> outbound.optString("password", null)
                                    "shadowsocks" -> outbound.optString("password", null)
                                    else -> null
                                }
                                if (tag.isNotEmpty() && server.isNotEmpty() && port > 0) {
                                    servers.add(SubscriptionServer(tag, type, server, port, uuid))
                                    android.util.Log.d("DashboardViewModel", "Added server: $tag ($type) $server:$port")
                                }
                            }
                        }

                        withContext(Dispatchers.Main) {
                            updateState {
                                copy(
                                    updatingProfileId = null,
                                    updatedProfileId = profileId,
                                    subscriptionServers = servers,
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            delay(1500)
                            updateState { copy(updatedProfileId = null) }
                        }

                        if (profile.id == Settings.selectedProfile) {
                            withContext(Dispatchers.Main) {
                                sendGlobalEvent(UiEvent.RequestReconnectService)
                            }
                        }
                    }
                    is SubscriptionImportResult.Error -> {
                        sendErrorMessage("Failed to refresh subscription: ${result.message}")
                        withContext(Dispatchers.Main) {
                            updateState { copy(updatingProfileId = null) }
                        }
                    }
                }
            } catch (e: Exception) {
                sendErrorMessage("Failed to refresh subscription: ${e.message}")
                updateState { copy(updatingProfileId = null) }
            }
        }
    }

    fun showSubscriptionGroupsSheet() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = ProfileManager.get(uiState.value.selectedProfileId)
                if (profile == null) {
                    withContext(Dispatchers.Main) {
                        sendErrorMessage("No profile selected")
                    }
                    return@launch
                }

                android.util.Log.d("DashboardViewModel", "Force resolve: ${profile.typed.forceResolve}")

                loadServersFromConfig(profile)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error loading servers", e)
                sendErrorMessage("Error loading servers: ${e.message}")
            }
        }
    }

    private fun isIPAddress(address: String): Boolean {
        val ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        val ipv6Pattern = "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$"
        return address.matches(ipv4Pattern.toRegex()) || address.matches(ipv6Pattern.toRegex())
    }

    private fun resolveDomain(domain: String): String? = try {
        val addresses = java.net.InetAddress.getAllByName(domain)
        addresses.firstOrNull()?.hostAddress
    } catch (e: Exception) {
        android.util.Log.w("DashboardViewModel", "Failed to resolve domain: $domain", e)
        null
    }

    fun copyServerInfo(server: SubscriptionServer, context: android.content.Context) {
        val info = """Type: ${server.type.uppercase()}
Tag: ${server.tag}
Server: ${server.server}:${server.port}
${if (server.uuid != null) "${if (server.type in listOf("vmess", "vless")) "UUID" else "Password"}: ${server.uuid}" else ""}"""

        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Server Info", info.trim())
        clipboard.setPrimaryClip(clip)

        android.util.Log.d("DashboardViewModel", "Copied server info to clipboard")
    }

    fun editServer(server: SubscriptionServer) {
        android.util.Log.d("DashboardViewModel", "Editing server: ${server.tag}")
        val editState = ServerEditState(
            originalTag = server.tag,
            tag = server.tag,
            type = server.type,
            server = server.server,
            port = server.port,
            uuid = server.uuid,
        )
        updateState {
            copy(
                showServerEditDialog = true,
                editingServer = editState,
            )
        }
    }

    fun deleteServer(server: SubscriptionServer) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = ProfileManager.get(uiState.value.selectedProfileId)
                if (profile == null) {
                    sendErrorMessage("No profile selected")
                    return@launch
                }

                val configFile = java.io.File(profile.typed.path)
                val configJson = org.json.JSONObject(configFile.readText())
                val outbounds = configJson.optJSONArray("outbounds") ?: org.json.JSONArray()

                val newOutbounds = org.json.JSONArray()
                var deleted = false

                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)
                    val tag = outbound.optString("tag", "")

                    if (tag == server.tag) {
                        android.util.Log.d("DashboardViewModel", "Deleting server: $tag")
                        deleted = true
                    } else {
                        newOutbounds.put(outbound)
                    }
                }

                if (deleted) {
                    configJson.put("outbounds", newOutbounds)
                    io.nekohasekai.libbox.Libbox.checkConfig(configJson.toString())
                    configFile.writeText(configJson.toString(2))

                    // Force resolve aktifse IP'ye çevir
                    val finalContent = if (profile.typed.forceResolve) {
                        resolveDomainsInConfig(configJson.toString())
                    } else {
                        configJson.toString(2)
                    }
                    configFile.writeText(finalContent)

                    // Reload servers
                    loadServersFromConfig(profile)
                    sendGlobalEvent(UiEvent.RequestReconnectService)
                } else {
                    sendErrorMessage("Server not found")
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error deleting server", e)
                sendErrorMessage("Error deleting server: ${e.message}")
            }
        }
    }

    fun updateServerEditField(field: String, value: Any) {
        val currentEdit = uiState.value.editingServer ?: return
        val updatedEdit = when (field) {
            "tag" -> currentEdit.copy(tag = value as String)
            "server" -> currentEdit.copy(server = value as String)
            "port" -> currentEdit.copy(port = (value as String).toIntOrNull() ?: currentEdit.port)
            "uuid" -> currentEdit.copy(uuid = value as String)
            else -> currentEdit
        }
        updateState { copy(editingServer = updatedEdit) }
    }

    fun saveServerEdit() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val editState = uiState.value.editingServer ?: return@launch
                val profile = ProfileManager.get(uiState.value.selectedProfileId)
                if (profile == null) {
                    sendErrorMessage("No profile selected")
                    return@launch
                }

                val configFile = java.io.File(profile.typed.path)
                val configJson = org.json.JSONObject(configFile.readText())
                val outbounds = configJson.optJSONArray("outbounds") ?: org.json.JSONArray()

                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)
                    val tag = outbound.optString("tag", "")

                    if (tag == editState.originalTag) {
                        android.util.Log.d("DashboardViewModel", "Updating server: $tag")

                        when (editState.type) {
                            "vmess", "vless" -> outbound.put("uuid", editState.uuid ?: "")
                            "trojan", "shadowsocks" -> outbound.put("password", editState.uuid ?: "")
                        }
                        outbound.put("server", editState.server)
                        outbound.put("server_port", editState.port)
                        if (tag != editState.tag) {
                            outbound.put("tag", editState.tag)
                        }

                        break
                    }
                }

                io.nekohasekai.libbox.Libbox.checkConfig(configJson.toString())

                // Force resolve aktifse IP'ye çevir
                val finalContent = if (profile.typed.forceResolve) {
                    resolveDomainsInConfig(configJson.toString())
                } else {
                    configJson.toString(2)
                }
                configFile.writeText(finalContent)

                withContext(Dispatchers.Main) {
                    hideServerEditDialog()
                    loadServersFromConfig(profile)
                    sendGlobalEvent(UiEvent.RequestReconnectService)
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error saving server", e)
                sendErrorMessage("Error saving server: ${e.message}")
            }
        }
    }

    private fun resolveDomainsInConfig(configJson: String): String {
        return try {
            val jsonObject = org.json.JSONObject(configJson)
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
                        android.util.Log.d("DashboardViewModel", "Resolved $server -> $resolvedIP")
                    }
                }
            }

            jsonObject.toString(2)
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error resolving domains", e)
            configJson
        }
    }

    private fun loadServersFromConfig(profile: io.nekohasekai.sfa.database.Profile) {
        try {
            val configFile = java.io.File(profile.typed.path)
            val configJson = org.json.JSONObject(configFile.readText())
            val outbounds = configJson.optJSONArray("outbounds") ?: org.json.JSONArray()

            var selectedTag: String? = null
            val servers = mutableListOf<SubscriptionServer>()
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.getJSONObject(i)
                val type = outbound.optString("type", "")

                if (type == "selector") {
                    selectedTag = outbound.optString("selected", null)
                }

                if (type in listOf("vmess", "vless", "trojan", "shadowsocks")) {
                    var server = outbound.optString("server", "")
                    val tag = outbound.optString("tag", "")
                    val port = outbound.optInt("server_port", 0)
                    val uuid = when (type) {
                        "vmess", "vless" -> outbound.optString("uuid", null)
                        "trojan" -> outbound.optString("password", null)
                        "shadowsocks" -> outbound.optString("password", null)
                        else -> null
                    }

                    if (profile.typed.forceResolve && server.isNotEmpty() && !isIPAddress(server)) {
                        val resolvedIP = resolveDomain(server)
                        if (resolvedIP != null) {
                            server = resolvedIP
                        }
                    }

                    if (tag.isNotEmpty() && server.isNotEmpty() && port > 0) {
                        servers.add(SubscriptionServer(tag, type, server, port, uuid))
                    }
                }
            }

            updateState {
                copy(
                    subscriptionServers = servers,
                    showSubscriptionGroupsSheet = true,
                    selectedServerTag = selectedTag,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error loading servers", e)
        }
    }

    fun showServerEditDialog() {
        updateState { copy(showServerEditDialog = true) }
    }

    fun hideServerEditDialog() {
        updateState {
            copy(
                showServerEditDialog = false,
                editingServer = null,
            )
        }
    }

    fun hideSubscriptionGroupsSheet() {
        updateState { copy(showSubscriptionGroupsSheet = false) }
    }

    fun selectServer(serverTag: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = ProfileManager.get(uiState.value.selectedProfileId)
                if (profile == null) return@launch

                val configFile = java.io.File(profile.typed.path)
                val configJson = org.json.JSONObject(configFile.readText())
                val outbounds = configJson.optJSONArray("outbounds") ?: return@launch

                var found = false
                for (i in 0 until outbounds.length()) {
                    val outbound = outbounds.getJSONObject(i)
                    if (outbound.optString("type") == "selector") {
                        outbound.put("selected", serverTag)
                        found = true
                        android.util.Log.d("DashboardViewModel", "Selected server: $serverTag")
                        break
                    }
                }

                if (found) {
                    Libbox.checkConfig(configJson.toString())

                    val finalContent = if (profile.typed.forceResolve) {
                        resolveDomainsInConfig(configJson.toString())
                    } else {
                        configJson.toString(2)
                    }
                    configFile.writeText(finalContent)

                    withContext(Dispatchers.Main) {
                        updateState { copy(selectedServerTag = serverTag) }
                        sendGlobalEvent(UiEvent.RequestReconnectService)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Error selecting server", e)
                sendErrorMessage("Failed to select server: ${e.message}")
            }
        }
    }

    private fun saveDisabledItems(visibleCards: Set<CardGroup>) {
        val allItems = CardGroup.values().toSet()
        val actualVisibleCards = visibleCards + CardGroup.Profiles
        val disabledItems = allItems - actualVisibleCards
        Settings.dashboardDisabledItems = disabledItems.map { cardGroupToString(it) }.toSet()
    }

    private fun cardGroupToString(card: CardGroup): String = card.name

    private fun stringToCardGroup(name: String): CardGroup? = try {
        CardGroup.valueOf(name)
    } catch (e: IllegalArgumentException) {
        null
    }
}
