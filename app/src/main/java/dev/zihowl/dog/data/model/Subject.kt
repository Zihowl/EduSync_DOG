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
    val owner: String = "",
    /** Materia oficial sincronizada desde el servidor: solo lectura. */
    val isOfficial: Boolean = false,
    /** Grupo/subgrupo del que proviene una materia oficial (para re-sync). */
    val sourceGroupId: Int? = null,
    /** Salón asignado a la materia oficial (para detectar cambios de salón). */
    val classroom: String? = null
) : Serializable
