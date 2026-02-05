package io.nekohasekai.sfa.database.preference

import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.test.AutoConnectSettings

object AutoConnectPreferences {
    private const val KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    private const val KEY_TEST_INTERVAL = "test_interval"
    private const val KEY_AUTO_SWITCH_ENABLED = "auto_switch_enabled"
    private const val KEY_FAILURE_THRESHOLD = "failure_threshold"
    private const val KEY_TEST_ALL_PROFILES = "test_all_profiles"
    private const val KEY_ONLY_TEST_ON_FAILURE = "only_test_on_failure"
    private const val KEY_MIN_LATENCY_THRESHOLD = "min_latency_threshold"
    private const val KEY_MAX_LATENCY_THRESHOLD = "max_latency_threshold"

    private val store = Settings.dataStore

    var enabled: Boolean
        get() = store.getBoolean(KEY_AUTO_CONNECT_ENABLED, false)
        set(value) = store.putBoolean(KEY_AUTO_CONNECT_ENABLED, value)

    var testInterval: Long
        get() = store.getLong(KEY_TEST_INTERVAL, 5 * 60 * 1000L)
        set(value) = store.putLong(KEY_TEST_INTERVAL, value)

    var autoSwitchEnabled: Boolean
        get() = store.getBoolean(KEY_AUTO_SWITCH_ENABLED, false)
        set(value) = store.putBoolean(KEY_AUTO_SWITCH_ENABLED, value)

    var failureThreshold: Int
        get() = store.getInt(KEY_FAILURE_THRESHOLD, 3)
        set(value) = store.putInt(KEY_FAILURE_THRESHOLD, value)

    var testAllProfiles: Boolean
        get() = store.getBoolean(KEY_TEST_ALL_PROFILES, false)
        set(value) = store.putBoolean(KEY_TEST_ALL_PROFILES, value)

    var onlyTestOnFailure: Boolean
        get() = store.getBoolean(KEY_ONLY_TEST_ON_FAILURE, true)
        set(value) = store.putBoolean(KEY_ONLY_TEST_ON_FAILURE, value)

    var minLatencyThreshold: Int
        get() = store.getInt(KEY_MIN_LATENCY_THRESHOLD, 0)
        set(value) = store.putInt(KEY_MIN_LATENCY_THRESHOLD, value)

    var maxLatencyThreshold: Int
        get() = store.getInt(KEY_MAX_LATENCY_THRESHOLD, 3000)
        set(value) = store.putInt(KEY_MAX_LATENCY_THRESHOLD, value)

    fun toSettings(): AutoConnectSettings = AutoConnectSettings(
        enabled = enabled,
        testInterval = testInterval,
        autoSwitchEnabled = autoSwitchEnabled,
        failureThreshold = failureThreshold,
        testAllProfiles = testAllProfiles,
        onlyTestOnFailure = onlyTestOnFailure,
        minLatencyThreshold = minLatencyThreshold,
        maxLatencyThreshold = maxLatencyThreshold,
    )

    fun fromSettings(settings: AutoConnectSettings) {
        enabled = settings.enabled
        testInterval = settings.testInterval
        autoSwitchEnabled = settings.autoSwitchEnabled
        failureThreshold = settings.failureThreshold
        testAllProfiles = settings.testAllProfiles
        onlyTestOnFailure = settings.onlyTestOnFailure
        minLatencyThreshold = settings.minLatencyThreshold
        maxLatencyThreshold = settings.maxLatencyThreshold
    }
}
