package dev.zihowl.dog.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.zihowl.dog.data.model.Task

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAll(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks")
    fun getAllList(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getById(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE subjectName = :subjectName")
    fun getBySubjectName(subjectName: String): List<Task>

    @Query("SELECT * FROM tasks WHERE owner = :owner")
    fun getAllForOwner(owner: String): List<Task>

    @Insert
    fun insert(task: Task): Long

    @Update
    fun update(task: Task): Int

    @Delete
    fun delete(task: Task): Int

    @Delete
    fun deleteAll(tasks: List<Task>): Int
}
