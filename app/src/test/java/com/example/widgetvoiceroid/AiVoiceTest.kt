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
        // VOICEVOX は「行番号_キャラ_テキスト冒頭」なので、先頭の数字を使う
        assertEquals(3, AiVoice.numberIn("003_ずんだもん（ノーマル）_12時です.wav"))
    }

    @Test
    fun 文字コードを判定して読む() {
        val text = "おはようございます。"
        assertEquals(text, AiVoice.decodeText(AiVoice.encode(text)))
        assertEquals(text, AiVoice.decodeText(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, AiVoice.decodeText(text.toByteArray(charset("windows-31j"))))
        assertEquals(text, AiVoice.decodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun 区切り文字のソフトは区切りごとにタグを付ける() {
        val text = AiVoice.buildText(
            AiVoice.linesToExport(character, true),
            AiVoiceSettings(software = VoiceSoftware.VOICEROID2, usePreset = true, preset = "琴葉 葵", prompt = "＞"),
        )
        assertEquals("琴葉 葵＞何か用ですか？…呼んでみただけ、ですか。\r\n##\r\n琴葉 葵＞おはようございます。\r\n", text)
    }

    @Test
    fun VOICEVOXは1行1セリフでコンマを全角にする() {
        val lines = listOf(Line("x", "えっと,その\nあのね", ""), Line("y", "はい", ""))
        val plain = AiVoice.buildText(lines, AiVoiceSettings(software = VoiceSoftware.VOICEVOX))
        assertEquals("えっと，そのあのね\r\nはい\r\n", plain)
        val named = AiVoice.buildText(
            lines,
            AiVoiceSettings(software = VoiceSoftware.VOICEVOX, usePreset = true, preset = "ずんだもん（ノーマル）"),
        )
        assertEquals("ずんだもん（ノーマル）,えっと，そのあのね\r\nずんだもん（ノーマル）,はい\r\n", named)
    }

    @Test
    fun 話者を指定できないソフトはタグを付けない() {
        for (s in listOf(VoiceSoftware.AIVOICE2, VoiceSoftware.VOICEPEAK, VoiceSoftware.VOISONA)) {
            val text = AiVoice.buildText(listOf(Line("x", "あ\nい", "")), AiVoiceSettings(software = s, usePreset = true, preset = "葵"))
            assertEquals("あい\r\n", text)
        }
        assertEquals("1＜2", AiVoice.cleanText("1<2", VoiceSoftware.VOISONA))
    }

    @Test
    fun BOMを付けるかはソフトごと() {
        assertEquals(0xEF, AiVoice.encode("あ", VoiceSoftware.AIVOICE.bom)[0].toInt() and 0xFF)
        assertEquals("あ", String(AiVoice.encode("あ", VoiceSoftware.VOICEVOX.bom), Charsets.UTF_8))
    }

    @Test
    fun 文字数の上限を超えるセリフを数える() {
        val lines = listOf(Line("x", "あ".repeat(141), ""), Line("y", "い", ""))
        assertEquals(1, AiVoice.tooLong(lines, VoiceSoftware.VOICEPEAK))
        assertEquals(0, AiVoice.tooLong(lines, VoiceSoftware.VOICEVOX))
    }

    @Test
    fun 全角にしたセリフも対応づける() {
        val c = character.copy(
            lines = character.lines + listOf(Line("e", "A##B", ""), Line("f", "えっと,その", "")),
        )
        val result = AiVoice.match(
            c,
            listOf(
                AiVoice.Candidate("1.wav", 1, "A＃＃B"),
                AiVoice.Candidate("2.wav", 2, "えっと，その"),
            ),
        )
        assertEquals(mapOf("1.wav" to listOf("e"), "2.wav" to listOf("f")), result)
    }

    @Test
    fun VOICEVOXのキャラ名付きのテキストでも対応づける() {
        val c = character.copy(aiVoice = AiVoiceSettings(software = VoiceSoftware.VOICEVOX, preset = "ずんだもん"))
        val result = AiVoice.match(c, listOf(AiVoice.Candidate("1.wav", 1, "ずんだもん,おはようございます。")))
        assertEquals(mapOf("1.wav" to listOf("b")), result)
    }

    @Test
    fun 見出し付きのテキストでも対応づける() {
        val result = AiVoice.match(character, listOf(AiVoice.Candidate("1.wav", 1, "[琴葉 葵]\r\nおはようございます。")))
        assertEquals(mapOf("1.wav" to listOf("b")), result)
    }

    @Test
    fun 連番が0始まりでも対応づける() {
        val c = character.copy(aiVoice = AiVoiceSettings(exportedLineIds = listOf("b", "a")))
        val result = AiVoice.match(c, listOf(AiVoice.Candidate("0.wav", 0, null), AiVoice.Candidate("1.wav", 1, null)))
        assertEquals(mapOf("0.wav" to listOf("b"), "1.wav" to listOf("a")), result)
    }

    @Test
    fun ソフトごとの作り方がある() {
        for (s in VoiceSoftware.entries) assertTrue(AiVoice.guide(s).size >= 3)
    }
}
