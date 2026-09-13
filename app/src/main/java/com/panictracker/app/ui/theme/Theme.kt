package com.panictracker.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 차분한 청록/남색 계열 — 불안을 자극하지 않는 색으로 골랐습니다. */
private val Primary = Color(0xFF2F6F8F)
private val PrimaryDark = Color(0xFF8FCBE4)
private val Secondary = Color(0xFF4F8A7A)
private val Tertiary = Color(0xFF7C6BA8)

private val LightColors = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    secondary = Color(0xFF9ED5C4),
    tertiary = Color(0xFFC3B5E8),
    background = Color(0xFF111517),
    surface = Color(0xFF1A1F22),
)

/** 증상 심각도·상태 표시에 쓰는 보조 색. */
object AccentColors {
    val calm = Color(0xFF3E8E7E)
    val watch = Color(0xFFD8A13A)
    val alert = Color(0xFFD2564F)
    val sleep = Color(0xFF5C6BC0)
    val nap = Color(0xFF9575CD)
}

@Composable
fun PanicTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 갤럭시(One UI)에서 사용자의 배경화면 색을 따라가도록 Material You 를 켭니다. */
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
