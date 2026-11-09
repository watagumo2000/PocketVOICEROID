@file:OptIn(ExperimentalLayoutApi::class)

package com.example.widgetvoiceroid

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.widgetvoiceroid.ui.theme.VpColor
import com.example.widgetvoiceroid.ui.theme.VpType
import com.example.widgetvoiceroid.ui.theme.contentColorOn
import kotlinx.coroutines.launch

// 選べるテーマ色。どれも上に ink の文字が読める淡い色
private val Palette = listOf(
    0xFFFFB3BE, // pink(茜)
    0xFFB3E4FF, // blue(葵)
    0xFFFFE08A, // レモン
    0xFFB8EBC4, // ミント
    0xFFD7C9FF, // ラベンダー
    0xFFFFCBA4, // ピーチ
    0xFFA9EEE2, // アクア
    0xFFF5C2EC, // ライラック
    0xFFE6D9C3, // サンド
    0xFFD9D7E6, // グレー
).map { it.toInt() }

// 画面を回転しても編集中の内容が消えないよう、JSONにして覚えておく
private val CharacterSaver = Saver<Character, String>(
    save = { CharacterRepository.json.encodeToString(it) },
    restore = { CharacterRepository.json.decodeFromString<Character>(it) },
)

// 「いつ話す？」の種類
private enum class ConditionKind(val label: String, val hint: String) {
    ALWAYS("いつでも", "普段のセリフです。タップしたときなどに話します。"),
    TIME("時間帯", "その時間帯のあいだ、ウィジェットや通知で話します。"),
    WEEKDAY("曜日", "選んだ曜日のあいだ話します。いくつでも選べます。"),
    DATE("記念日", "毎年その日になると、ほかのセリフより優先して話します。"),
    CHARGING("充電中", "充電しているときに話します。"),
    BATTERY_LOW("電池が少ない", "充電していなくて、電池が残り${BATTERY_LOW_PERCENT}%以下のときに話します。"),
    ABSENCE("久しぶり", "${LONG_ABSENCE_DAYS}日以上ぶりにアプリを開いたとき、最初に話します。");

    // この種類を選んだときの、最初の条件(今日・今の時間帯に合わせる)
    fun defaultCondition(): Condition? {
        val now = java.time.LocalDateTime.now()
        return when (this) {
            ALWAYS -> null
            TIME -> Condition.TimeOfDay(TimeSlot.of(now.hour))
            WEEKDAY -> Condition.Weekdays(setOf(Weekday.of(now.dayOfWeek)))
            DATE -> Condition.Anniversary(now.monthValue, now.dayOfMonth)
            CHARGING -> Condition.Charging
            BATTERY_LOW -> Condition.BatteryLow
            ABSENCE -> Condition.LongAbsence
        }
    }

    companion object {
        fun of(condition: Condition?): ConditionKind = when (condition) {
            null -> ALWAYS
            is Condition.TimeOfDay -> TIME
            is Condition.Weekdays -> WEEKDAY
            is Condition.Anniversary -> DATE
            Condition.Charging -> CHARGING
            Condition.BatteryLow -> BATTERY_LOW
            Condition.LongAbsence -> ABSENCE
        }
    }
}

// セリフ一覧の絞り込み
private sealed interface LineFilter {
    data object All : LineFilter
    data class Kind(val kind: ConditionKind) : LineFilter

    fun matches(line: Line) = this == All || (this is Kind && ConditionKind.of(line.condition) == kind)
}

private data class FaceEdit(val face: Face, val isNew: Boolean)
private data class LineEdit(val line: Line, val isNew: Boolean)

// 画像を選んだあと、新しい表情にするのか、今の表情の画像を差し替えるのか
private const val PICK_NEW_FACE = "__new_face__"

