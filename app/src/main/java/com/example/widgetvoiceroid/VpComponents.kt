package com.example.widgetvoiceroid

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.VpType
import com.example.widgetvoiceroid.ui.theme.contentColorOn
import com.example.widgetvoiceroid.ui.theme.rememberReduceMotion

// ---- VOICEROID POCKET の部品 ----
// フチはすべて ink の実線、影はぼかさずに右下へずらすだけ

val PillShape = RoundedCornerShape(999.dp)
val RadiusMd = RoundedCornerShape(12.dp)
val RadiusLg = RoundedCornerShape(16.dp)
val RadiusXl = RoundedCornerShape(20.dp)
val Radius2xl = RoundedCornerShape(28.dp)

// サムネイルで顔が真ん中に来る位置(上から14%)
val FaceAlignment = BiasAlignment(0f, 0.14f * 2 - 1)

// キャラの差し色:キャラ色が水色寄りなら pink、それ以外は blue
val Character.subColor: Color
    get() {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        return if (hsv[1] > 0.1f && hsv[0] in 165f..265f) VpColor.pink else VpColor.blue
    }

// 地の色・フチ・ずらし影をまとめて付ける
fun Modifier.vpSurface(
    shape: Shape,
    fill: Color = VpColor.surface,
    border: Dp = 3.dp,
    shadow: DpOffset? = null,
    shadowColor: Color = VpColor.ink,
): Modifier {
    val withShadow = if (shadow == null) this else this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(shadow.x.toPx(), shadow.y.toPx()) { drawOutline(outline, shadowColor) }
    }
    val filled = withShadow.clip(shape).background(fill)
    return if (border > 0.dp) filled.border(border, VpColor.ink, shape) else filled
}

// 点線の枠(画像の置き場所や「追加」の枠)
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 2.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
    )
    val inset = width.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = size.copy(width = size.width - inset * 2, height = size.height - inset * 2),
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke,
    )
}

// ---- Button ----
// ピル型。押すと影の分だけ沈む。large は画面に1つだけの主ボタン
@Composable
fun VpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = VpColor.surface,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val sink = if (pressed && enabled && !reduceMotion) 3.dp else 0.dp

    val shadow = when {
        !enabled -> null
        large -> DpOffset(5.dp - sink, 5.dp - sink)
        else -> DpOffset(0.dp, 5.dp - sink)
    }
    val fill = if (enabled) color else VpColor.line

    Box(
        modifier = modifier
            .padding(end = if (large) 5.dp else 0.dp, bottom = 5.dp)  // 影のぶんの場所
            .offset(x = if (large) sink else 0.dp, y = sink)
            .heightIn(min = if (large) 58.dp else 54.dp)
            .vpSurface(PillShape, fill = fill, border = 3.dp, shadow = shadow)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (large) 34.dp else 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = (if (large) VpType.buttonL else VpType.button).copy(
                color = if (enabled) contentColorOn(fill) else VpColor.inkMuted,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

// 小さいボタン(角丸12、高さ44、影なし)。selected で塗りつぶす(押せるフィルター用)
@Composable
fun VpSmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectedColor: Color = VpColor.pink,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        selected -> selectedColor
        pressed -> VpColor.surfaceSoft
        else -> VpColor.surface
    }
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .vpSurface(RadiusMd, fill = fill, border = 2.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = VpType.label.copy(color = contentColorOn(fill)), maxLines = 1)
    }
}

