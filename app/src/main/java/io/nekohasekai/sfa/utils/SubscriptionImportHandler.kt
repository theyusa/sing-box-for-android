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
    data class Success(val serverCount: Int) : SubscriptionImportResult()
    data class Error(val message: String) : SubscriptionImportResult()
}

class SubscriptionImportHandler(
    private val context: Context,
    private val subscriptionParser: SubscriptionParser,
) {
    suspend fun importSubscription(url: String, profileId: Long): SubscriptionImportResult = withContext(Dispatchers.IO) {
        try {
            val content = HTTPClient().use { it.getString(url) }

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

            configFile.writeText(configJson.toString(2))

            profile.typed.lastUpdated = java.util.Date()
            ProfileManager.update(profile)

            Libbox.newStandaloneCommandClient().serviceReload()

            SubscriptionImportResult.Success(newOutbounds.size)
        } catch (e: Exception) {
            Log.e("SubscriptionImportHandler", "Error importing subscription", e)
            SubscriptionImportResult.Error(e.message ?: "Failed to import subscription")
        }
    }
}
