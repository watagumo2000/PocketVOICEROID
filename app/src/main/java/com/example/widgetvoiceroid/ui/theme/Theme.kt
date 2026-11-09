package com.example.widgetvoiceroid.ui.theme

import android.provider.Settings
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// デザインシステムはライトテーマだけなので、端末がダークモードでも白地で表示する
private val ColorScheme = lightColorScheme(
    primary = VpColor.pink,
    onPrimary = VpColor.ink,
    secondary = VpColor.blue,
    onSecondary = VpColor.ink,
    background = VpColor.surface,
    onBackground = VpColor.ink,
    surface = VpColor.surface,
    onSurface = VpColor.ink,
    surfaceVariant = VpColor.surfaceSoft,
    onSurfaceVariant = VpColor.inkMuted,
    outline = VpColor.ink,
    outlineVariant = VpColor.line,
)

@Composable
fun VoiceroidPocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = Typography) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = VpColor.ink,
                backgroundColor = VpColor.pink,
            ),
            content = content,
        )
    }
}

// 端末で「アニメーションを削除」(アニメーション速度0)にしていたら、動きを止める
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
