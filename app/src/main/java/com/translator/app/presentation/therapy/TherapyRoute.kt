// Путь: app/src/main/java/com/translator/app/presentation/therapy/TherapyRoute.kt
//
// Связка: достаёт TherapyViewModel (Hilt), собирает состояние, запрашивает
// разрешение на микрофон, запускает сессию и прокидывает колбэки в «глупый»
// TherapyScreen. Это точка входа экрана в навигации.
// ═══════════════════════════════════════════════════════════════════════════
package com.translator.app.presentation.therapy

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TherapyRoute(
    onOpenResources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TherapyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicPermissionGranted()
            viewModel.startSession()
        }
    }

    // При входе на экран — стартуем сессию (запросив микрофон при необходимости).
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startSession()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    TherapyScreen(
        state = state,
        onToggleMute = viewModel::toggleMute,
        onEndSession = viewModel::endSession,
        onOpenResources = onOpenResources,
        modifier = modifier
    )
}
