package com.example.widgetvoiceroid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.VpType
import com.example.widgetvoiceroid.ui.theme.contentColorOn

// カラーコード(#RRGGBB)と ARGB の変換
object ColorCode {
    // "#FFB3BE"・"ffb3be"・"#FB3"(3桁の省略形)・全角の "＃ＦＦＢ３ＢＥ" を読む。読めなければ null
    fun parse(text: String): Int? {
        val half = text.map { if (it in '！'..'～') it - 0xFEE0 else it }.joinToString("")
        val hex = half.trim().removePrefix("#")
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF000000 or full.toLong(16)).toInt()
    }

    fun format(color: Int): String = "#%06X".format(color and 0xFFFFFF)
}

// テーマ色を自分で決めるダイアログ(色相・鮮やかさ・明るさのスライダーと、カラーコードの入力)
@Composable
fun ColorPickerDialog(
    initial: Int,
    name: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val start = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(start[1]) }
    var value by rememberSaveable { mutableFloatStateOf(start[2]) }
    var code by rememberSaveable { mutableStateOf(ColorCode.format(initial)) }

    val color = Color.hsv(hue, saturation, value)
    // スライダーを動かした直後に呼ぶので、color(描いたときの値)ではなく今の値から作る
    fun syncCode() {
        code = ColorCode.format(Color.hsv(hue, saturation, value).toArgb())
    }

    VpDialog(
        title = "テーマ色を決める",
        onDismiss = onDismiss,
        buttons = {
            VpSmallButton("キャンセル", onClick = onDismiss)
            VpButton("決定", color = color, onClick = { onPick(color.toArgb()) })
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ColorPreview(color, name)

            ColorSlider(
                label = "色相",
                fraction = hue / 360f,
                track = Brush.horizontalGradient(
                    (0..6).map { Color.hsv(it * 60f % 360f, saturation.coerceAtLeast(0.35f), value.coerceAtLeast(0.6f)) },
                ),
                thumb = color,
                onChange = { hue = it * 360f; syncCode() },
            )
            ColorSlider(
                label = "鮮やかさ",
                fraction = saturation,
                track = Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))),
                thumb = color,
                onChange = { saturation = it; syncCode() },
            )
            ColorSlider(
                label = "明るさ",
                fraction = value,
                track = Brush.horizontalGradient(listOf(Color.hsv(hue, saturation, 0f), Color.hsv(hue, saturation, 1f))),
                thumb = color,
                onChange = { value = it; syncCode() },
            )

            VpTextField(
                value = code,
                onValueChange = { text ->
                    code = text
                    ColorCode.parse(text)?.let { parsed ->
                        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed, it) }
                        // 白黒(鮮やかさ0)のときは色相が決まらないので、今の色相のままにする
                        if (hsv[1] > 0f) hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                    }
                },
                label = "カラーコード",
                placeholder = "#FFB3BE",
                // 英数字のキーボードを出す(全角で入っても読める)
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
            )
            Text(
                if (ColorCode.parse(code) == null) {
                    "#RRGGBB の形で入れてください(例:#FFB3BE)"
                } else {
                    "淡い色がおすすめです。濃い色にすると、上に乗る文字が白になります。"
                },
                style = VpType.caption,
            )
        }
    }
}

// 選んだ色の見え方(名前の札と、色の上の文字)
@Composable
private fun ColorPreview(color: Color, name: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 5.dp, bottom = 5.dp)
            .vpSurface(RadiusXl, fill = color, shadow = DpOffset(5.dp, 5.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                name.trim().ifEmpty { "キャラの名前" },
                style = VpType.tag.copy(color = VpColor.ink),
                modifier = Modifier
                    .vpSurface(RoundedCornerShapeSmall, border = 2.dp)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Text("こんにちは！この色でどうかな？", style = VpType.bubble.copy(color = contentColorOn(color)))
        }
    }
}

private val RoundedCornerShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)

// グラデーションのつまみ付きスライダー。fraction は 0〜1
@Composable
private fun ColorSlider(
    label: String,
    fraction: Float,
    track: Brush,
    thumb: Color,
    onChange: (Float) -> Unit,
) {
    val latest by rememberUpdatedState(onChange)
    val thumbSize = 30.dp
    val thumbPx = with(LocalDensity.current) { thumbSize.toPx() }
    fun fractionAt(x: Float, width: Int): Float =
        ((x - thumbPx / 2) / (width - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = VpType.label)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbSize + 6.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { latest(it.coerceIn(0f, 1f)); true }
                }
                .pointerInput(Unit) {
                    detectTapGestures { latest(fractionAt(it.x, size.width)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        latest(fractionAt(change.position.x, size.width))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = thumbSize / 2 - 6.dp)
                    .height(18.dp)
                    .clip(PillShape)
                    .background(track)
                    .border(2.5.dp, VpColor.ink, PillShape),
            )
            Box(
                Modifier
                    .offset(x = (maxWidth - thumbSize) * fraction)
                    .size(thumbSize)
                    .vpSurface(CircleShape, fill = thumb, border = 3.dp),
            )
        }
    }
}
