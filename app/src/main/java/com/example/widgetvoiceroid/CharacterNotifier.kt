package com.example.widgetvoiceroid

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

// 通知で話しかけるタイミング
enum class CallTiming(val hours: IntRange, val slot: TimeSlot) {
    WAKE_UP(7..9, TimeSlot.MORNING),        // 朝7〜9時台に起こしてくれる
    GO_TO_BED(0..3, TimeSlot.LATE_NIGHT),   // 深夜0〜3時台に怒られる
}

class CharacterNotifier(private val context: Context) {

    // 通知を出す
    fun notify(character: Character, line: Line) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return  // 許可がなければ何もしない

        ensureChannel()

        // 通知をタップしたらアプリを開く
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(faceIcon(character, line))
            .setContentTitle(character.name)
            .setContentText(line.text)
            .setColor(character.themeColor.toArgb())
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(character.id.hashCode(), notification)
    }

    // 同じ日・同じタイミングでは1回だけ通知する
    fun notifyOnce(timing: CallTiming, character: Character, line: Line) {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val key = "$today-${timing.name}"
        val prefs = context.getSharedPreferences("notifier", Context.MODE_PRIVATE)
        if (prefs.getString("last", null) == key) return

        notify(character, line)
        prefs.edit().putString("last", key).apply()
    }

    // 通知の種類(チャンネル)を登録する。設定アプリの通知設定にこの名前で出る
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "キャラからの話しかけ", NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // 立ち絵の上の方を正方形に切り抜いて、通知の顔アイコンにする(画像がなければ null)
    private fun faceIcon(character: Character, line: Line): Bitmap? {
        val ref = character.face(line.faceId)?.image ?: return null
        val full = ImageStore.loadBitmap(context, ref, ICON_SOURCE_SIZE) ?: return null
        val size = (minOf(full.width * FACE_SIZE, full.height.toFloat())).toInt().coerceAtLeast(1)
        val x = (full.width - size) / 2
        val y = (full.height * FACE_TOP).toInt().coerceIn(0, full.height - size)
        return Bitmap.createBitmap(full, x, y, size, size)
    }

    companion object {
        private const val CHANNEL_ID = "character_talk"
        private const val FACE_SIZE = 0.6f  // 切り抜く大きさ(横幅に対する割合)
        private const val FACE_TOP = 0.05f  // 切り抜く位置(上からの割合)
        private const val ICON_SOURCE_SIZE = 800  // 切り抜く前に縮小する大きさ(長い辺のピクセル数)
    }
}