// ---- Badge / Tag ----
@Composable
fun VpBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = VpType.tag.copy(color = VpColor.onInk, fontSize = 11.sp, lineHeight = 15.sp),
        modifier = modifier
            .background(VpColor.ink, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun VpTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = VpType.tag.copy(fontSize = 12.sp, lineHeight = 16.sp),
        modifier = modifier
            .vpSurface(PillShape, border = 2.dp)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

// ---- SectionTitle ----
// 白抜きの大きな英字と、白座布団の日本語ラベル。alt で影の色が入れ替わる
@Composable
fun SectionTitle(en: String, ja: String, modifier: Modifier = Modifier, alt: Boolean = false) {
    val enShadow = if (alt) VpColor.blue else VpColor.pink
    val jaShadow = if (alt) VpColor.pink else VpColor.blue
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = if (alt) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedDisplayText(en, VpType.displayEnSm, enShadow)
        Text(
            ja,
            style = VpType.headingM,
            modifier = Modifier
                .padding(end = 5.dp, bottom = 5.dp)
                .vpSurface(RoundedCornerShape(4.dp), border = 2.5.dp, shadow = DpOffset(5.dp, 5.dp), shadowColor = jaShadow)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

// 塗り surface ＋ ink のフチ ＋ 色のずらし影の英字
@Composable
fun OutlinedDisplayText(text: String, style: TextStyle, shadowColor: Color, modifier: Modifier = Modifier) {
    // フチは文字の外側に出す(フチを先に描いて、上から塗りを重ねる)
    val strokeWidth = with(LocalDensity.current) { 3.dp.toPx() * 2 }
    Box(modifier) {
        Text(text, style = style.copy(color = shadowColor), modifier = Modifier.offset(6.dp, 6.dp))
        Text(text, style = style.copy(color = VpColor.ink, drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round)))
        Text(text, style = style.copy(color = VpColor.surface))
    }
}

// ---- CharacterThumb ----
// キャラ色の地に顔を切り抜いて見せる。選ぶと4dp浮いて影が出る
@Composable
fun CharacterThumb(
    character: Character,
    number: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    val lift by animateDpAsState(if (selected) (-4).dp else 0.dp, label = "lift")
    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp, bottom = 4.dp)
            .offset(y = lift)
            .size(size)
            .vpSurface(
                RadiusXl,
                fill = character.themeColor,
                shadow = if (selected) DpOffset(4.dp, 4.dp) else null,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        FaceImage(
            character = character,
            face = character.defaultFace,
            maxSize = 400,
            contentScale = ContentScale.Crop,
            alignment = FaceAlignment,
            placeholderTextSize = 28.sp,
            modifier = Modifier.fillMaxSize(),
        )
        if (number != null) {
            Text(
                "%02d".format(number),
                style = VpType.nav.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .vpSurface(PillShape, border = 2.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

// 「追加」用の点線のタイル
@Composable
fun AddTile(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 80.dp) {
    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp, bottom = 4.dp)
            .size(size)
            .clip(RadiusXl)
            .dashedBorder(VpColor.placeholder, 20.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", style = VpType.headingM)
            Text(text, style = VpType.caption.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        }
    }
}

// ---- NamePlate ----
// 立ち絵の横に添える名前:小さい英字ラベルの下に、マーカー下線を引いた名前
@Composable
fun NamePlate(character: Character, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.widthIn(max = 240.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("CHARACTER", style = VpType.eyebrowEn.copy(fontSize = 12.sp, lineHeight = 16.sp))
        Text(
            character.name.ifBlank { "?" },
            style = VpType.headingLSm.copy(fontSize = 24.sp, lineHeight = 32.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 文字の下 40% にキャラ色を敷く
            modifier = Modifier.drawBehind {
                drawRect(
                    color = character.themeColor,
                    topLeft = Offset(0f, size.height * 0.6f),
                    size = size.copy(height = size.height * 0.4f),
                )
            },
        )
    }
}

// ---- SpeechBubble ----
// 左上に名前の札、右下にキャラ色のずらし影。本文は常に3行分の高さ
@Composable
fun SpeechBubble(
    character: Character,
    text: String,
    modifier: Modifier = Modifier,
    showNext: Boolean = true,
    onPlayVoice: (() -> Unit)? = null,  // ボイスがあるときだけ「ボイスを聞く」ボタンを出す
) {
    Box(modifier.padding(top = 16.dp, end = 5.dp, bottom = 5.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .vpSurface(RadiusXl, shadow = DpOffset(5.dp, 5.dp), shadowColor = character.themeColor)
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
        ) {
            val lineHeight = VpType.bubble.lineHeight
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = with(LocalDensity.current) { (lineHeight * 3).toDp() }),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(text, style = VpType.bubble, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        // 名前の札
        Text(
            character.name.ifBlank { "?" },
            style = VpType.tag.copy(fontWeight = FontWeight.Black, color = contentColorOn(character.themeColor)),
            modifier = Modifier
                .padding(start = 16.dp)
                .offset(y = (-16).dp)
                .vpSurface(RoundedCornerShape(8.dp), fill = character.themeColor, border = 2.5.dp)
                .padding(horizontal = 12.dp, vertical = 3.dp),
        )
        if (onPlayVoice != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
                    .size(44.dp)
                    .clip(PillShape)
                    .clickable(role = Role.Button, onClickLabel = "ボイスを聞く", onClick = onPlayVoice),
                contentAlignment = Alignment.Center,
            ) {
                SpeakerIcon(Modifier.size(22.dp))
            }
        } else if (showNext) {
            Text(
                "▼",
                fontSize = 11.sp,
                color = VpColor.ink,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 6.dp),
            )
        }
    }
}

// スピーカーのアイコン(デザインシステムの「ボイスを聞く」と同じ形。18×18 の座標で描く)
@Composable
fun SpeakerIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 18f
        val stroke = Stroke(width = 1.8f * u, join = StrokeJoin.Round, cap = StrokeCap.Round)
        val body = Path().apply {
            moveTo(3 * u, 7 * u); lineTo(6 * u, 7 * u); lineTo(10 * u, 3 * u)
            lineTo(10 * u, 15 * u); lineTo(6 * u, 11 * u); lineTo(3 * u, 11 * u); close()
        }
        drawPath(body, VpColor.pink)
        drawPath(body, VpColor.ink, style = stroke)
        val wave = Path().apply {
            moveTo(12.5f * u, 6 * u)
            quadraticTo(14.5f * u, 9 * u, 12.5f * u, 12 * u)
        }
        drawPath(wave, VpColor.ink, style = stroke)
    }
}

// ---- DiagonalBands / 水玉 / キラキラ ----
// 太いキャラ色の平行四辺形と細い差し色の帯。上辺が右にずれる向き
@Composable
fun DiagonalBands(main: Color, sub: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(0.27f * w, 0f); lineTo(0.76f * w, 0f)
                lineTo(0.55f * w, h); lineTo(0.06f * w, h); close()
            },
            main,
        )
        drawPath(
            Path().apply {
                moveTo(0.7412f * w, 0f); lineTo(0.84f * w, 0f)
                lineTo(0.6788f * w, h); lineTo(0.58f * w, h); close()
            },
            sub,
        )
    }
}

