@file:OptIn(ExperimentalTextApi::class)

package com.example.widgetvoiceroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.widgetvoiceroid.R

// 日本語はすべて Zen Maru Gothic(本文 500、強調 700、見出し 900)
val ZenMaruGothic = FontFamily(
    Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
    Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
    Font(R.font.zen_maru_gothic_black, FontWeight.Black),
)

// 英字の見出し・ラベル・数字は Fredoka(可変フォントから太さを選ぶ)
val Fredoka = FontFamily(
    Font(
        R.font.fredoka, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.fredoka, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

// tokens.json の文字スタイル(アプリ画面ではスマホ幅の -sm を使う)
object VpType {
    private fun jp(size: TextUnit, weight: FontWeight, lineHeight: Float) = TextStyle(
        fontFamily = ZenMaruGothic,
        fontWeight = weight,
        fontSize = size,
        lineHeight = size * lineHeight,
        color = VpColor.ink,
    )

    val displayEnSm = TextStyle(
        fontFamily = Fredoka, fontWeight = FontWeight.Bold,
        fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = 0.02.em,
    )
    val eyebrowEn = TextStyle(
        fontFamily = Fredoka, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.26.em, color = VpColor.ink,
    )
    val nav = TextStyle(
        fontFamily = Fredoka, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 14.sp, letterSpacing = 0.06.em, color = VpColor.ink,
    )
    val wordmark = TextStyle(
        fontFamily = Fredoka, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 18.sp, letterSpacing = 0.04.em, color = VpColor.ink,
    )

    val headingXlSm = jp(32.sp, FontWeight.Black, 1.3f)
    val headingLSm = jp(26.sp, FontWeight.Black, 1.5f)
    val headingM = jp(22.sp, FontWeight.Black, 1.4f)
    val quote = jp(19.sp, FontWeight.Black, 1.6f)
    val buttonL = jp(18.sp, FontWeight.Black, 1.0f)
    val button = jp(16.sp, FontWeight.Black, 1.0f)
    val bubble = jp(16.sp, FontWeight.Bold, 1.7f)  // SpeechBubble の本文
    val body = jp(16.sp, FontWeight.Medium, 1.9f).copy(color = VpColor.inkMuted)
    val label = jp(14.sp, FontWeight.Bold, 1.5f)
    val tag = jp(13.sp, FontWeight.Bold, 1.5f)
    val caption = jp(12.sp, FontWeight.Medium, 1.8f).copy(color = VpColor.inkMuted)
}

// Material の部品が使う文字も Zen Maru Gothic にそろえる
val Typography = Typography().let { t ->
    fun TextStyle.zen() = copy(fontFamily = ZenMaruGothic)
    Typography(
        displayLarge = t.displayLarge.zen(), displayMedium = t.displayMedium.zen(), displaySmall = t.displaySmall.zen(),
        headlineLarge = t.headlineLarge.zen(), headlineMedium = t.headlineMedium.zen(), headlineSmall = t.headlineSmall.zen(),
        titleLarge = t.titleLarge.zen(), titleMedium = t.titleMedium.zen(), titleSmall = t.titleSmall.zen(),
        bodyLarge = VpType.label.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = t.bodyMedium.zen(), bodySmall = t.bodySmall.zen(),
        labelLarge = t.labelLarge.zen(), labelMedium = t.labelMedium.zen(), labelSmall = t.labelSmall.zen(),
    )
}
