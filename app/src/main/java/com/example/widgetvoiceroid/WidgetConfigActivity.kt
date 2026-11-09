@file:OptIn(ExperimentalLayoutApi::class)

package com.example.widgetvoiceroid

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.widgetvoiceroid.ui.theme.VoiceroidPocketTheme
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.VpType
import kotlinx.coroutines.launch

// ウィジェットを置くとき(長押しで「設定」したとき)に、表示するキャラを選ぶ画面
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // 何も選ばずに戻ったら、ウィジェットは置かれない
        setResult(RESULT_CANCELED, result)

        val repository = CharacterRepository.get(this)
        val glanceManager = GlanceAppWidgetManager(this)

        enableVpEdgeToEdge()
        setContent {
            val characters by repository.characters.collectAsState()
            var currentId by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                currentId = runCatching {
                    CharacterWidget.characterIdOf(this@WidgetConfigActivity, glanceManager.getGlanceIdBy(appWidgetId))
                }.getOrNull()
            }

            VoiceroidPocketTheme {
                Box(Modifier.fillMaxSize().background(VpColor.surface)) {
                    CharacterPicker(
                        characters = characters,
                        currentId = currentId,
                        onPick = { character ->
                            lifecycleScope.launch {
                                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                                CharacterWidget.setCharacter(this@WidgetConfigActivity, glanceId, character.id)
                                SpeakWorker.schedule(this@WidgetConfigActivity)
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        },
                        onOpenApp = {
                            startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterPicker(
    characters: List<Character>,
    currentId: String?,
    onPick: (Character) -> Unit,
    onOpenApp: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SectionTitle(en = "CHARACTER", ja = "キャラを選ぶ")
        Text("ホーム画面のウィジェットに出すキャラを選んでください！", style = VpType.body)

        if (characters.isEmpty()) {
            Text("キャラがまだいません。アプリでキャラを追加できます！", style = VpType.body)
            VpButton("アプリを開く ›", onClick = onOpenApp, color = VpColor.pink, large = true)
            return@Column
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            characters.forEachIndexed { index, character ->
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CharacterThumb(
                        character = character,
                        number = index + 1,
                        selected = character.id == currentId,
                        onClick = { onPick(character) },
                        size = 96.dp,
                    )
                    Text(
                        character.name,
                        style = VpType.label,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
