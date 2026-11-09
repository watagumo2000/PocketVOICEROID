package com.example.widgetvoiceroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVoiceTest {

    private val character = Character(
        id = "aoi", name = "琴葉 葵", color = 0,
        lines = listOf(
            Line("a", "何か用ですか？…呼んでみただけ、ですか。", ""),
            Line("b", "おはようございます。", "", Condition.TimeOfDay(TimeSlot.MORNING)),
            Line("c", "おやすみなさい。", "", voice = "done.wav"),
            Line("d", "  ", ""),
        ),
    )

    @Test
    fun ボイスがないセリフだけ書き出す() {
        assertEquals(listOf("a", "b"), AiVoice.linesToExport(character, onlyMissing = true).map { it.id })
        assertEquals(listOf("a", "b", "c"), AiVoice.linesToExport(character, onlyMissing = false).map { it.id })
    }

    @Test
    fun 区切り文字でつないだテキストになる() {
        val text = AiVoice.buildText(AiVoice.linesToExport(character, true), AiVoiceSettings())
        assertEquals("何か用ですか？…呼んでみただけ、ですか。\r\n##\r\nおはようございます。\r\n", text)
    }

    @Test
    fun プリセットのタグを先頭に付ける() {
        val text = AiVoice.buildText(
            AiVoice.linesToExport(character, true),
            AiVoiceSettings(usePreset = true, preset = "琴葉 葵", prompt = "＞"),
        )
        assertTrue(text.startsWith("琴葉 葵＞何か用ですか？"))
    }

    @Test
    fun セリフの中の区切り文字は全角にする() {
        val text = AiVoice.buildText(listOf(Line("x", "A##B", "")), AiVoiceSettings())
        assertEquals("A＃＃B\r\n", text)
    }

    @Test
    fun BOM付きUTF8で書き出す() {
        val bytes = AiVoice.encode("あ")
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun テキストの中身で対応づける() {
        val c = character.copy(aiVoice = AiVoiceSettings(usePreset = true, preset = "琴葉 葵", prompt = "＞"))
        val result = AiVoice.match(
            c,
            listOf(
                // 1つ目はタグ付き・改行付きで出てくる
                AiVoice.Candidate("001.wav", 1, "琴葉 葵＞何か用ですか？…呼んでみただけ、ですか。\r\n"),
                AiVoice.Candidate("002.wav", 2, "\r\nおはようございます。\r\n"),
                AiVoice.Candidate("003.wav", 3, "知らないセリフ"),
            ),
        )
        assertEquals(mapOf("001.wav" to listOf("a"), "002.wav" to listOf("b")), result)
    }

    @Test
    fun 別のプリセット名のタグでも対応づける() {
        val result = AiVoice.match(character, listOf(AiVoice.Candidate("x.wav", null, "琴葉 葵（蕾）＞おはようございます。")))
        assertEquals(mapOf("x.wav" to listOf("b")), result)
    }

    @Test
    fun テキストがなければ連番で対応づける() {
        val c = character.copy(aiVoice = AiVoiceSettings(exportedLineIds = listOf("b", "a")))
        val result = AiVoice.match(
            c,
            listOf(AiVoice.Candidate("voice_001.wav", 1, null), AiVoice.Candidate("voice_002.wav", 2, null)),
        )
        assertEquals(mapOf("voice_001.wav" to listOf("b"), "voice_002.wav" to listOf("a")), result)
    }

    @Test
    fun ファイル名の連番() {
        assertEquals(12, AiVoice.numberIn("琴葉 葵_20261109_012.wav"))
        assertEquals(1, AiVoice.numberIn("001.wav"))
        assertEquals(null, AiVoice.numberIn("voice.wav"))
    }

    @Test
    fun 文字コードを判定して読む() {
        val text = "おはようございます。"
        assertEquals(text, AiVoice.decodeText(AiVoice.encode(text)))
        assertEquals(text, AiVoice.decodeText(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, AiVoice.decodeText(text.toByteArray(charset("windows-31j"))))
        assertEquals(text, AiVoice.decodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
    }
}
