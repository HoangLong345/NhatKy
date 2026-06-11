package com.example.nhatki.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName
import java.util.UUID

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val mood: String, // 😀, 😢, 😡, 😴
    val timestamp: Long = System.currentTimeMillis(),
    val imageUris: String = "", 
    val videoUris: String = "",
    val fileUris: String = "",
    val tags: String = "", 
    
    @get:PropertyName("isLocked")
    @set:PropertyName("isLocked")
    var isLocked: Boolean = false,

    val password: String? = null,
    val userId: String = "" // For cloud sync identification
) {
    // Firebase needs a no-argument constructor
    constructor() : this(UUID.randomUUID().toString(), "", "", "", System.currentTimeMillis(), "", "", "", "", false, null, "")
}
