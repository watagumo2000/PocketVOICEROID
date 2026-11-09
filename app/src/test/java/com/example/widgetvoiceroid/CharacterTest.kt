package com.example.widgetvoiceroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterTest {

    private val defaults = BuiltInCharacters.defaults()

    @Test
    fun 内蔵キャラは基本の表情がNORMAL() {
        for (c in defaults) {
            assertEquals(Expression.NORMAL.name, c.defaultFace?.id)
        }
    }

    @Test
    fun 内蔵キャラのセリフは全部引き継がれる() {
        val akane = defaults.first { it.id == "akane" }
        val seed = BuiltInCharacters.seed("akane")!!
        assertEquals(
            seed.lines.size + seed.timeLines.values.sumOf { it.size } + seed.conditionLines.size,
            akane.lines.size,
        )
        assertEquals(4, akane.lines.count { it.condition == Condition.TimeOfDay(TimeSlot.MORNING) })
        // どのセリフも、そのキャラが持っている表情を指している
        for (c in defaults) {
            val faceIds = c.faces.map { it.id }.toSet()
            assertTrue(c.lines.all { it.faceId in faceIds })
        }
    }

    @Test
    fun 内蔵画像はリソースが見つかる() {
        for (c in defaults) for (face in c.faces) {
            assertTrue(BuiltInCharacters.drawableFor(face.image as ImageRef.BuiltIn) != null)
        }
    }

    @Test
    fun JSONに保存して読み戻しても同じ() {
        val custom = Character(
            id = newId(),
            name = "テスト",
            color = 0xFF123456.toInt(),
            faces = listOf(Face("f1", "笑顔", ImageRef.Stored("a.png"))),
            lines = listOf(
                Line("l1", "こんにちは", "f1", Condition.TimeOfDay(TimeSlot.NIGHT)),
                Line("l2", "月曜", "f1", Condition.Weekdays(setOf(Weekday.MON, Weekday.FRI))),
                Line("l3", "誕生日", "f1", Condition.Anniversary(4, 25, "誕生日")),
                Line("l4", "充電", "f1", Condition.Charging),
                Line("l5", "電池", "f1", Condition.BatteryLow),
                Line("l6", "久しぶり", "f1", Condition.LongAbsence),
            ),
            floating = false,
        )
        val list = defaults + custom
        val json = CharacterRepository.json
        assertEquals(list, json.decodeFromString<List<Character>>(json.encodeToString(list)))
    }

    @Test
    fun 直前と同じセリフは避ける() {
        val c = Character(
            id = "c", name = "c", color = 0,
            lines = listOf(Line("a", "A", ""), Line("b", "B", "")),
        )
        repeat(20) { assertNotEquals("a", c.randomLine(except = c.lines[0]).id) }
    }

    @Test
    fun 時間帯のセリフがなければ普段のセリフ() {
        val c = Character(
            id = "c", name = "c", color = 0,
            lines = listOf(Line("usual", "普段", ""), Line("night", "夜", "", Condition.TimeOfDay(TimeSlot.NIGHT))),
        )
        assertEquals("night", c.speakTo(situation(slot = TimeSlot.NIGHT)).id)
        assertEquals("usual", c.speakTo(situation(slot = TimeSlot.MORNING)).id)
        assertEquals("usual", c.randomLine().id)
    }

    @Test
    fun セリフも表情もないキャラでも落ちない() {
        val c = Character(id = "c", name = "c", color = 0)
        assertEquals(Character.emptyLine, c.randomLine())
        assertEquals(Character.emptyLine, c.speakTo(Situation.now()))
        assertEquals(null, c.face("x"))
    }

    private fun situation(
        slot: TimeSlot = TimeSlot.DAYTIME,
        weekday: Weekday = Weekday.WED,
        month: Int = 6,
        day: Int = 10,
        charging: Boolean = false,
        batteryLow: Boolean = false,
        longAbsence: Boolean = false,
    ) = Situation(slot, weekday, month, day, charging, batteryLow, longAbsence)

    private val conditional = Character(
        id = "c", name = "c", color = 0,
        lines = listOf(
            Line("usual", "普段", ""),
            Line("mon", "月曜", "", Condition.Weekdays(setOf(Weekday.MON))),
            Line("xmas", "クリスマス", "", Condition.Anniversary(12, 25)),
            Line("charge", "充電", "", Condition.Charging),
            Line("low", "電池", "", Condition.BatteryLow),
            Line("away", "久しぶり", "", Condition.LongAbsence),
        ),
    )

    @Test
    fun 曜日のセリフはその曜日だけ() {
        assertEquals("mon", conditional.speakTo(situation(weekday = Weekday.MON)).id)
        assertEquals("usual", conditional.speakTo(situation(weekday = Weekday.TUE)).id)
    }

    @Test
    fun 記念日は優先される() {
        repeat(20) {
            assertEquals("xmas", conditional.speakTo(situation(month = 12, day = 25, weekday = Weekday.MON, charging = true)).id)
        }
    }

    @Test
    fun 久しぶりは記念日より優先される() {
        assertEquals("away", conditional.speakTo(situation(month = 12, day = 25, longAbsence = true)).id)
        assertEquals("away", conditional.absenceLine()?.id)
    }

    @Test
    fun 充電と電池() {
        assertEquals("charge", conditional.speakTo(situation(charging = true)).id)
        assertEquals("low", conditional.speakTo(situation(batteryLow = true)).id)
    }

    @Test
    fun 条件付きのセリフは普段のセリフに混ざらない() {
        repeat(20) { assertEquals("usual", conditional.randomLine().id) }
    }

    @Test
    fun 古い形式の時間帯は条件に移される() {
        val old = """[{"id":"c","name":"c","color":0,"lines":[{"id":"l","text":"夜","faceId":"","slot":"NIGHT"}]}]"""
        val line = CharacterRepository.json.decodeFromString<List<Character>>(old).first().lines.first().migrated()
        assertEquals(Condition.TimeOfDay(TimeSlot.NIGHT), line.condition)
        assertEquals(null, line.legacySlot)
    }

    @Test
    fun 内蔵キャラに新しいセリフ例が一度だけ足される() {
        val seed = BuiltInCharacters.seed("aoi")!!
        // 前の版のデータ(条件付きのセリフなし、名前は編集済み)
        val saved = seed.toCharacter().let { c ->
            c.copy(
                name = "琴葉 葵",
                lines = c.lines.filter { it.condition == null || it.condition is Condition.TimeOfDay },
                seedRevision = 0,
            )
        }
        val upgraded = seed.upgrade(saved)
        assertEquals("琴葉 葵", upgraded.name)
        assertEquals(seed.toCharacter().lines.size, upgraded.lines.size)
        // 足したセリフを消して保存したら、もう足されない
        val edited = upgraded.copy(lines = upgraded.lines.filter { it.condition !is Condition.LongAbsence })
        assertEquals(edited, seed.upgrade(edited))
    }

    @Test
    fun 曜日のラベル() {
        assertEquals("週末", Condition.Weekdays(setOf(Weekday.SAT, Weekday.SUN)).label)
        assertEquals("平日", Condition.Weekdays(Weekday.entries.toSet() - setOf(Weekday.SAT, Weekday.SUN)).label)
        assertEquals("月・金", Condition.Weekdays(setOf(Weekday.FRI, Weekday.MON)).label)
    }
}