@Composable
fun Dots(modifier: Modifier = Modifier, color: Color = VpColor.line) {
    Canvas(modifier) {
        val step = 24.dp.toPx()
        val r = 2.5.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(color, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

// 4点星。1.8秒で0.6倍〜1倍に拡大縮小する
@Composable
fun Sparkle(color: Color, modifier: Modifier = Modifier, delayMillis: Int = 0) {
    val reduceMotion = rememberReduceMotion()
    val scale = if (reduceMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "sparkle")
        val s by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(delayMillis),
            ),
            label = "scale",
        )
        s
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * scale
        val k = r / 6
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            cubicTo(c.x + k * 0.66f, c.y - k, c.x + k, c.y - k * 0.66f, c.x + r, c.y)
            cubicTo(c.x + k, c.y + k * 0.66f, c.x + k * 0.66f, c.y + k, c.x, c.y + r)
            cubicTo(c.x - k * 0.66f, c.y + k, c.x - k, c.y + k * 0.66f, c.x - r, c.y)
            cubicTo(c.x - k, c.y - k * 0.66f, c.x - k * 0.66f, c.y - k, c.x, c.y - r)
            close()
        }
        drawPath(path, color, alpha = 0.5f + (scale - 0.6f) * 1.25f)
    }
}

// ---- 入力欄 ----
@Composable
fun VpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = VpType.label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else 5,
            textStyle = VpType.bubble,
            cursorBrush = SolidColor(VpColor.ink),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .vpSurface(RadiusLg, border = 2.5.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = VpType.bubble.copy(color = VpColor.inkMuted, fontWeight = FontWeight.Medium))
                    }
                    inner()
                }
            },
        )
    }
}

// ---- スイッチ ----
@Composable
fun VpSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 24.dp else 0.dp, label = "knob")
    Box(
        modifier = modifier
            .size(width = 56.dp, height = 32.dp)
            .vpSurface(PillShape, fill = if (checked) VpColor.pink else VpColor.surfaceSoft, border = 2.5.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(22.dp)
                .vpSurface(PillShape, fill = if (checked) VpColor.ink else VpColor.surface, border = 2.5.dp),
        )
    }
}

// ---- ダイアログ(白いカード) ----
@Composable
fun VpDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(end = 8.dp, bottom = 8.dp)
                .vpSurface(Radius2xl, shadow = DpOffset(8.dp, 8.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(title, style = VpType.headingM)
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    }
}