// キャラの編集画面。original が null なら新しいキャラを作る
// 閉じるときに、保存したキャラのID(保存しなかったら null)を返す
@Composable
fun CharacterEditor(original: Character?, onClose: (savedId: String?) -> Unit) {
    val context = LocalContext.current
    val repository = remember { CharacterRepository.get(context) }
    val scope = rememberCoroutineScope()

    val start by rememberSaveable(stateSaver = CharacterSaver) {
        mutableStateOf(
            original ?: Character(id = newId(), name = "", color = Palette.first()),
        )
    }
    var draft by rememberSaveable(stateSaver = CharacterSaver) { mutableStateOf(start) }

    var faceEdit by remember { mutableStateOf<FaceEdit?>(null) }
    var lineEdit by remember { mutableStateOf<LineEdit?>(null) }
    var filter by remember { mutableStateOf<LineFilter>(LineFilter.All) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // ---- 端末から画像を選ぶ ----
    var pickTarget by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val target = pickTarget ?: return@rememberLauncherForActivityResult
        pickTarget = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val image = ImageStore.import(context, uri)
            if (image == null) {
                Toast.makeText(context, "画像を読み込めませんでした", Toast.LENGTH_SHORT).show()
                return@launch
            }
            faceEdit = if (target == PICK_NEW_FACE) {
                FaceEdit(Face(newId(), "表情${draft.faces.size + 1}", image), isNew = true)
            } else {
                faceEdit?.let { it.copy(face = it.face.copy(image = image)) }
            }
        }
    }
    fun pickImage(target: String) {
        pickTarget = target
        picker.launch("image/*")
    }

    // ---- トークソフトとの受け渡し ----
    var exportDialog by remember { mutableStateOf(false) }
    var guideDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<AiVoice.ImportResult?>(null) }
    var pendingExport by remember { mutableStateOf<List<Line>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        val lines = pendingExport ?: return@rememberLauncherForActivityResult
        pendingExport = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(AiVoice.encode(AiVoice.buildText(lines, draft.aiVoice), draft.aiVoice.software.bom))
                    }
                }
            }.isSuccess
            if (ok) {
                // 書き出した順番を覚えておく(連番で対応づけるときに使う)
                draft = draft.copy(aiVoice = draft.aiVoice.copy(exportedLineIds = lines.map { it.id }))
                guideDialog = true
            } else {
                Toast.makeText(context, "ファイルを書き出せませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runImport(files: suspend () -> List<AiVoice.InputFile>) {
        scope.launch {
            val list = files()
            if (list.isEmpty()) return@launch
            val result = AiVoice.importVoices(context, draft, list)
            draft = result.character
            importResult = result
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) runImport { AiVoice.listFolder(context, uri) }
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) runImport { AiVoice.describe(context, uris) }
    }

    // ---- 閉じる・保存 ----
    val changed = draft != start
    fun close() {
        if (changed) confirm = Confirm.Discard else onClose(null)
    }
    BackHandler(onBack = ::close)

    fun save() {
        val toSave = draft.copy(name = draft.name.trim())
        scope.launch {
            repository.save(toSave)
            onClose(toSave.id)
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // 上のバー
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VpSmallButton("キャンセル", onClick = ::close)
            Text(
                text = if (original == null) "キャラを追加" else "キャラを編集",
                style = VpType.label.copy(fontSize = 16.sp),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            VpButton(
                "保存する",
                onClick = ::save,
                enabled = draft.name.isNotBlank(),
                color = VpColor.pink,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        val shownLines = draft.lines.filter { filter.matches(it) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- プロフィール ----
            item {
                SectionTitle(en = "PROFILE", ja = "プロフィール")
            }
            item {
                ProfileCard(draft = draft, onChange = { draft = it })
            }

            // ---- 表情 ----
            item {
                SectionTitle(en = "FACE", ja = "表情", alt = true)
                Text(
                    "画像を選んで表情を追加できます！先頭が基本の表情です。",
                    style = VpType.body.copy(fontSize = 14.sp),
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    draft.faces.forEachIndexed { index, face ->
                        FaceTile(
                            character = draft,
                            face = face,
                            isDefault = index == 0,
                            onClick = { faceEdit = FaceEdit(face, isNew = false) },
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AddTile(text = "表情を\n追加", onClick = { pickImage(PICK_NEW_FACE) })
                    }
                }
            }

            // ---- セリフ ----
            item {
                SectionTitle(en = "LINES", ja = "セリフ")
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val filters = listOf(LineFilter.All) + ConditionKind.entries.map { LineFilter.Kind(it) }
                    filters.forEach { f ->
                        val count = draft.lines.count { f.matches(it) }
                        VpSmallButton(
                            "${filterLabel(f)} $count",
                            onClick = { filter = f },
                            selected = filter == f,
                            selectedColor = draft.themeColor,
                        )
                    }
                }
            }
            item {
                VpButton(
                    "＋ セリフを追加する",
                    onClick = {
                        val condition = (filter as? LineFilter.Kind)?.kind?.defaultCondition()
                        val faceId = draft.defaultFace?.id.orEmpty()
                        lineEdit = LineEdit(Line(newId(), "", faceId, condition), isNew = true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (shownLines.isEmpty()) {
                item {
                    Text(
                        "セリフがまだありません。「セリフを追加する」から書いてみてください！",
                        style = VpType.body.copy(fontSize = 14.sp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            items(shownLines, key = { it.id }) { line ->
                LineRow(draft, line, onClick = { lineEdit = LineEdit(line, isNew = false) })
            }

            // ---- ボイス ----
            item {
                SectionTitle(en = "VOICE", ja = "ボイス", alt = true)
            }
            item {
                val total = draft.lines.count { it.text.isNotBlank() }
                val voiced = draft.lines.count { it.text.isNotBlank() && it.voice != null }
                VoiceCard(
                    total = total,
                    voiced = voiced,
                    onExport = { exportDialog = true },
                    onImportFolder = { folderLauncher.launch(null) },
                    onImportFiles = { filesLauncher.launch(arrayOf("audio/*", "text/plain")) },
                    onGuide = { guideDialog = true },
                )
            }

            // ---- その他 ----
            if (original != null) {
                item {
                    SectionTitle(en = "OTHERS", ja = "その他")
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (original.isBuiltIn) {
                            VpButton(
                                "初期状態に戻す",
                                onClick = { confirm = Confirm.Reset },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        VpButton(
                            "このキャラを削除する",
                            onClick = { confirm = Confirm.Delete },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // ---- ダイアログ ----
    faceEdit?.let { edit ->
        FaceDialog(
            character = draft,
            edit = edit,
            isDefault = draft.faces.firstOrNull()?.id == edit.face.id,
            onChange = { faceEdit = edit.copy(face = it) },
            onPickImage = { pickImage(edit.face.id) },
            onDismiss = { faceEdit = null },
            onConfirm = { face ->
                draft = if (edit.isNew) {
                    draft.copy(faces = draft.faces + face)
                } else {
                    draft.copy(faces = draft.faces.map { if (it.id == face.id) face else it })
                }
                faceEdit = null
            },
            onMakeDefault = {
                val face = edit.face
                draft = draft.copy(faces = listOf(face) + draft.faces.filter { it.id != face.id })
                faceEdit = null
            },
            onDelete = {
                faceEdit = null
                confirm = Confirm.DeleteFace(edit.face)
            },
        )
    }

    lineEdit?.let { edit ->
        LineDialog(
            character = draft,
            edit = edit,
            onDismiss = { lineEdit = null },
            onConfirm = { line ->
                draft = if (edit.isNew) {
                    draft.copy(lines = draft.lines + line)
                } else {
                    draft.copy(lines = draft.lines.map { if (it.id == line.id) line else it })
                }
                lineEdit = null
            },
            onDelete = {
                draft = draft.copy(lines = draft.lines.filter { it.id != edit.line.id })
                lineEdit = null
            },
        )
    }

    if (exportDialog) {
        ExportDialog(
            character = draft,
            onChange = { draft = draft.copy(aiVoice = it) },
            onDismiss = { exportDialog = false },
            onExport = { onlyMissing ->
                exportDialog = false
                val lines = AiVoice.linesToExport(draft, onlyMissing)
                pendingExport = lines
                exportLauncher.launch("${draft.name.trim().ifEmpty { "character" }}_${draft.aiVoice.software.fileTag}.txt")
            },
        )
    }

    if (guideDialog) {
        GuideDialog(software = draft.aiVoice.software, onDismiss = { guideDialog = false })
    }

    importResult?.let { r ->
        VpDialog(
            title = "ボイスを読み込みました",
            onDismiss = { importResult = null },
            buttons = { VpButton("OK", color = VpColor.pink, onClick = { importResult = null }) },
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${r.matchedLines}個のセリフにボイスを付けました！", style = VpType.label.copy(fontSize = 16.sp))
                if (r.unmatched.isNotEmpty()) {
                    Text("合うセリフが見つからなかったファイル(${r.unmatched.size}個):", style = VpType.label)
                    Text(r.unmatched.take(20).joinToString("\n") + if (r.unmatched.size > 20) "\n…" else "", style = VpType.caption)
                }
                if (r.unsupported.isNotEmpty()) {
                    Text("再生できない形式のため読み込まなかったファイル(WAVE か MP3 で保存してください):", style = VpType.label)
                    Text(r.unsupported.take(20).joinToString("\n"), style = VpType.caption)
                }
                Text("右上の「保存する」を押すと確定します。", style = VpType.caption)
            }
        }
    }

    confirm?.let { c ->
        VpDialog(
            title = c.title,
            onDismiss = { confirm = null },
            buttons = {
                VpSmallButton("やめる", onClick = { confirm = null })
                VpButton(c.action, color = VpColor.pink, onClick = {
                    confirm = null
                    when (c) {
                        Confirm.Discard -> onClose(null)
                        Confirm.Delete -> scope.launch {
                            repository.delete(draft.id)
                            onClose(null)
                        }
                        Confirm.Reset -> scope.launch {
                            repository.resetBuiltIn(draft.id)
                            onClose(draft.id)
                        }
                        is Confirm.DeleteFace -> {
                            val rest = draft.faces.filter { it.id != c.face.id }
                            val fallback = rest.firstOrNull()?.id.orEmpty()
                            // この表情を使っていたセリフは、基本の表情にする
                            draft = draft.copy(
                                faces = rest,
                                lines = draft.lines.map { if (it.faceId == c.face.id) it.copy(faceId = fallback) else it },
                            )
                        }
                    }
                })
            },
        ) {
            Text(c.message, style = VpType.body.copy(fontSize = 15.sp))
        }
    }
}

// 確認ダイアログの種類
private sealed class Confirm(val title: String, val message: String, val action: String) {
    data object Discard : Confirm("変更を捨てますか？", "保存していない変更は消えてしまいます。", "捨てる")
    data object Delete : Confirm("このキャラを削除しますか？", "表情やセリフもすべて消えてしまいます。", "削除する")
    data object Reset : Confirm(
        "初期状態に戻しますか？",
        "追加・編集した表情やセリフはすべて消えて、最初の内容に戻ります。",
        "戻す",
    )
    data class DeleteFace(val face: Face) : Confirm(
        "「${face.label}」を削除しますか？",
        "この表情を使っているセリフは、基本の表情になります。",
        "削除する",
    )
}

private fun filterLabel(filter: LineFilter): String = when (filter) {
    LineFilter.All -> "すべて"
    is LineFilter.Kind -> filter.kind.label
}

// 名前・テーマ色・動き方をまとめた白いカード
@Composable
private fun ProfileCard(draft: Character, onChange: (Character) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 8.dp)
            .vpSurface(Radius2xl, shadow = DpOffset(8.dp, 8.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        VpTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            label = "名前",
            placeholder = "例:琴葉 茜",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("テーマ色", style = VpType.label)
            ColorPalette(selected = draft.color, name = draft.name, onSelect = { onChange(draft.copy(color = it)) })
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("動き方", style = VpType.label)
            ChoiceRow {
                IdleMotion.entries.forEach { motion ->
                    VpSmallButton(
                        motion.label,
                        onClick = { onChange(draft.withMotion(motion)) },
                        selected = draft.motion == motion,
                        selectedColor = draft.themeColor,
                    )
                }
            }
            MotionPreview(draft)
        }
    }
}

// 選んだ動き方のお試し表示(基本の表情を小さく動かす)
@Composable
private fun MotionPreview(character: Character) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 104.dp)
                .vpSurface(RadiusLg, fill = character.themeColor, border = 2.dp)
                .clip(RadiusLg)
                .padding(top = 18.dp, start = 6.dp, end = 6.dp),
        ) {
            // 動き方を切り替えたら、動きを最初からやり直す
            key(character.motion) {
                MotionBox(character.motion, Modifier.fillMaxSize()) {
                    FaceImage(
                        character = character,
                        face = character.defaultFace,
                        maxSize = 320,
                        alignment = Alignment.BottomCenter,
                        placeholderTextSize = 32.sp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Text(character.motion.description, style = VpType.caption, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ColorPalette(selected: Int, name: String, onSelect: (Int) -> Unit) {
    // パレットにない色(自分で決めた色)は、最後に並べる
    val colors = if (selected in Palette) Palette else Palette + selected
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        ColorPickerDialog(
            initial = selected,
            name = name,
            onDismiss = { picking = false },
            onPick = { picking = false; onSelect(it) },
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(40.dp)
                    .vpSurface(
                        CircleShape,
                        fill = Color(color),
                        border = if (isSelected) 3.dp else 2.dp,
                        shadow = if (isSelected) DpOffset(4.dp, 4.dp) else null,
                    )
                    .clickable(role = Role.RadioButton) { onSelect(color) }
                    .semantics { this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(Modifier.size(12.dp).background(contentColorOn(Color(color)), CircleShape))
                }
            }
        }
        // 自分で色を決める
        Box(
            modifier = Modifier
                .padding(end = 4.dp, bottom = 4.dp)
                .size(40.dp)
                .vpSurface(CircleShape, fill = VpColor.surface, border = 2.dp)
                .clickable(role = Role.Button) { picking = true }
                .semantics { contentDescription = "色を自分で決める" },
            contentAlignment = Alignment.Center,
        ) {
            Text("＋", style = VpType.button.copy(color = VpColor.ink))
        }
    }
}

@Composable
private fun FaceTile(character: Character, face: Face, isDefault: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(84.dp).semantics(mergeDescendants = true) { contentDescription = face.label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 4.dp, bottom = 4.dp)
                    .size(80.dp)
                    .vpSurface(RadiusXl, fill = character.themeColor)
                    .clickable(role = Role.Button, onClick = onClick),
            ) {
                FaceImage(
                    character = character,
                    face = face,
                    maxSize = 400,
                    contentScale = ContentScale.Crop,
                    alignment = FaceAlignment,
                    placeholderTextSize = 28.sp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isDefault) {
                VpBadge("基本", Modifier.padding(start = 6.dp, top = 10.dp))
            }
        }
        Text(face.label, style = VpType.tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// セリフの一覧の1行(フチだけで影のないブロック)
@Composable
private fun LineRow(character: Character, line: Line, onClick: () -> Unit) {
    val face = character.face(line.faceId)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vpSurface(RadiusXl, border = 2.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .vpSurface(RadiusMd, fill = character.themeColor, border = 2.dp),
        ) {
            FaceImage(
                character = character,
                face = face,
                maxSize = 200,
                contentScale = ContentScale.Crop,
                alignment = FaceAlignment,
                placeholderTextSize = 20.sp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                line.text.ifEmpty { "(空のセリフ)" },
                style = VpType.bubble.copy(
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = if (line.text.isEmpty()) VpColor.inkMuted else VpColor.ink,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                VpTag("#${line.condition?.label ?: "いつでも"}")
                if (line.voice != null) VpTag("ボイス")
                Text(face?.label ?: "表情なし", style = VpType.caption)
            }
        }
    }
}

@Composable
private fun FaceDialog(
    character: Character,
    edit: FaceEdit,
    isDefault: Boolean,
    onChange: (Face) -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Face) -> Unit,
    onMakeDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val face = edit.face
    VpDialog(
        title = if (edit.isNew) "表情を追加" else "表情を編集",
        onDismiss = onDismiss,
        buttons = {
            VpSmallButton("キャンセル", onClick = onDismiss)
            VpButton(
                "決定",
                onClick = { onConfirm(face.copy(label = face.label.trim())) },
                enabled = face.label.isNotBlank(),
                color = VpColor.pink,
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(180.dp)
                    .vpSurface(RadiusXl, fill = character.themeColor),
            ) {
                FaceImage(
                    character = character,
                    face = face,
                    maxSize = 600,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            VpSmallButton("画像を変える", onClick = onPickImage)
            VpTextField(
                value = face.label,
                onValueChange = { onChange(face.copy(label = it)) },
                label = "表情の名前",
                placeholder = "例:笑顔",
                modifier = Modifier.fillMaxWidth(),
            )
            if (!edit.isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isDefault) {
                        VpSmallButton("基本の表情にする", onClick = onMakeDefault)
                    }
                    VpSmallButton("削除する", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun LineDialog(
    character: Character,
    edit: LineEdit,
    onDismiss: () -> Unit,
    onConfirm: (Line) -> Unit,
    onDelete: () -> Unit,
) {
    var line by remember(edit) { mutableStateOf(edit.line) }
    VpDialog(
        title = if (edit.isNew) "セリフを追加" else "セリフを編集",
        onDismiss = onDismiss,
        buttons = {
            VpSmallButton("キャンセル", onClick = onDismiss)
            VpButton(
                "決定",
                onClick = { onConfirm(line.copy(text = line.text.trim())) },
                enabled = line.text.isNotBlank(),
                color = VpColor.pink,
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VpTextField(
                value = line.text,
                onValueChange = { line = line.copy(text = it) },
                label = "セリフ",
                placeholder = "例:今日もおつかれさまです！",
                singleLine = false,
                minLines = 2,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("表情", style = VpType.label)
                if (character.faces.isEmpty()) {
                    Text("表情がまだありません(あとから追加できます)", style = VpType.caption)
                } else {
                    val selectedFace = character.face(line.faceId)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(character.faces, key = { it.id }) { face ->
                            val isSelected = face.id == selectedFace?.id
                            Column(
                                modifier = Modifier.width(64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp, end = 4.dp, bottom = 4.dp)
                                        .size(56.dp)
                                        .vpSurface(
                                            RadiusMd,
                                            fill = if (isSelected) character.themeColor else VpColor.surfaceSoft,
                                            border = if (isSelected) 3.dp else 2.dp,
                                            shadow = if (isSelected) DpOffset(4.dp, 4.dp) else null,
                                        )
                                        .clickable(role = Role.RadioButton) { line = line.copy(faceId = face.id) }
                                        .semantics { this.selected = isSelected },
                                ) {
                                    FaceImage(
                                        character = character,
                                        face = face,
                                        maxSize = 200,
                                        contentScale = ContentScale.Crop,
                                        alignment = FaceAlignment,
                                        placeholderTextSize = 18.sp,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Text(face.label, style = VpType.tag.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            ConditionPicker(
                condition = line.condition,
                color = character.themeColor,
                onChange = { line = line.copy(condition = it) },
            )

            VoicePicker(voice = line.voice, onChange = { line = line.copy(voice = it) })

            if (!edit.isNew) {
                VpSmallButton("このセリフを削除する", onClick = onDelete)
            }
        }
    }
}

// 「いつ話す？」を選ぶ。種類を選んでから、時間帯・曜日・日付などの中身を決める
@Composable
private fun ConditionPicker(condition: Condition?, color: Color, onChange: (Condition?) -> Unit) {
    val kind = ConditionKind.of(condition)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("いつ話す？", style = VpType.label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConditionKind.entries.forEach { k ->
                VpSmallButton(
                    k.label,
                    onClick = { if (k != kind) onChange(k.defaultCondition()) },
                    selected = k == kind,
                    selectedColor = color,
                )
            }
        }

        // 種類ごとの中身
        when (condition) {
            is Condition.TimeOfDay -> ChoiceRow {
                TimeSlot.entries.forEach { slot ->
                    VpSmallButton(
                        "${slot.label}(${slot.hours.first}〜${slot.hours.last}時)",
                        onClick = { onChange(Condition.TimeOfDay(slot)) },
                        selected = condition.slot == slot,
                        selectedColor = color,
                    )
                }
            }

            is Condition.Weekdays -> {
                ChoiceRow {
                    Weekday.entries.forEach { day ->
                        val on = day in condition.days
                        VpSmallButton(
                            day.label,
                            onClick = {
                                val days = if (on) condition.days - day else condition.days + day
                                if (days.isNotEmpty()) onChange(Condition.Weekdays(days))  // 0個にはしない
                            },
                            selected = on,
                            selectedColor = color,
                        )
                    }
                }
                ChoiceRow {
                    VpSmallButton("平日にする", onClick = {
                        onChange(Condition.Weekdays(Weekday.entries.toSet() - setOf(Weekday.SAT, Weekday.SUN)))
                    })
                    VpSmallButton("週末にする", onClick = {
                        onChange(Condition.Weekdays(setOf(Weekday.SAT, Weekday.SUN)))
                    })
                }
            }

            is Condition.Anniversary -> {
                val maxDay = java.time.Month.of(condition.month).maxLength()  // 2月は29日まで選べる
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Stepper(
                        value = "${condition.month}月",
                        onMinus = {
                            val m = if (condition.month == 1) 12 else condition.month - 1
                            onChange(condition.copy(month = m, day = condition.day.coerceAtMost(java.time.Month.of(m).maxLength())))
                        },
                        onPlus = {
                            val m = if (condition.month == 12) 1 else condition.month + 1
                            onChange(condition.copy(month = m, day = condition.day.coerceAtMost(java.time.Month.of(m).maxLength())))
                        },
                    )
                    Stepper(
                        value = "${condition.day}日",
                        onMinus = { onChange(condition.copy(day = if (condition.day == 1) maxDay else condition.day - 1)) },
                        onPlus = { onChange(condition.copy(day = if (condition.day >= maxDay) 1 else condition.day + 1)) },
                    )
                }
                VpTextField(
                    value = condition.title,
                    onValueChange = { onChange(condition.copy(title = it)) },
                    label = "記念日の名前(なくてもOK)",
                    placeholder = "例:誕生日",
                )
            }

            else -> Unit
        }

        Text(kind.hint, style = VpType.caption)
    }
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// −/＋ で数字を増減する
@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        VpSmallButton("−", onClick = onMinus)
        Text(
            value,
            style = VpType.label.copy(fontSize = 16.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 44.dp),
        )
        VpSmallButton("＋", onClick = onPlus)
    }
}

// ボイスの章:トークソフト用リストの書き出しと、ボイスの読み込み
@Composable
private fun VoiceCard(
    total: Int,
    voiced: Int,
    onExport: () -> Unit,
    onImportFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onGuide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 8.dp)
            .vpSurface(Radius2xl, shadow = DpOffset(8.dp, 8.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ボイス付きのセリフ $voiced / $total", style = VpType.label.copy(fontSize = 16.sp))
        Text(
            "A.I.VOICE・VOICEVOX などのトークソフト用に、セリフのリストを書き出せます！作ったボイスはフォルダごと読み込むと、セリフに自動で付きます。",
            style = VpType.body.copy(fontSize = 14.sp),
        )
        VpButton("トークソフト用のリストを書き出す", onClick = onExport, modifier = Modifier.fillMaxWidth())
        VpButton("ボイスのフォルダを読み込む", onClick = onImportFolder, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VpSmallButton("ファイルを選んで読み込む", onClick = onImportFiles)
            VpSmallButton("使い方", onClick = onGuide)
        }
    }
}

@Composable
private fun ExportDialog(
    character: Character,
    onChange: (AiVoiceSettings) -> Unit,
    onDismiss: () -> Unit,
    onExport: (onlyMissing: Boolean) -> Unit,
) {
    val settings = character.aiVoice
    val software = settings.software
    val missing = AiVoice.linesToExport(character, onlyMissing = true).size
    val all = AiVoice.linesToExport(character, onlyMissing = false).size
    var onlyMissing by remember { mutableStateOf(missing > 0) }
    val tooLong = AiVoice.tooLong(AiVoice.linesToExport(character, onlyMissing), software)

    VpDialog(
        title = "セリフのリスト",
        onDismiss = onDismiss,
        buttons = {
            VpSmallButton("キャンセル", onClick = onDismiss)
            VpButton(
                "書き出す",
                onClick = { onExport(onlyMissing) },
                enabled = (if (onlyMissing) missing else all) > 0,
                color = VpColor.pink,
            )
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("使うソフト", style = VpType.label)
                ChoiceRow {
                    VoiceSoftware.entries.forEach { s ->
                        VpSmallButton(
                            s.label,
                            onClick = { onChange(settings.copy(software = s)) },
                            selected = s == software,
                            selectedColor = character.themeColor,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("書き出すセリフ", style = VpType.label)
                ChoiceRow {
                    VpSmallButton(
                        "ボイスがないセリフ $missing",
                        onClick = { onlyMissing = true },
                        selected = onlyMissing,
                        selectedColor = character.themeColor,
                    )
                    VpSmallButton(
                        "すべて $all",
                        onClick = { onlyMissing = false },
                        selected = !onlyMissing,
                        selectedColor = character.themeColor,
                    )
                }
                if (tooLong > 0) {
                    Text(
                        "${software.label} は1セリフ${software.maxChars}文字までです。超えているセリフが${tooLong}個あります。",
                        style = VpType.caption,
                    )
                }
            }

            when (software.speaker) {
                SpeakerStyle.PRESET_TAG -> SpeakerSettings(
                    character = character,
                    settings = settings,
                    onChange = onChange,
                    title = "ボイスプリセットを指定する",
                    caption = "セリフの先頭にタグを付けて、開いたときに声が選ばれるようにします",
                    fieldLabel = "ボイスプリセット名",
                    placeholder = "例:琴葉 葵",
                ) {
                    VpTextField(
                        value = settings.prompt,
                        onValueChange = { onChange(settings.copy(prompt = it)) },
                        label = "プロンプト文字列",
                        placeholder = "＞",
                    )
                    Text(
                        "${software.label} の「プロジェクト設定」→「ボイス」のプロンプト文字列と同じにしてください。",
                        style = VpType.caption,
                    )
                }
                SpeakerStyle.NAME_COMMA -> SpeakerSettings(
                    character = character,
                    settings = settings,
                    onChange = onChange,
                    title = "キャラクターを指定する",
                    caption = "行の先頭にキャラ名を付けて、読み込んだときにキャラが選ばれるようにします",
                    fieldLabel = "キャラ名(スタイル)",
                    placeholder = "例:ずんだもん（ノーマル）",
                ) {
                    Text(
                        "VOICEVOX のキャラ名と同じにしてください。スタイルは全角のかっこで付けます。付けなければ、いつものスタイルになります。",
                        style = VpType.caption,
                    )
                }
                SpeakerStyle.NONE -> Text(
                    "${software.label} はテキストで声を指定できないので、${software.label} の画面でキャラを選んでください。",
                    style = VpType.caption,
                )
            }
        }
    }
}

// 話者の指定(オン・オフと名前)
@Composable
private fun SpeakerSettings(
    character: Character,
    settings: AiVoiceSettings,
    onChange: (AiVoiceSettings) -> Unit,
    title: String,
    caption: String,
    fieldLabel: String,
    placeholder: String,
    extra: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = VpType.label)
            Text(caption, style = VpType.caption)
        }
        VpSwitch(
            checked = settings.usePreset,
            onCheckedChange = { on ->
                onChange(settings.copy(usePreset = on, preset = settings.preset.ifBlank { character.name.trim() }))
            },
        )
    }
    if (settings.usePreset) {
        VpTextField(
            value = settings.preset,
            onValueChange = { onChange(settings.copy(preset = it)) },
            label = fieldLabel,
            placeholder = placeholder,
        )
        extra()
    }
}

// トークソフトでの書き出し方
@Composable
private fun GuideDialog(software: VoiceSoftware, onDismiss: () -> Unit) {
    VpDialog(
        title = "${software.label}での作り方",
        onDismiss = onDismiss,
        buttons = { VpButton("OK", color = VpColor.pink, onClick = onDismiss) },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiVoice.guide(software).forEachIndexed { i, step -> GuideStep(i + 1, step) }
            Text(
                "テキストファイルの中身でセリフを探すので、ファイル名は自由に変えても大丈夫です。テキストファイルがないときは、ファイル名の連番と書き出した順番で対応づけます。",
                style = VpType.caption,
            )
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "%02d".format(number),
            style = VpType.nav.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        )
        Text(text, style = VpType.body.copy(fontSize = 14.sp, lineHeight = 24.sp, color = VpColor.ink))
    }
}

// セリフのボイス:聞く・外す・端末から選ぶ
@Composable
private fun VoicePicker(voice: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = AiVoice.describe(context, listOf(uri)).firstOrNull()?.name.orEmpty()
            val ext = AiVoice.extension(name).ifBlank {
                when (context.contentResolver.getType(uri)) {
                    "audio/mpeg" -> "mp3"
                    "audio/mp4", "audio/aac" -> "m4a"
                    "audio/ogg" -> "ogg"
                    else -> "wav"
                }
            }
            if (ext !in PlayableAudioExtensions) {
                Toast.makeText(context, "この形式は再生できません(WAVE か MP3 にしてください)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val stored = VoiceStore.import(context, uri, ext)
            if (stored == null) {
                Toast.makeText(context, "ボイスを読み込めませんでした", Toast.LENGTH_SHORT).show()
            } else {
                onChange(stored)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ボイス", style = VpType.label)
        ChoiceRow {
            if (voice != null) {
                VpSmallButton("聞く", onClick = { VoicePlayer.play(context, voice, force = true) })
                VpSmallButton("変える", onClick = { launcher.launch("audio/*") })
                VpSmallButton("外す", onClick = { VoicePlayer.stop(); onChange(null) })
            } else {
                VpSmallButton("ボイスを選ぶ", onClick = { launcher.launch("audio/*") })
            }
        }
    }
}
