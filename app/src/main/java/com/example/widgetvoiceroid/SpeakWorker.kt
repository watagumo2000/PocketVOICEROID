package com.example.widgetvoiceroid

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SpeakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val situation = Situation.now(applicationContext)
        val hour = java.time.LocalTime.now().hour

        // ウィジェットにしゃべらせる
        val manager = GlanceAppWidgetManager(applicationContext)
        val widget = CharacterWidget()
        for (id in manager.getGlanceIds(CharacterWidget::class.java)) {
            widget.speak(applicationContext, id, situation)
        }

        // 通知のタイミングなら、どちらかが話しかけてくる
        val timing = CallTiming.entries.firstOrNull { hour in it.hours }
        if (timing != null) {
            val characters = CharacterRepository.get(applicationContext).characters.value
            val character = characters.filter { it.lines.isNotEmpty() }.randomOrNull()
            if (character != null) {
                // 通知のときは、通知の時間帯として話す(記念日や電池の状態などはそのまま)
                val line = character.speakTo(situation.copy(slot = timing.slot))
                CharacterNotifier(applicationContext).notifyOnce(timing, character, line)
            }
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "character_speak"

        // 30分おきの実行を予約する(すでに予約済みなら何もしない)
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SpeakWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}