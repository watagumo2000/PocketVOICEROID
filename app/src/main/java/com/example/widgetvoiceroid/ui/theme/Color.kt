package com.example.widgetvoiceroid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// VOICEROID POCKET デザインシステムの色(tokens.json と同じ値)
object VpColor {
    val surface = Color(0xFFFFFFFF)      // 画面の地色
    val surfaceSoft = Color(0xFFF7F5FB)  // 少し沈めたい場所
    val line = Color(0xFFECEAF3)         // 水玉・区切り線・未選択のUI(文字には使わない)
    val placeholder = Color(0xFFB9B5CC)  // 画像の置き場所を示す点線(文字には使わない)
    val pink = Color(0xFFFFB3BE)         // 茜のカラー
    val blue = Color(0xFFB3E4FF)         // 葵のカラー
    val ink = Color(0xFF3D3A55)          // 文字・フチ・ずらし影
    val inkMuted = Color(0xFF5E5A78)     // 説明文(surface と surface-soft の上だけ)
    val onInk = Color(0xFFFFFFFF)        // ink で塗った面の上の文字
    val onInkMuted = Color(0xFFD5D2E3)
}

// キャラ色の面の上に置く文字の色。淡い色なら ink、濃い色(古いデータなど)なら白
fun contentColorOn(color: Color): Color =
    if (color.luminance() > 0.35f) VpColor.ink else VpColor.onInk
