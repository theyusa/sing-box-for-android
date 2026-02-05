package io.nekohasekai.sfa.test

data class AutoConnectSettings(
    val enabled: Boolean,
    val testInterval: Long,
    val autoSwitchEnabled: Boolean,
    val failureThreshold: Int,
    val testAllProfiles: Boolean,
    val onlyTestOnFailure: Boolean,
    val minLatencyThreshold: Int,
    val maxLatencyThreshold: Int,
)
