package com.example.nhatki.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nhatki.data.dao.DiaryDao
import com.example.nhatki.data.database.AppDatabase
import com.example.nhatki.data.model.DiaryEntry
import com.example.nhatki.data.preferences.SettingsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val diaryDao: DiaryDao = AppDatabase.getDatabase(application).diaryDao()
    private val settingsManager = SettingsManager(application)
    
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _cloudEntries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    
    val themeMode: StateFlow<String> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")

    val language: StateFlow<String> = settingsManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "vi")

    val backgroundUri: StateFlow<String?> = settingsManager.backgroundUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appLockEnabled: StateFlow<Boolean> = settingsManager.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsManager.setThemeMode(mode)
        }
    }

    fun setBackgroundUri(uri: String?) {
        viewModelScope.launch {
            settingsManager.setBackgroundUri(uri)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAppLockEnabled(enabled)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            settingsManager.setLanguage(lang)
        }
    }
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val allEntries: StateFlow<List<DiaryEntry>> = _searchQuery
        .debounce(300L)
        .flatMapLatest { query ->
            val localFlow = if (query.isBlank()) {
                diaryDao.getAllEntries()
            } else {
                diaryDao.searchDiaries("%$query%")
            }
            
            combine(localFlow, _cloudEntries) { local, cloud ->
                val merged = (local + cloud).distinctBy { it.id }
                if (query.isBlank()) {
                    merged.sortedByDescending { it.timestamp }
                } else {
                    // Re-filter cloud entries since they are not filtered by SQL
                    merged.filter { it.title.contains(query, true) || it.content.contains(query, true) }
                        .sortedByDescending { it.timestamp }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initial fetch if user is already logged in
        auth.currentUser?.uid?.let { 
            Log.d("DiaryViewModel", "Khởi tạo fetch dữ liệu cho user: $it")
            fetchCloudEntries(it) 
        }
        
        // Listen for auth changes to fetch data
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                Log.d("DiaryViewModel", "Phát hiện đăng nhập: $uid. Bắt đầu fetch...")
                fetchCloudEntries(uid)
            } else {
                Log.d("DiaryViewModel", "Đã đăng xuất. Xóa dữ liệu cloud tạm thời.")
                _cloudEntries.value = emptyList()
            }
        }
    }

    private fun fetchCloudEntries(userId: String) {
        firestore.collection("diaries")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("DiaryViewModel", "Lỗi fetch cloud: ${error.message}")
                    return@addSnapshotListener
                }
                value?.let { snapshot ->
                    val entries = snapshot.toObjects<DiaryEntry>()
                    Log.d("DiaryViewModel", "Đã tải ${entries.size} bài viết từ Cloud")
                    _cloudEntries.value = entries
                    
                    // Sync cloud data to local database
                    viewModelScope.launch {
                        entries.forEach { cloudEntry ->
                            diaryDao.insertDiary(cloudEntry)
                        }
                    }
                }
            }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus = _syncStatus.asStateFlow()

    fun insert(entry: DiaryEntry) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: ""
                val entryWithUser = entry.copy(userId = userId)
                diaryDao.insertDiary(entryWithUser)
                
                if (userId.isNotEmpty()) {
                    _syncStatus.value = "Đang đồng bộ..."
                    val uploadedEntry = uploadMediaAndGetEntry(entryWithUser)
                    uploadToCloud(uploadedEntry)
                    _syncStatus.value = "Đã đồng bộ"
                }
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "Lỗi khi lưu: ${e.message}")
                _syncStatus.value = "Lỗi đồng bộ: ${e.message}"
            }
        }
    }

    private suspend fun uploadMediaAndGetEntry(entry: DiaryEntry): DiaryEntry {
        val userId = auth.currentUser?.uid ?: return entry
        
        val remoteImageUris = uploadFiles(entry.imageUris.split(","), "images", userId)
        val remoteVideoUris = uploadFiles(entry.videoUris.split(","), "videos", userId)
        val remoteFileUris = uploadFiles(entry.fileUris.split(","), "files", userId)
        
        return entry.copy(
            imageUris = remoteImageUris.joinToString(","),
            videoUris = remoteVideoUris.joinToString(","),
            fileUris = remoteFileUris.joinToString(",")
        )
    }

    private suspend fun uploadFiles(uris: List<String>, folder: String, userId: String): List<String> {
        return uris.filter { it.isNotEmpty() }.map { uriString ->
            if (uriString.startsWith("http")) return@map uriString // Already remote
            
            val uri = Uri.parse(uriString)
            val fileName = uri.lastPathSegment ?: "${System.currentTimeMillis()}"
            val ref = storage.reference.child("$folder/$userId/$fileName")
            
            try {
                ref.putFile(uri).await()
                ref.downloadUrl.await().toString()
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "Lỗi tải lên $uri: ${e.message}")
                uriString // Fallback to local if failed
            }
        }
    }

    private suspend fun uploadToCloud(entry: DiaryEntry) {
        try {
            firestore.collection("diaries")
                .document(entry.id)
                .set(entry)
                .await()
        } catch (e: Exception) {
            Log.e("DiaryViewModel", "Lỗi tải lên Firestore: ${e.message}")
            throw e
        }
    }

    fun update(entry: DiaryEntry) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: ""
                val entryWithUser = entry.copy(userId = userId)
                diaryDao.updateDiary(entryWithUser)
                
                if (userId.isNotEmpty()) {
                    _syncStatus.value = "Đang cập nhật..."
                    val uploadedEntry = uploadMediaAndGetEntry(entryWithUser)
                    uploadToCloud(uploadedEntry)
                    // Update local again with remote URLs to avoid re-uploading
                    diaryDao.updateDiary(uploadedEntry)
                    _syncStatus.value = "Đã cập nhật"
                }
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "Lỗi khi cập nhật: ${e.message}")
                _syncStatus.value = "Lỗi cập nhật: ${e.message}"
            }
        }
    }

    fun delete(entry: DiaryEntry) {
        viewModelScope.launch {
            try {
                diaryDao.deleteDiary(entry)
                if (auth.currentUser != null) {
                    firestore.collection("diaries")
                        .document(entry.id)
                        .delete()
                        .await()
                }
            } catch (e: Exception) {
                Log.e("DiaryViewModel", "Lỗi khi xóa: ${e.message}")
            }
        }
    }

    suspend fun getDiaryById(id: String): DiaryEntry? {
        return diaryDao.getDiaryById(id)
    }

    val moodStatistics: Flow<Map<String, Int>> = allEntries.map { entries ->
        entries.groupBy { it.mood }.mapValues { it.value.size }
    }

    val allTags: StateFlow<List<String>> = allEntries.map { entries ->
        entries.flatMap { it.tags.split(",") }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
