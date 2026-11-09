package com.example.widgetvoiceroid

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// 再生できるボイスの拡張子(WMA は Android で再生できない)
val PlayableAudioExtensions = setOf("wav", "mp3", "m4a", "aac", "ogg", "flac")

// ---- ボイスのファイルを保存する ----
object VoiceStore {
    private const val TAG = "VoiceStore"

    private fun dir(context: Context) = File(context.filesDir, "voices").apply { mkdirs() }

    fun file(context: Context, fileName: String) = File(dir(context), fileName)

    // 端末のファイルをアプリ内にコピーする。失敗したら null
    suspend fun import(context: Context, uri: Uri, extension: String): String? = withContext(Dispatchers.IO) {
        val ext = extension.lowercase().ifBlank { "wav" }
        val target = File(dir(context), "${UUID.randomUUID()}.$ext")
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            check(target.length() > 0) { "空のファイルです" }
            target.name
        }.getOrElse { e ->
            Log.w(TAG, "ボイスを取り込めませんでした: $uri", e)
            target.delete()
            null
        }
    }

    // どのセリフからも使われなくなったボイスを消す
    fun cleanUp(context: Context, characters: List<Character>) {
        val used = characters.flatMap { it.lines }.mapNotNull { it.voice }.toSet()
        dir(context).listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }
}

// ---- ボイスを鳴らすかどうか(アプリ全体の設定) ----
object VoiceSettings {
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "voice_enabled"

    @Volatile
    private var state: MutableStateFlow<Boolean>? = null

    fun enabled(context: Context): StateFlow<Boolean> = flow(context).asStateFlow()

    fun isEnabled(context: Context): Boolean = flow(context).value

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        flow(context).value = enabled
        if (!enabled) VoicePlayer.stop()
    }

    private fun flow(context: Context): MutableStateFlow<Boolean> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(
                context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED, true),
            ).also { state = it }
        }
}

// ---- ボイスを鳴らす(前のボイスが鳴っていたら止める) ----
object VoicePlayer {
    private const val TAG = "VoicePlayer"
    private var player: MediaPlayer? = null

    // force が false のときは、設定でボイスがオフなら鳴らさない
    @Synchronized
    fun play(context: Context, fileName: String?, force: Boolean = false) {
        if (fileName == null) return
        if (!force && !VoiceSettings.isEnabled(context)) return
        val file = VoiceStore.file(context, fileName)
        if (!file.exists()) return
        stop()
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(file.path)
            mp.setOnCompletionListener { release(it) }
            mp.setOnErrorListener { p, _, _ -> release(p); true }
            mp.prepare()
            mp.start()
            player = mp
        }.onFailure { Log.w(TAG, "ボイスを再生できませんでした: $fileName", it) }
    }

    @Synchronized
    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    @Synchronized
    private fun release(mp: MediaPlayer) {
        mp.release()
        if (player === mp) player = null
    }
}
