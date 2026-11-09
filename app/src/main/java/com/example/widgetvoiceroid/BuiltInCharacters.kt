package com.example.widgetvoiceroid

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ---- 最初から入っているキャラ(茜・葵)の定義 ----
// アプリを初めて起動したとき、または「初期状態に戻す」ときに、ここから編集用のデータを作る

// 内蔵キャラの表情の種類。label は編集画面に出る名前
enum class Expression(val label: String) {
    NORMAL("いつもの"),
    SMILE("笑顔"),
    CUTE("かわいい"),
    NICE("いいね"),
    SMUG("ふふん"),
    POUT("むすっ"),
    HMM("むむっ"),
    RELUCTANT("やだなぁ"),
    RESIGNED("しゃーない"),
    CONFESSION("告白"),
    SISTER_TALK("姉妹語り"),
    ANGRY("怒り"),
    ANGRY_2("怒り2"),
    SHY_1("照れ"),
    SHY_2("照れ2"),
    IGNORE("知らんぷり"),
    YUMMY("おいしそう"),
    PLEADING("ぴえん"),
}

data class SeedLine(val text: String, val expression: Expression)

class CharacterSeed(
    val id: String,
    val name: String,
    val themeColor: Color,
    val faces: Map<Expression, Int>,
    val lines: List<SeedLine>,
    val timeLines: Map<TimeSlot, List<SeedLine>> = emptyMap(),
    val conditionLines: List<Pair<Condition, SeedLine>> = emptyList(),  // 曜日・記念日・電池などのセリフ
    val motion: IdleMotion = IdleMotion.NONE,
) {
    init {
        require(Expression.NORMAL in faces) { "${name}には NORMAL の表情が必要です" }
    }

    // 編集できるキャラのデータに変換する
    fun toCharacter(): Character {
        // NORMAL を先頭(基本の表情)にする
        val expressions = faces.keys.sortedBy { it != Expression.NORMAL }
        val allLines: List<Pair<SeedLine, Condition?>> = lines.map { it to null } +
            timeLines.flatMap { (slot, list) -> list.map { it to Condition.TimeOfDay(slot) } } +
            conditionLines.map { (condition, line) -> line to condition }

        return Character(
            id = id,
            name = name,
            color = themeColor.toArgb(),
            faces = expressions.map { Face(it.name, it.label, ImageRef.BuiltIn(id, it.name)) },
            lines = allLines.mapIndexed { i, (line, condition) ->
                val face = if (line.expression in faces) line.expression else Expression.NORMAL
                Line(id = "$id-$i", text = line.text, faceId = face.name, condition = condition)
            },
            floating = motion != IdleMotion.NONE,
            idleMotion = motion,
            seedRevision = SEED_REVISION,
        )
    }

    // 初期データの更新で増えたセリフを、すでに保存されているキャラに足す(編集内容はそのまま)
    fun upgrade(character: Character): Character {
        if (character.seedRevision >= SEED_REVISION) return character
        var lines = character.lines
        if (character.seedRevision < 2) {
            // 2: 曜日・記念日・電池・久しぶりのセリフを追加
            val added = toCharacter().lines.filter { line ->
                val c = line.condition
                c != null && c !is Condition.TimeOfDay && lines.none { it.id == line.id }
            }
            lines = lines + added
        }
        return character.copy(lines = lines, seedRevision = SEED_REVISION)
    }

    companion object {
        // 初期データの版。セリフを足したら上げて、upgrade() に足し方を書く
        const val SEED_REVISION = 2
    }
}

object BuiltInCharacters {
    val seeds: List<CharacterSeed> get() = listOf(aoiSeed, akaneSeed)

    fun seed(id: String): CharacterSeed? = seeds.firstOrNull { it.id == id }

    fun defaults(): List<Character> = seeds.map { it.toCharacter() }

    // 内蔵画像のリソースIDを探す
    @DrawableRes
    fun drawableFor(ref: ImageRef.BuiltIn): Int? {
        val expression = Expression.entries.firstOrNull { it.name == ref.expression } ?: return null
        return seed(ref.characterId)?.faces?.get(expression)
    }
}
