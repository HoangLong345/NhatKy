package com.example.nhatki.data.dao

import androidx.room.*
import com.example.nhatki.data.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE title LIKE :searchQuery OR content LIKE :searchQuery")
    fun searchDiaries(searchQuery: String): Flow<List<DiaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiary(entry: DiaryEntry)

    @Update
    suspend fun updateDiary(entry: DiaryEntry)

    @Delete
    suspend fun deleteDiary(entry: DiaryEntry)

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun getDiaryById(id: String): DiaryEntry?
}
