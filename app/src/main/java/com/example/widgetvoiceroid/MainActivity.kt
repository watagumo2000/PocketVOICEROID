package com.example.widgetvoiceroid

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.widgetvoiceroid.ui.theme.VoiceroidPocketTheme
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.VpType
import com.example.widgetvoiceroid.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    // 久しぶりに開いたので、あいさつを待っている
    private val greetPending = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpeakWorker.schedule(this)
        enableVpEdgeToEdge()
        setContent {
            VoiceroidPocketTheme {
                Box(Modifier.fillMaxSize().background(VpColor.surface)) {
                    App(
                        greetPending = greetPending.value,
                        onGreeted = { greetPending.value = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Visits.checkIn(this)) greetPending.value = true
    }
}

// 白い地に合わせて、ステータスバーの文字を濃い色にする
fun ComponentActivity.enableVpEdgeToEdge() {
    val transparent = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(transparent, transparent),
        navigationBarStyle = SystemBarStyle.light(transparent, transparent),
    )
}

// 通知の許可をお願いする(Android 13以降)
@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// 編集画面で「新しいキャラを作る」ことを表す印
private const val NEW_CHARACTER = "__new__"

@Composable
fun App(greetPending: Boolean = false, onGreeted: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { CharacterRepository.get(context) }
    val characters by repository.characters.collectAsState()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // 編集中のキャラのID(新しく作るときは NEW_CHARACTER)。null ならホーム画面
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    RequestNotificationPermission()

    val editing = editingId
    if (editing != null) {
        val original = if (editing == NEW_CHARACTER) null else repository.find(editing)
        CharacterEditor(
            original = original,
            onClose = { savedId ->
                if (savedId != null) selectedId = savedId
                editingId = null
            },
        )
        return
    }

    HomeScreen(
        greetPending = greetPending,
        onGreeted = onGreeted,
        characters = characters,
        selected = characters.firstOrNull { it.id == selectedId } ?: characters.firstOrNull(),
        missingBuiltIns = repository.missingBuiltIns,
        onSelect = { selectedId = it.id },
        onEdit = { editingId = it.id },
        onAdd = { editingId = NEW_CHARACTER },
        onRestoreBuiltIns = repository::restoreMissingBuiltIns,
    )
}

@Composable
private fun HomeScreen(
    greetPending: Boolean,
    onGreeted: () -> Unit,
    characters: List<Character>,
    selected: Character?,
    missingBuiltIns: List<CharacterSeed>,
    onSelect: (Character) -> Unit,
    onEdit: (Character) -> Unit,
    onAdd: () -> Unit,
    onRestoreBuiltIns: suspend () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // ヘッダー(小さい場所は文字だけのロゴ)
        Text(
            "VOICEROID POCKET",
            style = VpType.wordmark,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )

        // キャラの切り替え
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            characters.forEachIndexed { index, c ->
                CharacterThumb(
                    character = c,
                    number = index + 1,
                    selected = c.id == selected?.id,
                    onClick = { onSelect(c) },
                    size = 72.dp,
                )
            }
            AddTile(text = "キャラを\n追加", onClick = onAdd, size = 72.dp)
        }

        // 選んでいるキャラへの操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected != null) {
                VpSmallButton("編集する", onClick = { onEdit(selected) })
                // 開発用:今選んでいるキャラから通知を送る
                VpSmallButton("通知を試す", onClick = {
                    CharacterNotifier(context).notify(selected, selected.randomLine())
                })
                val voiceOn by remember { VoiceSettings.enabled(context) }.collectAsState()
                VpSmallButton(
                    if (voiceOn) "ボイス オン" else "ボイス オフ",
                    onClick = { VoiceSettings.setEnabled(context, !voiceOn) },
                    selected = voiceOn,
                    selectedColor = selected.themeColor,
                )
            }
            if (missingBuiltIns.isNotEmpty()) {
                VpSmallButton(
                    "${missingBuiltIns.joinToString("・") { it.name }}を呼び戻す",
                    onClick = { scope.launch { onRestoreBuiltIns() } },
                )
            }
        }

        if (selected == null) {
            EmptyHome(onAdd)
        } else {
            val reduceMotion = rememberReduceMotion()
            val slide = with(LocalDensity.current) { 40.dp.roundToPx() }
            // キャラを切り替えたら、右から40dpスライドしながらフェードイン(0.45秒)
            AnimatedContent(
                targetState = selected,
                contentKey = { it.id },
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (slideInHorizontally(tween(450)) { slide } + fadeIn(tween(450))) togetherWith
                            fadeOut(tween(150))
                    }
                },
                modifier = Modifier.weight(1f),
                label = "character",
            ) { character ->
                CharacterStage(character, greetPending = greetPending, onGreeted = onGreeted)
            }
        }
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Text("キャラがまだいません", style = VpType.headingLSm)
        Text(
            "好きな画像とセリフで、あなただけのキャラを作れます！",
            style = VpType.body,
            textAlign = TextAlign.Center,
        )
        VpButton("キャラを追加する ›", onClick = onAdd, color = VpColor.pink, large = true)
    }
}

@Composable
fun CharacterStage(character: Character, greetPending: Boolean = false, onGreeted: () -> Unit = {}) {
    val context = LocalContext.current
    var line by remember(character.id) { mutableStateOf(character.firstLine) }

    // 久しぶりに開いたときは、まず「久しぶり」のセリフで迎える(1回だけ)
    LaunchedEffect(greetPending) {
        if (greetPending) {
            character.absenceLine()?.let { line = it }
            onGreeted()
        }
    }
    // 編集でセリフが変わっていたら、新しい内容で表示する
    val shown = character.line(line.id) ?: line

    // 1分たったら自動で次のセリフへ(タップで変わったらカウントし直す)
    LaunchedEffect(character, line) {
        delay(60_000)
        line = if (Random.nextBoolean()) {
            character.speakTo(Situation.now(context), except = line)
        } else {
            character.randomLine(except = line)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 立ち絵:斜めの帯の上に、キャラを下揃えで置く
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            DiagonalBands(
                main = character.themeColor,
                sub = character.subColor,
                modifier = Modifier.fillMaxSize(),
            )
            Dots(Modifier.align(Alignment.TopStart).padding(start = 8.dp).size(width = 96.dp, height = 120.dp))
            Sparkle(VpColor.blue, Modifier.align(Alignment.TopStart).offset(32.dp, 36.dp).size(30.dp))
            Sparkle(VpColor.pink, Modifier.align(Alignment.CenterEnd).offset((-28).dp, 10.dp).size(24.dp), delayMillis = 900)
            Sparkle(VpColor.pink, Modifier.align(Alignment.CenterStart).offset(24.dp, 70.dp).size(18.dp), delayMillis = 400)

            MotionBox(character.motion, Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                FaceImage(
                    character = character,
                    face = character.face(shown.faceId),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    placeholderTextSize = 120.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            line = character.randomLine(except = line)
                            VoicePlayer.play(context, line.voice)
                        },
                )
            }

            NamePlate(character, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 20.dp))
        }

        SpeechBubble(
            character = character,
            text = shown.text,
            modifier = Modifier.padding(horizontal = 24.dp),
            onPlayVoice = shown.voice?.let { voice -> { VoicePlayer.play(context, voice, force = true) } },
        )
        Text(
            "キャラをタップすると話しかけられます！",
            style = VpType.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        )
    }
}
