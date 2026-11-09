package com.example.widgetvoiceroid

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

// ---- A.I.VOICE Editor との受け渡し ----
//
// 1. アプリ → A.I.VOICE:セリフを区切り文字 "##" でつないだテキストファイルを書き出す。
//    A.I.VOICE で「テキストを開く」→「音声ファイルを保存」で
//    「指定された文字列で区切って複数のファイルに書き出す」(区切り文字列 ##)を選ぶと、セリフ1つが音声1ファイルになる。
// 2. A.I.VOICE → アプリ:書き出したフォルダ(またはファイル)を選ぶと、
//    一緒に保存されたテキストファイル(.txt)の中身でセリフと対応づける。
//    .txt がなければ、ファイル名の連番({Number})と書き出した順番で対応づける。
object AiVoice {
    const val DELIMITER = "##"

    // ファイル名の規則のおすすめ(連番3桁)
    const val NAMING_RULE = "{Number}"

    // ---- 書き出し ----

    // 書き出すセリフ。onlyMissing なら、まだボイスがないセリフだけ
    fun linesToExport(character: Character, onlyMissing: Boolean): List<Line> =
        character.lines.filter { it.text.isNotBlank() && (!onlyMissing || it.voice == null) }

    // A.I.VOICE で開くテキスト(改行は Windows に合わせて CRLF)
    fun buildText(lines: List<Line>, settings: AiVoiceSettings): String {
        val tag = if (settings.usePreset && settings.preset.isNotBlank()) {
            settings.preset.trim() + settings.prompt
        } else {
            ""
        }
        val body = lines.joinToString("\r\n$DELIMITER\r\n") { line ->
            // セリフの中に区切り文字があると分かれてしまうので、全角にしておく
            line.text.trim().replace(DELIMITER, "＃＃").replace("\r\n", "\n").replace("\n", "\r\n")
        }
        return tag + body + "\r\n"
    }

    // UTF-8(BOM付き)のバイト列。BOM があると A.I.VOICE が文字コードを確実に判定できる
    fun encode(text: String): ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
        text.toByteArray(Charsets.UTF_8)

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
        val prefix = if (settings.usePreset && settings.preset.isNotBlank()) settings.preset.trim() + settings.prompt else ""
        val byText = character.lines
            .filter { it.text.isNotBlank() }
            .groupBy { normalize(it.text) }
        val existing = character.lines.map { it.id }.toSet()

        val result = mutableMapOf<String, List<String>>()
        for (c in candidates) {
            // テキストの中身で探す(先頭のボイスプリセットのタグは取り除く)
            val byContent = c.text?.let { text ->
                val cleaned = stripTag(text, prefix, settings.prompt)
                byText[normalize(cleaned)] ?: byText[normalize(text)]
            }
            if (!byContent.isNullOrEmpty()) {
                result[c.audioName] = byContent.map { it.id }
                continue
            }
            // テキストがなければ連番で探す(開始番号1)
            val id = c.number?.let { settings.exportedLineIds.getOrNull(it - 1) }
            if (c.text == null && id != null && id in existing) {
                result[c.audioName] = listOf(id)
            }
        }
        return result
    }

    // 空白・改行・区切り文字の違いを無視して比べる
    fun normalize(text: String): String =
        text.removePrefix("﻿").replace(DELIMITER, "").filterNot { it.isWhitespace() }

    private fun stripTag(text: String, prefix: String, prompt: String): String {
        val t = text.removePrefix("﻿").trimStart()
        if (prefix.isNotEmpty() && t.startsWith(prefix)) return t.removePrefix(prefix)
        // 別のプリセット名のタグが付いていても外す(最初の行の、プロンプト文字列まで)
        val firstLine = t.lineSequence().firstOrNull().orEmpty()
        val index = if (prompt.isNotEmpty()) firstLine.indexOf(prompt) else -1
        return if (index in 1..40) t.substring(index + prompt.length) else t
    }

    // ファイル名の最後の数字(連番)を取り出す
    fun numberIn(fileName: String): Int? =
        Regex("(\\d+)(?!.*\\d)").find(baseName(fileName))?.value?.toIntOrNull()

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
