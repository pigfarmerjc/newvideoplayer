package com.pigfarmerjc.galleryplayer

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

object PermissionState {

    private fun hasSelectedVisualMediaPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasVideoPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED || hasSelectedVisualMediaPermission(context)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasImagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED || hasSelectedVisualMediaPermission(context)
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAnyStoragePermission(context: Context): Boolean {
        return hasVideoPermission(context) || hasImagesPermission(context)
    }

    val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

@Composable
fun PermissionScreen(
    context: Context,
    onGranted: () -> Unit
) {
    val preferences = remember(context) {
        context.getSharedPreferences("media_permission_state", Context.MODE_PRIVATE)
    }
    var requestedOnce by remember { mutableStateOf(preferences.getBoolean("requested_any_media", false)) }
    val activity = context.findComponentActivity()
    val missingPermissions = PermissionState.REQUIRED_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
    val permanentlyDenied = requestedOnce && missingPermissions.isNotEmpty() &&
        missingPermissions.none { activity?.shouldShowRequestPermissionRationale(it) == true }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        requestedOnce = true
        preferences.edit().putBoolean("requested_any_media", true).apply()
        if (result.values.any { it }) {
            onGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "允许访问本地媒体",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (permanentlyDenied) {
                        "权限已被系统关闭，请前往系统设置重新允许视频或图片访问。"
                    } else {
                        "仅用于在设备本地浏览、播放视频和查看图片，不会上传媒体内容。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        if (permanentlyDenied) openAppSettings(context)
                        else launcher.launch(PermissionState.REQUIRED_PERMISSIONS)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (permanentlyDenied) "打开系统设置" else "继续")
                }
            }
        }
    }
}

@Composable
fun InlinePermissionRequest(
    permissionType: String, // "video" or "image"
    onGranted: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (permissionType == "video") Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val preferenceKey = "requested_$permissionType"
    val preferences = remember(context) {
        context.getSharedPreferences("media_permission_state", Context.MODE_PRIVATE)
    }
    var requestedOnce by remember(permissionType) {
        mutableStateOf(preferences.getBoolean(preferenceKey, false))
    }
    val activity = context.findComponentActivity()
    val hasPermission = ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
    val permanentlyDenied = requestedOnce && !hasPermission &&
        activity?.shouldShowRequestPermissionRationale(permissionToRequest) != true

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        requestedOnce = true
        preferences.edit().putBoolean(preferenceKey, true).apply()
        if (isGranted) {
            onGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (permissionType == "video") "需要视频访问权限" else "需要图片访问权限",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (permanentlyDenied) {
                        "权限已被系统关闭，请在系统设置中重新允许。"
                    } else if (permissionType == "video") {
                        "允许后才能显示和播放设备中的本地视频。"
                    } else {
                        "允许后才能显示设备中的本地图片和 GIF。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (permanentlyDenied) openAppSettings(context)
                        else launcher.launch(permissionToRequest)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (permanentlyDenied) "打开系统设置" else "允许访问")
                }
            }
        }
    }
}
