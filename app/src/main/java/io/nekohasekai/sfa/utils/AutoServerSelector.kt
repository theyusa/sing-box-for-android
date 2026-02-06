package io.nekohasekai.sfa.utils

import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoServerSelector {

    suspend fun selectBestServerOnConnectionLoss(groupTag: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.i("AutoServerSelector", "Auto-selecting best server on connection loss for group: $groupTag")

                val profileId = ProfileManager.getProfileId() ?: return@withContext

                val client = Libbox.newStandaloneCommandClient()
                try {
                    client.urlTest(groupTag)
                    Log.i("AutoServerSelector", "URL test completed for group: $groupTag")
                } finally {
                    client.close()
                }
            } catch (e: Exception) {
                Log.e("AutoServerSelector", "Error auto-selecting server", e)
            }
        }
    }
}
