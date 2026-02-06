package io.nekohasekai.sfa.utils

import android.content.Context
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed class SubscriptionImportResult {
    data class Success(val serverCount: Int, val outbounds: List<org.json.JSONObject>) : SubscriptionImportResult()
    data class Error(val message: String) : SubscriptionImportResult()
}

class SubscriptionImportHandler(
    private val context: Context,
    private val subscriptionParser: SubscriptionParser,
) {
    suspend fun importSubscription(url: String, profileId: Long): SubscriptionImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d("SubscriptionImportHandler", "Fetching URL: $url")
            val content = HTTPClient().use { it.getString(url) }
            Log.d("SubscriptionImportHandler", "Fetched content length: ${content.length}")

            val newOutbounds = subscriptionParser.parseSubscriptionUrl(content)

            if (newOutbounds.isEmpty()) {
                return@withContext SubscriptionImportResult.Error("No valid servers found")
            }

            val profile = ProfileManager.get(profileId) ?: run {
                return@withContext SubscriptionImportResult.Error("Profile not found")
            }

            val configFile = File(profile.typed.path)
            val configJson = JSONObject(configFile.readText())

            val existingOutbounds = configJson.optJSONArray("outbounds") ?: JSONArray()
            val nonSelectorOutbounds = JSONArray()

            for (i in 0 until existingOutbounds.length()) {
                val outbound = existingOutbounds.getJSONObject(i)
                val type = outbound.optString("type")

                if (type in listOf("selector", "urltest", "direct", "block", "dns")) {
                    nonSelectorOutbounds.put(outbound)
                }
            }

            val finalOutbounds = JSONArray()

            for (i in 0 until nonSelectorOutbounds.length()) {
                finalOutbounds.put(nonSelectorOutbounds.getJSONObject(i))
            }

            newOutbounds.forEach { outbound ->
                finalOutbounds.put(outbound)
            }

            configJson.put("outbounds", finalOutbounds)

            Libbox.checkConfig(configJson.toString())

            // Force resolve aktifse domain'leri IP'ye çevir
            val finalContent = if (profile.typed.forceResolve) {
                Log.d("SubscriptionImportHandler", "Force resolve enabled, resolving domains to IPs")
                resolveDomainToIP(configJson.toString())
            } else {
                Log.d("SubscriptionImportHandler", "Force resolve disabled, keeping domains")
                configJson.toString(2)
            }

            configFile.writeText(finalContent)

            profile.typed.lastUpdated = java.util.Date()
            ProfileManager.update(profile)

            Libbox.newStandaloneCommandClient().serviceReload()

            SubscriptionImportResult.Success(newOutbounds.size, newOutbounds)
        } catch (e: Exception) {
            Log.e("SubscriptionImportHandler", "Error importing subscription", e)
            SubscriptionImportResult.Error(e.message ?: "Failed to import subscription")
        }
    }

    private fun resolveDomainToIP(configJson: String): String {
        return try {
            val jsonObject = JSONObject(configJson)
            val outbounds = jsonObject.optJSONArray("outbounds") ?: return configJson

            val skipTypes = setOf(
                "selector",
                "urltest",
                "direct",
                "block",
                "dns",
                "reject",
                "blackhole",
                "loopback",
            )

            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.getJSONObject(i)
                val server = outbound.optString("server", "")
                val type = outbound.optString("type", "")

                if (type in skipTypes) {
                    continue
                }

                if (server.isNotEmpty() && !isIPAddress(server)) {
                    val resolvedIP = resolveDomain(server)
                    if (resolvedIP != null) {
                        outbound.put("server", resolvedIP)
                        Log.d("SubscriptionImportHandler", "Resolved $server -> $resolvedIP")
                    }
                }
            }

            jsonObject.toString(2)
        } catch (e: Exception) {
            Log.e("SubscriptionImportHandler", "Error resolving domains", e)
            configJson
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
        null
    }
}
