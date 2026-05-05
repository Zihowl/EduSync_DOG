package dev.zihowl.dog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.Date

@Entity(tableName = "manual_events")
data class ManualEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val location: String? = null,
    val startTime: String,
    val endTime: String,
    val frequencyType: String,
    val dayOfWeek: Int? = null,
    val date: Date? = null,
    val owner: String = ""
) : Serializable {
    companion object {
        const val FREQUENCY_UNIQUE = "UNIQUE"
        const val FREQUENCY_RECURRENT = "RECURRENT"
    }
}
