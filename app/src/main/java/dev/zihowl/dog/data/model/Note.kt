package dev.zihowl.dog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String? = null,
    val subjectName: String? = null,
    val owner: String = "",
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: Long? = null
) : Serializable
