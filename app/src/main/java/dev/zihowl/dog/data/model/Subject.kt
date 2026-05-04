package dev.zihowl.dog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val professorName: String? = null,
    val schedule: String? = null,
    val tasksPending: Int = 0,
    val notesCount: Int = 0,
    val owner: String = ""
) : Serializable
