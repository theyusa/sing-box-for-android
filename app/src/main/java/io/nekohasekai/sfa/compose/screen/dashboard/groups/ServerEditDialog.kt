package io.nekohasekai.sfa.compose.screen.dashboard.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R

data class ServerEditState(
    val tag: String = "",
    val server: String = "",
    val server_port: Int = 443,
    val uuid: String = "",
    val security: String = "auto",
    val alter_id: Int = 0,
    val network: String = "tcp",
    val tlsEnabled: Boolean = false,
    val tlsServerName: String = "",
    val tlsInsecure: Boolean = false,
    val transportType: String = "",
    val transportPath: String = "",
    val transportHost: String = "",
    val protocolType: String = "vmess",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditDialog(
    serverState: ServerEditState,
    onDismiss: () -> Unit,
    onSave: (ServerEditState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(serverState) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        title = { Text("V2Ray Sunucu Düzenle") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.tag,
                    onValueChange = { state = state.copy(tag = it) },
                    label = { Text("Sunucu Adı") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = state.server,
                    onValueChange = { state = state.copy(server = it) },
                    label = { Text("Sunucu Adresi") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = state.server_port.toString(),
                    onValueChange = {
                        state = state.copy(server_port = it.toIntOrNull() ?: 443)
                    },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = state.uuid,
                    onValueChange = { state = state.copy(uuid = it) },
                    label = { Text("UUID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (state.protocolType == "vmess") {
                    OutlinedTextField(
                        value = state.alter_id.toString(),
                        onValueChange = {
                            state = state.copy(alter_id = it.toIntOrNull() ?: 0)
                        },
                        label = { Text("Alter ID") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                Text("Ağ Ayarları", style = MaterialTheme.typography.titleSmall)

                val networkOptions = listOf("tcp", "ws", "grpc")
                var networkExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = networkExpanded,
                    onExpandedChange = { networkExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = state.network,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ağ Türü") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = networkExpanded) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = networkExpanded,
                        onDismissRequest = { networkExpanded = false },
                    ) {
                        networkOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    state = state.copy(network = option)
                                    networkExpanded = false
                                },
                            )
                        }
                    }
                }

                Text("TLS Ayarları", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("TLS Aktif")
                    Switch(
                        checked = state.tlsEnabled,
                        onCheckedChange = { state = state.copy(tlsEnabled = it) },
                    )
                }

                if (state.tlsEnabled) {
                    OutlinedTextField(
                        value = state.tlsServerName,
                        onValueChange = { state = state.copy(tlsServerName = it) },
                        label = { Text("TLS Server Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("TLS Güvensiz Mod")
                        Switch(
                            checked = state.tlsInsecure,
                            onCheckedChange = { state = state.copy(tlsInsecure = it) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(state) }) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("İptal")
            }
        },
    )
}
