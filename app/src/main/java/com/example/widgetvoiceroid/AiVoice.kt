package com.example.widgetvoiceroid

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

// 書き出すリストの形
enum class ListStyle {
    DELIMITED,     // 区切り文字 ## でつなぐ(1つのテキストを開いて、区切り文字で分けて書き出すソフト)
    ONE_PER_LINE,  // 1行に1セリフ(改行で分けるソフト。セリフの中の改行はつなげる)
}

// 話者の指定のしかた
enum class SpeakerStyle {
    PRESET_TAG,  // セリフの先頭に「ボイスプリセット名＋プロンプト文字列」(例:琴葉 葵＞)
    NAME_COMMA,  // 行頭に「キャラ名,」(例:ずんだもん（ノーマル）,)
    NONE,        // テキストでは指定できない(ソフトの画面で選ぶ)
}

// 書き出し先のトークソフト
@Serializable
enum class VoiceSoftware(
    val label: String,
    val fileTag: String,          // 書き出すファイル名の後ろに付ける名前
    val style: ListStyle,
    val speaker: SpeakerStyle,
    val bom: Boolean,             // UTF-8 の BOM を付けるか(付けると先頭のキャラ名が読めなくなるソフトでは付けない)
    val maxChars: Int? = null,    // 1セリフ(1ブロック)の文字数の上限
) {
    AIVOICE("A.I.VOICE", "AIVOICE", ListStyle.DELIMITED, SpeakerStyle.PRESET_TAG, bom = true),
    AIVOICE2("A.I.VOICE2", "AIVOICE2", ListStyle.ONE_PER_LINE, SpeakerStyle.NONE, bom = true),
    VOICEROID2("VOICEROID2", "VOICEROID2", ListStyle.DELIMITED, SpeakerStyle.PRESET_TAG, bom = true),
    VOICEVOX("VOICEVOX", "VOICEVOX", ListStyle.ONE_PER_LINE, SpeakerStyle.NAME_COMMA, bom = false),
    VOICEPEAK("VOICEPEAK", "VOICEPEAK", ListStyle.ONE_PER_LINE, SpeakerStyle.NONE, bom = false, maxChars = 140),
    VOISONA("VoiSona Talk", "VoiSona", ListStyle.ONE_PER_LINE, SpeakerStyle.NONE, bom = true, maxChars = 500),
}

// ---- トークソフトとの受け渡し ----
//
// 1. アプリ → ソフト:ソフトに合わせた形でセリフのリスト(.txt)を書き出す。
//    ソフトで「1セリフ=1音声ファイル」になるように書き出し、テキストファイルも一緒に保存してもらう。
// 2. ソフト → アプリ:書き出したフォルダ(またはファイル)を選ぶと、
//    音声と同じ名前のテキストファイル(.txt)の中身でセリフと対応づける。
//    .txt がなければ、ファイル名の連番と書き出した順番で対応づける。
object AiVoice {
    const val DELIMITER = "##"

    // A.I.VOICE・VOICEROID2 のファイル名の規則のおすすめ
    const val NAMING_RULE = "{Number}"

    // ---- 書き出し ----

    // 書き出すセリフ。onlyMissing なら、まだボイスがないセリフだけ
    fun linesToExport(character: Character, onlyMissing: Boolean): List<Line> =
        character.lines.filter { it.text.isNotBlank() && (!onlyMissing || it.voice == null) }

    // 話者の指定(書き出すときに付けて、読み込むときに外す)。指定しないなら空
    fun speakerPrefix(settings: AiVoiceSettings): String {
        if (!settings.usePreset || settings.preset.isBlank()) return ""
        return when (settings.software.speaker) {
            SpeakerStyle.PRESET_TAG -> settings.preset.trim() + settings.prompt
            SpeakerStyle.NAME_COMMA -> settings.preset.trim().replace(",", "，") + ","
            SpeakerStyle.NONE -> ""
        }
    }

    // ソフトで開くテキスト(改行は Windows に合わせて CRLF)
    fun buildText(lines: List<Line>, settings: AiVoiceSettings): String {
        val software = settings.software
        val prefix = speakerPrefix(settings)
        val texts = lines.map { prefix + cleanText(it.text, software) }
        return when (software.style) {
            ListStyle.DELIMITED -> texts.joinToString("\r\n$DELIMITER\r\n")
            ListStyle.ONE_PER_LINE -> texts.joinToString("\r\n")
        } + "\r\n"
    }

    // セリフをソフトに合わせて整える(区切りに使われる文字を全角にするなど)
    fun cleanText(text: String, software: VoiceSoftware): String {
        var t = text.trim().replace("\r\n", "\n").replace('\r', '\n')
        t = when (software.style) {
            // セリフの中に区切り文字があると分かれてしまうので、全角にしておく
            ListStyle.DELIMITED -> t.replace(DELIMITER, "＃＃").replace("\n", "\r\n")
            // 改行があると別のセリフになってしまうので、つなげる
            ListStyle.ONE_PER_LINE -> t.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("")
        }
        return when (software) {
            VoiceSoftware.VOICEVOX -> t.replace(",", "，")  // 半角コンマもセリフの区切りになる
            VoiceSoftware.VOISONA -> t.replace("<", "＜")   // 半角の < は入力できない
            VoiceSoftware.VOICEPEAK -> t.replace("\t", " ")  // タブを分割文字にしている人もいる
            else -> t
        }
    }

