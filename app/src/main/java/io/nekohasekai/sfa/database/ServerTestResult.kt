package io.nekohasekai.sfa.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "server_test_results",
    indices = [
        Index(value = ["serverTag", "profileId"], unique = true),
        Index(value = ["profileId", "is_success"]),
        Index(value = ["tested_at"]),
    ],
)
data class ServerTestResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo
    val serverTag: String,

    @ColumnInfo
    val profileId: Long,

    @ColumnInfo(name = "is_success")
    val isSuccess: Boolean,

    @ColumnInfo(name = "latency_ms")
    val latencyMs: Int? = null,

    @ColumnInfo(name = "error_type")
    val errorType: String? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "tested_at")
    val testedAt: Long = System.currentTimeMillis(),
)
