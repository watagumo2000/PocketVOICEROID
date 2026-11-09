package com.example.widgetvoiceroid

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

// キャラのデータを characters.json に保存・読み込みする。アプリ・ウィジェット・通知で共通
class CharacterRepository private constructor(private val context: Context) {

    private val file = File(context.filesDir, "characters.json")
    private val mutex = Mutex()

    private val _characters = MutableStateFlow(load())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    fun find(id: String?): Character? = _characters.value.firstOrNull { it.id == id }

    // キャラを保存する(同じIDがあれば上書き、なければ追加)
    suspend fun save(character: Character) = update { list ->
        if (list.any { it.id == character.id }) {
            list.map { if (it.id == character.id) character else it }
        } else {
            list + character
        }
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    // 内蔵キャラを最初の状態に戻す
    suspend fun resetBuiltIn(id: String) {
        val seed = BuiltInCharacters.seed(id) ?: return
        save(seed.toCharacter())
    }

    // 消してしまった内蔵キャラを呼び戻す
    suspend fun restoreMissingBuiltIns() = update { list ->
        list + BuiltInCharacters.defaults().filter { d -> list.none { it.id == d.id } }
    }

    val missingBuiltIns: List<CharacterSeed>
        get() = BuiltInCharacters.seeds.filter { s -> _characters.value.none { it.id == s.id } }

    private suspend fun update(change: (List<Character>) -> List<Character>) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = change(_characters.value)
                write(updated)
                _characters.value = updated
                ImageStore.cleanUp(context, updated)
                VoiceStore.cleanUp(context, updated)
            }
        }
        // 置いてあるウィジェットにも反映する
        CharacterWidget().updateAll(context)
    }

    private fun load(): List<Character> {
        if (!file.exists()) return BuiltInCharacters.defaults()
        return runCatching { json.decodeFromString<List<Character>>(file.readText()).map(::migrate) }
            .getOrElse { e ->
                // 読めないファイルは上書きせずに退避しておく
                Log.e(TAG, "characters.json を読み込めませんでした", e)
                file.renameTo(File(file.parentFile, "characters.broken-${System.currentTimeMillis()}.json"))
                BuiltInCharacters.defaults()
            }
    }

    // 古い形式のデータを今の形に直す
    private fun migrate(character: Character): Character =
        migrateColor(character)
            .let { c -> c.copy(lines = c.lines.map { it.migrated() }) }
            .let { c -> BuiltInCharacters.seed(c.id)?.upgrade(c) ?: c }

    // 茜・葵の色を変えていなければ、デザイン変更前の色から新しい色に置き換える
    private fun migrateColor(character: Character): Character {
        val oldColor = OldBuiltInColors[character.id] ?: return character
        if (character.color != oldColor) return character
        val seed = BuiltInCharacters.seed(character.id) ?: return character
        return character.copy(color = seed.themeColor.toArgb())
    }

    private fun write(list: List<Character>) {
        // 書きかけで壊れないよう、一時ファイルに書いてから置き換える
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val TAG = "CharacterRepository"

        // デザイン変更前の茜・葵の色
        private val OldBuiltInColors = mapOf("akane" to 0xFFE0708A.toInt(), "aoi" to 0xFF5B9BD5.toInt())

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        @Volatile
        private var instance: CharacterRepository? = null

        fun get(context: Context): CharacterRepository =
            instance ?: synchronized(this) {
                instance ?: CharacterRepository(context.applicationContext).also { instance = it }
            }
    }
}
