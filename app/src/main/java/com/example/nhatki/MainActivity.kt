package com.example.nhatki

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nhatki.ui.screens.AddEditDiaryScreen
import com.example.nhatki.ui.screens.DiaryListScreen
import com.example.nhatki.ui.screens.LoginScreen
import com.example.nhatki.ui.screens.SettingsScreen
import com.example.nhatki.ui.theme.NhatkiTheme
import com.example.nhatki.ui.viewmodel.DiaryViewModel
import com.example.nhatki.ui.viewmodel.AuthViewModel
import com.example.nhatki.ui.components.AppBackground
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : AppCompatActivity() {
    
    private var isAppUnlocked by mutableStateOf(false)
    private var lastBackgroundTime: Long = 0

    private val googleLoginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { firebaseAuthWithGoogle(it) }
            } catch (e: ApiException) {
                Toast.makeText(this, "Đăng nhập thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val diaryViewModel: DiaryViewModel = viewModel()
            
            val user by authViewModel.user.collectAsState()
            val themeMode by diaryViewModel.themeMode.collectAsState()
            val backgroundUri by diaryViewModel.backgroundUri.collectAsState()
            val appLockEnabled by diaryViewModel.appLockEnabled.collectAsState()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        val currentTime = System.currentTimeMillis()
                        val timeInBackground = currentTime - lastBackgroundTime
                        
                        // Rule 1: Always lock on fresh start (if lastBackgroundTime is 0)
                        // Rule 2: Only lock if background time > 20 seconds
                        if (appLockEnabled) {
                            if (lastBackgroundTime == 0L || timeInBackground > 20000) {
                                isAppUnlocked = false
                                showBiometricPrompt()
                            } else {
                                isAppUnlocked = true
                            }
                        }
                    } else if (event == Lifecycle.Event.ON_STOP) {
                        lastBackgroundTime = System.currentTimeMillis()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(appLockEnabled) {
                if (!appLockEnabled) {
                    isAppUnlocked = true
                } else if (lastBackgroundTime == 0L) { 
                    // Initial check when app opens and setting is already ON
                    showBiometricPrompt()
                }
            }
            
            val darkTheme = when (themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            NhatkiTheme(
                darkTheme = darkTheme,
                dynamicColor = true
            ) {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.List) }

                LaunchedEffect(user) {
                    if (user != null) {
                        currentScreen = Screen.List
                    }
                }

                AppBackground(backgroundUri = backgroundUri) {
                    if (!isAppUnlocked && appLockEnabled) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Ứng dụng đang khóa. Vui lòng xác thực.", color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else if (user == null) {
                        LoginScreen {
                            startGoogleLogin()
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background.copy(alpha = if (backgroundUri != null) 0.5f else 1f)
                        ) {
                            when (val screen = currentScreen) {
                                is Screen.List -> {
                                    DiaryListScreen(
                                        viewModel = diaryViewModel,
                                        onAddEntry = { currentScreen = Screen.AddEdit(null) },
                                        onEntryClick = { entry -> currentScreen = Screen.AddEdit(entry.id) },
                                        onSettingsClick = { currentScreen = Screen.Settings },
                                        onRequireAuth = { onSuccess ->
                                            showBiometricPrompt(onSuccess)
                                        }
                                    )
                                }
                                is Screen.AddEdit -> {
                                    BackHandler {
                                        currentScreen = Screen.List
                                    }
                                    AddEditDiaryScreen(
                                        viewModel = diaryViewModel,
                                        entryId = screen.id,
                                        onBack = { currentScreen = Screen.List }
                                    )
                                }
                                is Screen.Settings -> {
                                    BackHandler {
                                        currentScreen = Screen.List
                                    }
                                    SettingsScreen(
                                        viewModel = diaryViewModel,
                                        onBack = { currentScreen = Screen.List }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startGoogleLogin() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("1090171177504-2vlp2vbb4j9mso0d2ihve3eu00jntam2.apps.googleusercontent.com")
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleLoginLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Chào mừng bạn!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Xác thực Firebase thất bại.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun showBiometricPrompt(onSuccess: (() -> Unit)? = null) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (onSuccess != null) {
                        onSuccess()
                    } else {
                        isAppUnlocked = true
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If it's a specific entry auth, we don't necessarily want to toast failure 
                    // if the user just cancelled, but for app lock we do.
                    if (onSuccess == null) {
                        Toast.makeText(this@MainActivity, "Xác thực thất bại: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Xác thực bảo mật")
            .setSubtitle("Vui lòng xác thực để tiếp tục")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

sealed class Screen {
    data object List : Screen()
    data class AddEdit(val id: String?) : Screen()
    data object Settings : Screen()
}
