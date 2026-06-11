package com.example.nhatki.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import coil.compose.AsyncImage
import com.example.nhatki.R
import com.example.nhatki.ui.viewmodel.AuthViewModel
import com.example.nhatki.ui.viewmodel.DiaryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DiaryViewModel,
    onBack: () -> Unit,
) {
    val authViewModel: AuthViewModel = viewModel()
    val user by authViewModel.user.collectAsState()
    
    val themeMode by viewModel.themeMode.collectAsState()
    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val language by viewModel.language.collectAsState()
    val context = LocalContext.current

    val bgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { }
            viewModel.setBackgroundUri(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // User Profile Section
            user?.let { u ->
                SettingsGroup(title = "Tài khoản") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = u.photoUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.displayName ?: "Người dùng", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(u.email ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { authViewModel.logout() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Logout, contentDescription = "Đăng xuất", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Appearance Section
            SettingsGroup(title = stringResource(R.string.appearance), icon = Icons.Default.Brightness6) {
                SettingsCard {
                    SettingsOption(
                        label = stringResource(R.string.theme_light),
                        selected = themeMode == "Light",
                        onClick = { viewModel.setThemeMode("Light") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsOption(
                        label = stringResource(R.string.theme_dark),
                        selected = themeMode == "Dark",
                        onClick = { viewModel.setThemeMode("Dark") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsOption(
                        label = stringResource(R.string.theme_system),
                        selected = themeMode == "System",
                        onClick = { viewModel.setThemeMode("System") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Background Section
            SettingsGroup(title = "Hình nền ứng dụng", icon = Icons.Default.Image) {
                SettingsCard {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (backgroundUri != null) {
                            val configuration = LocalConfiguration.current
                            val screenWidth = configuration.screenWidthDp.dp
                            val screenHeight = configuration.screenHeightDp.dp
                            val aspectRatio = screenWidth / screenHeight
                            
                            // Calculate preview size based on aspect ratio
                            val previewWidth = 120.dp
                            val previewHeight = previewWidth / aspectRatio

                            Box(
                                modifier = Modifier
                                    .width(previewWidth)
                                    .height(previewHeight)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = backgroundUri,
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                FilledTonalIconButton(
                                    onClick = { viewModel.setBackgroundUri(null) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                                }
                                
                                // Mock UI lines to simulate phone look
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.fillMaxWidth(0.6f).height(4.dp).background(Color.White.copy(0.4f), CircleShape))
                                    Box(Modifier.fillMaxWidth(0.9f).height(4.dp).background(Color.White.copy(0.4f), CircleShape))
                                    Box(Modifier.fillMaxWidth(0.4f).height(4.dp).background(Color.White.copy(0.4f), CircleShape))
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        Button(
                            onClick = { bgLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (backgroundUri == null) "Chọn hình nền" else "Thay đổi hình nền", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Section
            SettingsGroup(title = "Bảo mật", icon = Icons.Default.Fingerprint) {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Khóa bằng sinh trắc học", style = MaterialTheme.typography.bodyMedium)
                            Text("Yêu cầu vân tay/khuôn mặt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) },
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Language Section
            SettingsGroup(title = stringResource(R.string.language), icon = Icons.Default.Language) {
                SettingsCard {
                    SettingsOption(
                        label = stringResource(R.string.lang_vi),
                        selected = language == "vi",
                        onClick = { 
                            viewModel.setLanguage("vi")
                            updateLocale(context, "vi")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsOption(
                        label = stringResource(R.string.lang_en),
                        selected = language == "en",
                        onClick = { 
                            viewModel.setLanguage("en")
                            updateLocale(context, "en")
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun SettingsGroup(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        RadioButton(
            selected = selected, 
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        )
    }
}

// Reuse updateLocale from previous logic
fun updateLocale(context: Context, lang: String) {
    val locale = Locale.forLanguageTag(lang)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
    
    var currentContext = context
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            currentContext.recreate()
            return
        }
        currentContext = currentContext.baseContext
    }
}
