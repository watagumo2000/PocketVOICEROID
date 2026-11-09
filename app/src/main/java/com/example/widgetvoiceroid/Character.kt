package com.example.widgetvoiceroid

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

// ---- アプリ上で編集できるキャラのデータ。characters.json にそのまま保存される ----

// 画像のありか:アプリに最初から入っている画像か、ユーザーが追加した画像か
@Serializable
sealed interface ImageRef {
    // 茜・葵など、最初から入っている画像(BuiltInCharacters の表から探す)
    @Serializable
    @SerialName("builtin")
    data class BuiltIn(val characterId: String, val expression: String) : ImageRef

    // ユーザーが追加した画像(アプリ内の images フォルダにあるファイル名)
    @Serializable
    @SerialName("file")
    data class Stored(val fileName: String) : ImageRef
}

// 表情:名前と画像のセット。キャラごとに好きなだけ作れる
@Serializable
data class Face(
    val id: String,
    val label: String,
    val image: ImageRef,
)

// セリフ:文章と表情。condition を付けると、その状況のときに話すセリフになる(null ならいつでも)
@Serializable
data class Line(
    val id: String,
    val text: String,
    val faceId: String,
    val condition: Condition? = null,
    val voice: String? = null,  // ボイスのファイル名(アプリ内の voices フォルダ)。null ならボイスなし
    // 古い形式(時間帯だけ選べた頃)のデータ。読み込むときに condition へ移す
    @SerialName("slot") val legacySlot: TimeSlot? = null,
) {
    fun migrated(): Line = when {
        legacySlot == null -> this
        condition == null -> copy(condition = Condition.TimeOfDay(legacySlot), legacySlot = null)
        else -> copy(legacySlot = null)
    }
}

// キャラ:名前・色・表情・セリフ・動き方
@Serializable
data class Character(
    val id: String,
    val name: String,
    val color: Int,                       // テーマ色(ARGB)
    val faces: List<Face> = emptyList(),  // 先頭が「基本の表情」
    val lines: List<Line> = emptyList(),
    val floating: Boolean = true,         // (古いデータ用)ふわふわ浮かぶかどうか。idleMotion がないときだけ使う
    val idleMotion: IdleMotion? = null,   // 待機中の動き方
    val seedRevision: Int = 0,            // 内蔵キャラの初期データをどこまで取り込んだか
    val aiVoice: AiVoiceSettings = AiVoiceSettings(),  // A.I.VOICE 用リストの書き出し設定
) {
    val themeColor: Color get() = Color(color)

    val motion: IdleMotion get() = idleMotion ?: if (floating) IdleMotion.FLOAT else IdleMotion.NONE

    fun withMotion(motion: IdleMotion): Character = copy(idleMotion = motion, floating = motion != IdleMotion.NONE)

    val isBuiltIn: Boolean get() = BuiltInCharacters.seed(id) != null

    // 基本の表情(表情がひとつもなければ null)
    val defaultFace: Face? get() = faces.firstOrNull()

    // 表情を探す。見つからなければ基本の表情で代用する
    fun face(faceId: String?): Face? = faces.firstOrNull { it.id == faceId } ?: defaultFace

    fun line(lineId: String?): Line? = lines.firstOrNull { it.id == lineId }

    // 最初に見せるセリフ(普段のセリフの先頭)
    val firstLine: Line get() = lines.firstOrNull { it.condition == null } ?: lines.firstOrNull() ?: emptyLine

    // 普段のセリフからランダムに選ぶ(直前と同じものは避ける)
    fun randomLine(except: Line? = null): Line {
        val usual = lines.filter { it.condition == null }
        return pickFrom(usual.ifEmpty { lines }, except)
    }

    // 状況に合ったセリフを選ぶ。
    // 「久しぶり」→「記念日」の順に優先し、それ以外は当てはまる条件のセリフをまとめた中から選ぶ。
    // 当てはまるセリフがなければ普段のセリフから選ぶ
    fun speakTo(situation: Situation, except: Line? = null): Line {
        val matching = lines.filter { line -> line.condition?.let(situation::matches) == true }
        val pool = matching.filter { it.condition is Condition.LongAbsence }
            .ifEmpty { matching.filter { it.condition is Condition.Anniversary } }
            .ifEmpty { matching }
        return if (pool.isEmpty()) randomLine(except) else pickFrom(pool, except)
    }

    // 久しぶりに会ったときのセリフ(なければ null)
    fun absenceLine(): Line? = lines.filter { it.condition is Condition.LongAbsence }.randomOrNull()

    private fun pickFrom(pool: List<Line>, except: Line?): Line =
        pool.filter { it.id != except?.id }.ifEmpty { pool }.randomOrNull() ?: emptyLine

    companion object {
        // セリフがひとつもないときに表示するもの
        val emptyLine = Line(id = "", text = "…", faceId = "")
    }
}

fun newId(): String = UUID.randomUUID().toString()

// トークソフト用のセリフリストを書き出すときの設定(保存データの名前は A.I.VOICE だけだった頃のまま)
@Serializable
data class AiVoiceSettings(
    val software: VoiceSoftware = VoiceSoftware.AIVOICE,  // どのソフト用に書き出すか
    val usePreset: Boolean = false,       // 話者を指定するか(A.I.VOICE・VOICEROID2 はタグ、VOICEVOX は行頭のキャラ名)
    val preset: String = "",              // ボイスプリセット名・キャラ名(例:琴葉 葵、ずんだもん（ノーマル）)
    val prompt: String = "＞",            // プロンプト文字列(A.I.VOICE・VOICEROID2 のプロジェクト設定「ボイス」と同じにする)
    val exportedLineIds: List<String> = emptyList(),  // 最後に書き出したセリフの順番(連番で対応づけるときに使う)
)
