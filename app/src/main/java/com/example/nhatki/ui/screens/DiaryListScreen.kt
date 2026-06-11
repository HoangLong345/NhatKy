package com.example.nhatki.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.nhatki.R
import com.example.nhatki.data.model.DiaryEntry
import com.example.nhatki.ui.components.VideoPlayerDialog
import com.example.nhatki.ui.viewmodel.DiaryViewModel
import com.example.nhatki.ui.viewmodel.AuthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    viewModel: DiaryViewModel,
    onAddEntry: () -> Unit,
    onEntryClick: (DiaryEntry) -> Unit,
    onSettingsClick: () -> Unit,
    onRequireAuth: (onSuccess: () -> Unit) -> Unit
) {
    val authViewModel: AuthViewModel = viewModel()
    val user by authViewModel.user.collectAsState()
    
    val entries by viewModel.allEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    var showVideoDialog by remember { mutableStateOf<Uri?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.my_diary)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = { isSearchActive = false },
                    active = isSearchActive,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isSearchActive) 0.dp else 16.dp, vertical = if (isSearchActive) 0.dp else 8.dp),
                    colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                ) {
                    val filteredTags = allTags.filter { it.contains(searchQuery, ignoreCase = true) }
                    if (filteredTags.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filteredTags) { tag ->
                                ListItem(
                                    headlineContent = { Text(tag) },
                                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.onSearchQueryChange(tag)
                                        isSearchActive = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_diary))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Show Promo Card ONLY if user is NOT logged in
            if (user == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "Lưu giữ kỷ niệm của bạn trên mây",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Đăng nhập để đồng bộ ảnh, video và bài viết trên mọi thiết bị.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isEmpty()) stringResource(R.string.no_entry) else stringResource(R.string.no_result),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries) { entry ->
                        DiaryItem(
                            entry = entry,
                            onClick = { 
                                if (entry.isLocked) {
                                    onRequireAuth { onEntryClick(entry) }
                                } else {
                                    onEntryClick(entry)
                                }
                            },
                            onDelete = { 
                                if (entry.isLocked) {
                                    onRequireAuth { viewModel.delete(entry) }
                                } else {
                                    viewModel.delete(entry)
                                }
                            },
                            onWatchVideo = { uri ->
                                if (entry.isLocked) {
                                    onRequireAuth { showVideoDialog = uri }
                                } else {
                                    showVideoDialog = uri
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showVideoDialog != null) {
        VideoPlayerDialog(uri = showVideoDialog!!) {
            showVideoDialog = null
        }
    }
}

@Composable
fun DiaryItem(
    entry: DiaryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onWatchVideo: (Uri) -> Unit
) {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
    ) {
        Column {
            val imageList = if (entry.isLocked) emptyList() else entry.imageUris.split(",").filter { it.isNotEmpty() }
            if (imageList.isNotEmpty()) {
                AsyncImage(
                    model = imageList[0],
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val annotatedContent = remember(entry.content, entry.isLocked) {
                        if (entry.isLocked) {
                            AnnotatedString("Nội dung đã được khóa. Chạm để mở.")
                        } else {
                            val builder = AnnotatedString.Builder(entry.content)
                            val regex = Regex("#(\\w+)")
                            regex.findAll(entry.content).forEach { match ->
                                builder.addStyle(
                                    style = SpanStyle(
                                        color = Color(0xFF6200EE),
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    start = match.range.first,
                                    end = match.range.last + 1
                                )
                            }
                            builder.toAnnotatedString()
                        }
                    }
                    
                    Text(
                        text = annotatedContent,
                        style = if (entry.isLocked) MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.outline) else MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (entry.mood.isNotEmpty() && !entry.isLocked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.mood,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    if (entry.isLocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BadgeItem("🔒 Đã khóa", containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    }

                    val imageCount = imageList.size
                    val videoList = if (entry.isLocked) emptyList() else entry.videoUris.split(",").filter { it.isNotEmpty() }
                    val videoCount = videoList.size
                    val fileCount = if (entry.isLocked) 0 else entry.fileUris.split(",").filter { it.isNotEmpty() }.size
                    
                    if ((imageCount > 0) || (videoCount > 0) || (fileCount > 0)) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (imageCount > 0) BadgeItem("🖼️ $imageCount")
                            if (videoCount > 0) {
                                Box(modifier = Modifier.clickable { onWatchVideo(Uri.parse(videoList[0])) }) {
                                    BadgeItem("🎞️ $videoCount", containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                }
                            }
                            if (fileCount > 0) BadgeItem("📎 $fileCount", containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun BadgeItem(text: String, containerColor: Color = MaterialTheme.colorScheme.primaryContainer) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