    // 文字数の上限を超えるセリフの数
    fun tooLong(lines: List<Line>, software: VoiceSoftware): Int {
        val max = software.maxChars ?: return 0
        return lines.count { cleanText(it.text, software).length > max }
    }

    // バイト列にする。BOM があると文字コードを確実に判定できるソフトには付ける
    fun encode(text: String, bom: Boolean = true): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return if (bom) byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body else body
    }

    // ソフトごとの作り方
    fun guide(software: VoiceSoftware): List<String> {
        val copy = "書き出したテキストファイルをパソコンに移して、メモ帳などで開き、全部コピーします。"
        val finish = listOf(
            "できたフォルダをスマホにコピーして、「ボイスのフォルダを読み込む」で選びます。",
            "セリフにボイスが付いたら、右上の「保存する」を押して確定します。",
        )
        val steps = when (software) {
            VoiceSoftware.AIVOICE, VoiceSoftware.VOICEROID2 -> listOf(
                "書き出したテキストファイルをパソコンに移して、${software.label} のエディターの「ファイル」→「テキストを開く」で開きます。",
                "「音声保存」を押して、次のように設定します。\n" +
                    "・ファイル分割:「指定された文字列で区切って複数のファイルに書き出す」、区切り文字列は $DELIMITER\n" +
                    "・ファイル形式:WAVE(ファイルヘッダーあり)か MP3\n" +
                    "・「テキストファイルを音声ファイルと一緒に保存する」にチェック\n" +
                    "・ファイル命名規則:$NAMING_RULE(連番の開始番号は1)",
            )
            VoiceSoftware.AIVOICE2 -> listOf(
                copy,
                "A.I.VOICE2 のテキストブロックに貼り付けて、「改行位置で分割」で1行ずつのブロックに分けます。",
                "「アプリケーション設定」の書き出しで、次のように設定します。\n" +
                    "・「テキストファイルを音声ファイルと一緒に保存する」にチェック\n" +
                    "・ファイル命名規則に「テキストブロック番号」を入れる",
                "「一括書き出し (すべて)」で、「テキストブロックごとにファイルに書き出す」を選んで書き出します。",
            )
            VoiceSoftware.VOICEVOX -> listOf(
                "書き出したテキストファイルをパソコンに移して、VOICEVOX の「ファイル」→「テキスト読み込み」で読み込みます(1行が1つのテキスト欄になります)。",
                "「設定」→「オプション」の「保存」で、「txtファイルを書き出し」をオンにします。文字コードは UTF-8 にします。",
                "「ファイル」→「音声書き出し」で、全部のテキスト欄を WAV で書き出します。",
            )
            VoiceSoftware.VOICEPEAK -> listOf(
                "書き出したテキストファイルをパソコンに移して、VOICEPEAK の「テキストファイルを開く」で、分割文字に「改行」を指定して開きます(1行が1ブロックになります)。",
                "出力設定で、次のように設定して書き出します。\n" +
                    "・「ブロックごとに分割して保存」をオン(命名規則の先頭は連番にする)\n" +
                    "・「セリフをファイルに保存」をオン\n" +
                    "・形式は WAV か FLAC",
            )
            VoiceSoftware.VOISONA -> listOf(
                copy,
                "VoiSona Talk の文のリストに貼り付けます(1行が1つの文になります)。",
                "書き出しで「文ごと」のファイル書き出しを選び、WAV と Text にチェックを入れて書き出します。",
            )
        }
        return steps + finish
    }

    // ---- 読み込み ----

    // 選ばれたファイル1つ分
    data class InputFile(val name: String, val uri: Uri)

    // 音声ファイルと、同じ名前のテキストの中身(なければ null)
    data class Candidate(val audioName: String, val number: Int?, val text: String?)

    data class ImportResult(
        val character: Character,
        val matchedLines: Int,              // ボイスを付けたセリフの数
        val unmatched: List<String>,        // 対応するセリフが見つからなかった音声ファイル
        val unsupported: List<String>,      // 再生できない形式(WMA など)のファイル
    )

    // 音声とセリフを対応づける。戻り値は「音声ファイル名 → セリフのID」
    fun match(character: Character, candidates: List<Candidate>): Map<String, List<String>> {
        val settings = character.aiVoice
        val prefix = speakerPrefix(settings.copy(usePreset = true))
        val withText = character.lines.filter { it.text.isNotBlank() }
        val byText = withText.groupBy { normalize(it.text) }
        val existing = character.lines.map { it.id }.toSet()
        // 連番が 0 から始まっているソフトもあるので、0 があれば 0 始まりとみなす
        val base = if (candidates.any { it.number == 0 }) 0 else 1

        val result = mutableMapOf<String, List<String>>()
        for (c in candidates) {
            // テキストの中身で探す(先頭の話者の指定は取り除く)
            val byContent = c.text?.let { text ->
                val cleaned = normalize(stripSpeaker(text, prefix, settings.prompt))
                byText[cleaned] ?: byText[normalize(text)] ?: containedLines(cleaned, withText)
            }
            if (!byContent.isNullOrEmpty()) {
                result[c.audioName] = byContent.map { it.id }
                continue
            }
            // テキストがなければ連番で探す
            val id = c.number?.let { settings.exportedLineIds.getOrNull(it - base) }
            if (c.text == null && id != null && id in existing) {
                result[c.audioName] = listOf(id)
            }
        }
        return result
    }

    // テキストに見出しなどが付いていても見つけられるよう、中に含まれているセリフのうち一番長いものを探す
    private fun containedLines(text: String, lines: List<Line>): List<Line>? {
        val best = lines
            .map { it to normalize(it.text) }
            .filter { (_, n) -> n.length >= 2 && n in text && text.length <= n.length * 2 + 40 }
            .maxByOrNull { (_, n) -> n.length } ?: return null
        return lines.filter { normalize(it.text) == best.second }
    }

    // 全角・半角、空白・改行、区切り文字の違いを無視して比べる
    // (書き出すときに区切りの文字を全角にしているので、NFKC でそろえてから比べる)
    fun normalize(text: String): String =
        Normalizer.normalize(text.removePrefix("﻿"), Normalizer.Form.NFKC)
            .replace(DELIMITER, "")
            .filterNot { it.isWhitespace() }

    private fun stripSpeaker(text: String, prefix: String, prompt: String): String {
        val t = text.removePrefix("﻿").trimStart()
        if (prefix.isNotEmpty() && t.startsWith(prefix)) return t.removePrefix(prefix)
        // 別のプリセット名のタグが付いていても外す(最初の行の、プロンプト文字列まで)
        val firstLine = t.lineSequence().firstOrNull().orEmpty()
        val index = if (prompt.isNotEmpty()) firstLine.indexOf(prompt) else -1
        return if (index in 1..40) t.substring(index + prompt.length) else t
    }

    // ファイル名の連番を取り出す。先頭が数字ならそれ(VOICEVOX の「1_キャラ_テキスト」など)、なければ最後の数字
    fun numberIn(fileName: String): Int? {
        val base = baseName(fileName)
        val digits = Regex("^\\d+").find(base) ?: Regex("(\\d+)(?!.*\\d)").find(base)
        return digits?.value?.toIntOrNull()
    }

    fun baseName(fileName: String) = fileName.substringBeforeLast('.')
    fun extension(fileName: String) = fileName.substringAfterLast('.', "").lowercase()

    // テキストファイルの文字コードを判定して読む(BOM → UTF-8 → Shift-JIS の順に試す)
    fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-31j"))
        }
    }

    // 選ばれたファイルを読み込んで、キャラのセリフにボイスを付ける
    suspend fun importVoices(context: Context, character: Character, files: List<InputFile>): ImportResult =
        withContext(Dispatchers.IO) {
            val groups = files.groupBy { baseName(it.name).lowercase() }
            val audios = files.filter { extension(it.name) in PlayableAudioExtensions }
            val unsupported = files.filter { extension(it.name) in setOf("wma", "raw") }.map { it.name }

            val candidates = audios.map { audio ->
                val txt = groups[baseName(audio.name).lowercase()]?.firstOrNull { extension(it.name) == "txt" }
                val text = txt?.let { f ->
                    runCatching {
                        context.contentResolver.openInputStream(f.uri)!!.use { decodeText(it.readBytes()) }
                    }.getOrNull()
                }
                Candidate(audio.name, numberIn(audio.name), text)
            }

            val matches = match(character, candidates)
            val voiceForLine = mutableMapOf<String, String>()
            for (audio in audios) {
                val lineIds = matches[audio.name] ?: continue
                val stored = VoiceStore.import(context, audio.uri, extension(audio.name)) ?: continue
                lineIds.forEach { voiceForLine[it] = stored }
            }

            ImportResult(
                character = character.copy(
                    lines = character.lines.map { line -> voiceForLine[line.id]?.let { line.copy(voice = it) } ?: line },
                ),
                matchedLines = voiceForLine.size,
                unmatched = audios.map { it.name }.filter { it !in matches },
                unsupported = unsupported,
            )
        }

    // フォルダの中のファイルを一覧にする(サブフォルダも1段ずつたどる)
    suspend fun listFolder(context: Context, treeUri: Uri): List<InputFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<InputFile>()
        fun walk(documentId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 3) walk(id, depth + 1)
                    } else {
                        result += InputFile(name, DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(treeUri), 0)
        result
    }

    // 個別に選ばれたファイルの名前を調べる
    suspend fun describe(context: Context, uris: List<Uri>): List<InputFile> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            name?.let { InputFile(it, uri) }
        }
    }
}
