package com.example.widgetvoiceroid

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.widgetvoiceroid.ui.theme.Fredoka
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.ZenMaruGothic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// この大きさ以上なら内蔵画像をそのまま使う(小さいサムネイルは縮小して読み込み、メモリを節約する)
private const val FULL_SIZE = 1600

// 表情の画像を表示する。画像がなければ名前の頭文字を出す
@Composable
fun FaceImage(
    character: Character,
    face: Face?,
    modifier: Modifier = Modifier,
    maxSize: Int = FULL_SIZE,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    placeholderTextSize: TextUnit = 48.sp,
) {
    val ref = face?.image
    val builtInRes = (ref as? ImageRef.BuiltIn)?.let { BuiltInCharacters.drawableFor(it) }

    when {
        ref == null || (ref is ImageRef.BuiltIn && builtInRes == null) ->
            Placeholder(character, modifier, placeholderTextSize)

        builtInRes != null && maxSize >= FULL_SIZE -> Image(
            painter = painterResource(builtInRes),
            contentDescription = character.name,
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier,
        )

        else -> {
            val context = LocalContext.current
            val bitmap by produceState<ImageBitmap?>(null, ref, maxSize) {
                value = withContext(Dispatchers.IO) {
                    ImageStore.loadBitmap(context, ref, maxSize)?.asImageBitmap()
                }
            }
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = character.name,
                    contentScale = contentScale,
                    alignment = alignment,
                    modifier = modifier,
                )
            } else {
                Box(modifier)
            }
        }
    }
}

// 画像のないキャラは、名前の頭文字を ink のフチ付きで大きく出す
@Composable
private fun Placeholder(character: Character, modifier: Modifier, textSize: TextUnit) {
    val initial = character.name.trim().take(1).ifEmpty { "?" }
    val isAscii = initial.first().code < 128
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = initial,
            color = VpColor.ink,
            fontSize = textSize,
            fontFamily = if (isAscii) Fredoka else ZenMaruGothic,
            fontWeight = if (isAscii) FontWeight.Bold else FontWeight.Black,
        )
    }
}
