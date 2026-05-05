package dev.zihowl.dog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val entityType: String,
    val entityId: Int,
    val operation: String,
    val payloadJson: String,
    val owner: String = "",
    val timestamp: Date = Date()
) {
    companion object {
        const val OP_INSERT = "INSERT"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"

        const val TYPE_SUBJECT = "SUBJECT"
        const val TYPE_TASK = "TASK"
        const val TYPE_NOTE = "NOTE"
        const val TYPE_MANUAL_EVENT = "MANUAL_EVENT"
    }
}
