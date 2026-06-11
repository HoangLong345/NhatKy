package com.example.nhatki.ui.screens

import android.Manifest
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.nhatki.R
import com.example.nhatki.data.model.DiaryEntry
import com.example.nhatki.ui.components.VideoPlayerDialog
import com.example.nhatki.ui.components.ZoomableBox
import com.example.nhatki.ui.viewmodel.DiaryViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditDiaryScreen(
    viewModel: DiaryViewModel,
    entryId: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf("") }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    var timestamp by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    
    val allTags by viewModel.allTags.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    
    var imageUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    var videoUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    var fileUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    
    var isLoading by remember { mutableStateOf(entryId != null) }
    var showImageDialog by remember { mutableStateOf<Uri?>(null) }
    var showVideoDialog by remember { mutableStateOf<Uri?>(null) }
    
    var isMediaMenuExpanded by remember { mutableStateOf(false) }

    val moods = listOf("😀", "😊", "😔", "😢", "😡", "😴", "🤔")
    
    val tagColors = remember {
        listOf(
            Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), 
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF009688), 
            Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF795548)
        )
    }

    fun getTagColor(tagName: String): Color {
        val index = abs(tagName.hashCode()) % tagColors.size
        return tagColors[index]
    }

    val showTagSuggestions = remember(content) {
        content.endsWith("#") || (content.isNotEmpty() && content.last().isLetterOrDigit() && content.contains("#") && !content.substringAfterLast("#").contains(" "))
    }
    val currentTagQuery = remember(content) {
        if (content.contains("#")) content.substringAfterLast("#") else ""
    }

    val annotatedContentTransformation = remember {
        VisualTransformation { text ->
            val builder = AnnotatedString.Builder()
            builder.append(text.text)
            val regex = Regex("#(\\w+)")
            regex.findAll(text.text).forEach { match ->
                builder.addStyle(
                    style = SpanStyle(
                        color = getTagColor(match.value), 
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
            TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                content = if (content.isEmpty()) spokenText else "$content $spokenText"
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val savedUris = uris.mapNotNull { saveFileToInternalStorage(context, it, "img")?.toString() }
        imageUris = imageUris + savedUris
        isMediaMenuExpanded = false
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val savedUris = uris.mapNotNull { uri ->
            val duration = getVideoDuration(context, uri)
            if (duration > 120 * 1000) {
                val limitMsg = context.getString(R.string.video_limit)
                Toast.makeText(context, limitMsg, Toast.LENGTH_SHORT).show()
                null
            } else {
                saveFileToInternalStorage(context, uri, "vid")?.toString()
            }
        }
        videoUris = videoUris + savedUris
        isMediaMenuExpanded = false
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val savedUris = uris.mapNotNull { saveFileToInternalStorage(context, it, "file")?.toString() }
        fileUris = fileUris + savedUris
        isMediaMenuExpanded = false
    }

    var tempCameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null) {
            imageUris = imageUris + Uri.fromFile(File(tempCameraFile!!)).toString()
        }
        isMediaMenuExpanded = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = File(context.filesDir, "cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            tempCameraFile = file.absolutePath
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Bạn cần cấp quyền Camera để chụp ảnh!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(entryId) {
        if (entryId != null) {
            val entry = viewModel.getDiaryById(entryId)
            if (entry != null) {
                title = entry.title
                content = entry.content
                mood = entry.mood
                isLocked = entry.isLocked
                timestamp = entry.timestamp
                imageUris = entry.imageUris.split(",").filter { it.isNotEmpty() }
                videoUris = entry.videoUris.split(",").filter { it.isNotEmpty() }
                fileUris = entry.fileUris.split(",").filter { it.isNotEmpty() }
            }
            isLoading = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) stringResource(R.string.add_diary) else stringResource(R.string.edit_diary)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { isLocked = !isLocked }) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Khóa bài viết",
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val extractedTags = Regex("#(\\w+)").findAll(content).map { it.value }.joinToString(",")
                        val entry = DiaryEntry(
                            id = entryId ?: UUID.randomUUID().toString(),
                            title = title,
                            content = content,
                            mood = mood,
                            timestamp = timestamp,
                            isLocked = isLocked,
                            tags = extractedTags,
                            imageUris = imageUris.joinToString(","),
                            videoUris = videoUris.joinToString(","),
                            fileUris = fileUris.joinToString(",")
                        )
                        if (entryId == null) {
                            viewModel.insert(entry)
                        } else {
                            viewModel.update(entry)
                        }
                        onBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = isMediaMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.padding(bottom = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            MediaMenuIcon(Icons.Default.AddPhotoAlternate, "Ảnh") { imageLauncher.launch(arrayOf("image/*")) }
                            MediaMenuIcon(Icons.Default.CameraAlt, "Chụp") { 
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                        val file = File(context.filesDir, "cam_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        tempCameraFile = file.absolutePath
                                        cameraLauncher.launch(uri)
                                    }
                                    else -> permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                            MediaMenuIcon(Icons.Default.Mic, "Nói") {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Đang nghe... Hãy nói gì đó!")
                                    }
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Thiết bị không hỗ trợ chuyển giọng nói!", Toast.LENGTH_SHORT).show()
                                }
                                isMediaMenuExpanded = false
                            }
                            MediaMenuIcon(Icons.Default.Movie, "Video") { videoLauncher.launch(arrayOf("video/*")) }
                            MediaMenuIcon(Icons.Default.UploadFile, "Tệp") { fileLauncher.launch(arrayOf("*/*")) }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { isMediaMenuExpanded = !isMediaMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isMediaMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Thêm phương tiện",
                        modifier = Modifier.rotate(if (isMediaMenuExpanded) 90f else 0f)
                    )
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                syncStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (showTagSuggestions) {
                            val filteredTags = allTags.filter { it.contains(currentTagQuery, ignoreCase = true) }
                            if (filteredTags.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredTags) { tag ->
                                        AssistChip(
                                            onClick = {
                                                val prefix = content.substringBeforeLast("#")
                                                content = "$prefix#$tag "
                                            },
                                            label = { Text(tag) },
                                            colors = AssistChipDefaults.assistChipColors(containerColor = getTagColor(tag).copy(alpha = 0.2f))
                                        )
                                    }
                                }
                            }
                        }

                        if (imageUris.isNotEmpty() || videoUris.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                imageUris.forEach { uri ->
                                    Box(modifier = Modifier.size(80.dp)) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clickable { showImageDialog = Uri.parse(uri) },
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { imageUris = imageUris - uri },
                                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                videoUris.forEach { uri ->
                                    Box(modifier = Modifier.size(80.dp).background(Color.Black).clickable { showVideoDialog = Uri.parse(uri) }) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                                        IconButton(
                                            onClick = { videoUris = videoUris - uri },
                                            modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }

                        TextField(
                            value = content,
                            onValueChange = { content = it },
                            placeholder = { Text(stringResource(R.string.how_is_your_day)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 300.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            visualTransformation = annotatedContentTransformation
                        )

                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            moods.forEach { m ->
                                Text(
                                    text = m,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable { mood = if (mood == m) "" else m }
                                        .background(
                                            if (mood == m) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(4.dp),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                }

                if (fileUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("📎 Tệp đính kèm:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        fileUris.forEach { uriString ->
                            val uri = Uri.parse(uriString)
                            InputChip(
                                selected = false,
                                onClick = { openFile(context, uri) },
                                label = { 
                                    Text(
                                        text = uri.lastPathSegment?.take(15) ?: "Tệp",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    ) 
                                },
                                colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { fileUris = fileUris - uriString },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Xóa")
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showImageDialog != null) {
        Dialog(onDismissRequest = { showImageDialog = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(500.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                ZoomableBox(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = showImageDialog,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
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
fun MediaMenuIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun openFile(context: android.content.Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        val fileUri = if (uri.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path!!))
        } else {
            uri
        }
        intent.setDataAndType(fileUri, context.contentResolver.getType(fileUri) ?: "*/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Mở tệp bằng..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Không thể mở tệp này!", Toast.LENGTH_SHORT).show()
    }
}

private fun getVideoDuration(context: android.content.Context, uri: Uri): Long {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        time?.toLong() ?: 0L
    } catch (e: Exception) {
        0L
    } finally {
        retriever.release()
    }
}

private fun saveFileToInternalStorage(context: android.content.Context, uri: Uri, prefix: String): Uri? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val fileName = "${prefix}_${System.currentTimeMillis()}"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        outputStream.close()
        inputStream?.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}
