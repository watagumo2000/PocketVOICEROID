package com.example.widgetvoiceroid

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.contentColorOn
import kotlinx.coroutines.launch

// ---- ウィジェットごとに「どのキャラか」「今表示しているセリフ」を保存する ----
private val CharacterIdKey = stringPreferencesKey("character_id")
private val LineIdKey = stringPreferencesKey("line_id")

// ウィジェットに渡す画像の大きさの上限(大きすぎるとホーム画面に送れない)
private const val WIDGET_IMAGE_SIZE = 720

class CharacterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = CharacterRepository.get(context)

        provideContent {
            val prefs = currentState<Preferences>()
            val characters by repository.characters.collectAsState()
            // 選ばれたキャラが消されていたら、先頭のキャラで代用する
            val character = characters.firstOrNull { it.id == prefs[CharacterIdKey] }
                ?: characters.firstOrNull()

            if (character == null) {
                EmptyContent()
                return@provideContent
            }

            val line = character.line(prefs[LineIdKey]) ?: character.firstLine
            val scope = rememberCoroutineScope()

            WidgetContent(
                context = context,
                character = character,
                line = line,
                onTap = {
                    scope.launch {
                        val next = character.randomLine(except = line)
                        updateAppWidgetState(context, id) {
                            it[CharacterIdKey] = character.id
                            it[LineIdKey] = next.id
                        }
                        update(context, id)
                        // ボイスがあれば鳴らす(アプリの設定でオフなら鳴らさない)
                        VoicePlayer.play(context, next.voice)
                    }
                },
            )
        }
    }

    // 外から「この状況だよ」と伝えて、しゃべってもらう
    suspend fun speak(context: Context, id: GlanceId, situation: Situation) {
        val repository = CharacterRepository.get(context)
        updateAppWidgetState(context, id) { prefs ->
            val character = repository.find(prefs[CharacterIdKey])
                ?: repository.characters.value.firstOrNull()
                ?: return@updateAppWidgetState
            prefs[LineIdKey] = character.speakTo(situation, except = character.line(prefs[LineIdKey])).id
        }
        update(context, id)
    }

    companion object {
        // このウィジェットに表示するキャラを決める
        suspend fun setCharacter(context: Context, id: GlanceId, characterId: String) {
            updateAppWidgetState(context, id) {
                it[CharacterIdKey] = characterId
                it.remove(LineIdKey)
            }
            CharacterWidget().update(context, id)
        }

        suspend fun characterIdOf(context: Context, id: GlanceId): String? {
            val prefs = CharacterWidget().getAppWidgetState<Preferences>(context, id)
            return prefs[CharacterIdKey]
        }
    }
}

// ---- 見た目 ----
// VOICEROID POCKET のデザイン:太い ink のフチ、斜めの帯、名前の札付きの吹き出し
// (ウィジェットでは自前のフォントやフチ線が使えないので、色を重ねて形を作る)
private val Ink = VpColor.ink
private val InkText = ColorProvider(day = VpColor.ink, night = VpColor.ink)

@Composable
private fun WidgetContent(context: Context, character: Character, line: Line, onTap: () -> Unit) {
    val face = character.face(line.faceId)
    val bands = remember(character.color) { bandsBitmap(character.color, character.subColor.toArgb()) }

    // 外側の ink が 3dp のフチになる
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Ink)
            .cornerRadius(28.dp)
            .padding(3.dp)
            .clickable(onTap),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(VpColor.surface)
                .cornerRadius(25.dp),
        ) {
            Image(
                provider = ImageProvider(bands),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val provider = remember(face?.image) { face?.image?.let { imageProvider(context, it) } }
                if (provider != null) {
                    Image(
                        provider = provider,
                        contentDescription = character.name,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                    )
                } else {
                    // 画像がないキャラは名前の頭文字を出す
                    Box(
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = character.name.trim().take(1).ifEmpty { "?" },
                            style = TextStyle(color = InkText, fontSize = 64.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                WidgetBubble(character, line.text)
            }
        }
    }
}

// 吹き出し:キャラ色のずらし影 → ink のフチ → 白い中身 の順に重ねる
@Composable
private fun WidgetBubble(character: Character, text: String) {
    Box(modifier = GlanceModifier.fillMaxWidth().height(108.dp)) {
        Box(modifier = GlanceModifier.fillMaxSize().padding(start = 5.dp, top = 5.dp)) {
            Box(GlanceModifier.fillMaxSize().background(character.themeColor).cornerRadius(20.dp)) {}
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(end = 5.dp, bottom = 5.dp)) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Ink)
                    .cornerRadius(20.dp)
                    .padding(3.dp),
            ) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(VpColor.surface)
                        .cornerRadius(17.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // 名前の札
                    Box(GlanceModifier.background(Ink).cornerRadius(8.dp).padding(2.dp)) {
                        Box(
                            GlanceModifier
                                .background(character.themeColor)
                                .cornerRadius(6.dp)
                                .padding(horizontal = 10.dp, vertical = 1.dp),
                        ) {
                            val onColor = contentColorOn(character.themeColor)
                            Text(
                                text = character.name.ifBlank { "?" },
                                style = TextStyle(
                                    color = ColorProvider(day = onColor, night = onColor),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = text,
                        style = TextStyle(color = InkText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

// キャラがひとりもいないとき
@Composable
private fun EmptyContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Ink)
            .cornerRadius(28.dp)
            .padding(3.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(VpColor.surface)
                .cornerRadius(25.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "キャラがまだいません\nタップしてアプリで追加できます！",
                style = TextStyle(
                    color = InkText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

// 斜め2色の帯を描いた背景画像(アプリの DiagonalBands と同じ形)
private val bandsCache = mutableMapOf<Pair<Int, Int>, Bitmap>()

private fun bandsBitmap(main: Int, sub: Int): Bitmap = synchronized(bandsCache) {
    bandsCache.getOrPut(main to sub) {
        val w = 300f
        val h = 400f
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun band(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float, color: Int) {
            paint.color = color
            canvas.drawPath(
                Path().apply {
                    moveTo(topLeft * w, 0f); lineTo(topRight * w, 0f)
                    lineTo(bottomRight * w, h); lineTo(bottomLeft * w, h); close()
                },
                paint,
            )
        }
        band(0.27f, 0.76f, 0.55f, 0.06f, main)
        band(0.7412f, 0.84f, 0.6788f, 0.58f, sub)
        bitmap
    }
}

private fun imageProvider(context: Context, ref: ImageRef): ImageProvider? = when (ref) {
    is ImageRef.BuiltIn -> BuiltInCharacters.drawableFor(ref)?.let { ImageProvider(it) }
    is ImageRef.Stored -> ImageStore.loadBitmap(context, ref, WIDGET_IMAGE_SIZE)?.let { ImageProvider(it) }
}

// ---- Androidとの窓口 ----
class CharacterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CharacterWidget()

    // ウィジェットが置かれたり更新されたりしたら、定期おしゃべりを予約しておく
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        SpeakWorker.schedule(context)
    }
}
