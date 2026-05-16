package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.zihowl.dog.data.model.Note

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes")
    fun getAll(): LiveData<List<Note>>

    @Query("SELECT * FROM notes")
    fun getAllList(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getById(id: Int): Note?

    @Query("SELECT * FROM notes WHERE subjectName = :subjectName")
    fun getBySubjectName(subjectName: String): List<Note>

    @Query("SELECT * FROM notes WHERE owner = :owner")
    fun getAllForOwner(owner: String): List<Note>

    @Query("SELECT * FROM notes WHERE owner = :owner")
    fun getAllForOwnerLive(owner: String): LiveData<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE owner = :owner")
    fun countForOwner(owner: String): Int

    @Query("DELETE FROM notes WHERE owner = :owner")
    fun deleteByOwner(owner: String): Int

    @Query("UPDATE notes SET owner = :to WHERE owner = :from")
    fun reassignOwner(from: String, to: String): Int

    @Insert
    fun insert(note: Note): Long

    @Update
    fun update(note: Note): Int

    @Delete
    fun delete(note: Note): Int

    @Delete
    fun deleteAll(notes: List<Note>): Int
}
