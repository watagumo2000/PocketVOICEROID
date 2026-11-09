package com.example.widgetvoiceroid

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

// 時間帯(何時から何時まで)
enum class TimeSlot(val hours: IntRange, val label: String) {
    MORNING(5..9, "朝"),
    DAYTIME(10..16, "昼"),
    EVENING(17..21, "夕方"),
    NIGHT(22..23, "夜"),
    LATE_NIGHT(0..4, "深夜");

    companion object {
        // 時刻から時間帯を求める
        fun of(hour: Int): TimeSlot = entries.first { hour in it.hours }
    }
}

// 曜日(月曜はじまり)
enum class Weekday(val label: String) {
    MON("月"), TUE("火"), WED("水"), THU("木"), FRI("金"), SAT("土"), SUN("日");

    companion object {
        // java.time の曜日から変換する
        fun of(dayOfWeek: java.time.DayOfWeek): Weekday = entries[dayOfWeek.value - 1]
    }
}

// 電池が少ないとみなす残量(%)
const val BATTERY_LOW_PERCENT = 15

// 「久しぶり」とみなす日数
const val LONG_ABSENCE_DAYS = 3

// セリフを話す条件。セリフに付けておくと、その状況のときに話す(付けなければいつでも)
@Serializable
sealed interface Condition {
    val label: String

    // 時間帯
    @Serializable
    @SerialName("time")
    data class TimeOfDay(val slot: TimeSlot) : Condition {
        override val label get() = slot.label
    }

    // 曜日(複数選べる)
    @Serializable
    @SerialName("weekday")
    data class Weekdays(val days: Set<Weekday>) : Condition {
        override val label: String
            get() = when (days) {
                setOf(Weekday.SAT, Weekday.SUN) -> "週末"
                Weekday.entries.toSet() - setOf(Weekday.SAT, Weekday.SUN) -> "平日"
                else -> Weekday.entries.filter { it in days }.joinToString("・") { it.label }
            }
    }

    // 記念日(毎年この日)
    @Serializable
    @SerialName("date")
    data class Anniversary(val month: Int, val day: Int, val title: String = "") : Condition {
        override val label get() = "$month/$day" + if (title.isBlank()) "" else " ${title.trim()}"
    }

    // 充電中
    @Serializable
    @SerialName("charging")
    data object Charging : Condition {
        override val label get() = "充電中"
    }

    // 電池が少ない(充電していなくて残り15%以下)
    @Serializable
    @SerialName("battery_low")
    data object BatteryLow : Condition {
        override val label get() = "電池が少ない"
    }

    // 久しぶりにアプリを開いた(3日以上ぶり)
    @Serializable
    @SerialName("absence")
    data object LongAbsence : Condition {
        override val label get() = "久しぶり"
    }
}

// 今の状況。キャラはこれを見て、条件に合うセリフを選ぶ
data class Situation(
    val slot: TimeSlot,
    val weekday: Weekday,
    val month: Int,
    val day: Int,
    val charging: Boolean = false,
    val batteryLow: Boolean = false,
    val longAbsence: Boolean = false,
) {
    // この状況で、その条件のセリフを話してよいか
    fun matches(condition: Condition): Boolean = when (condition) {
        is Condition.TimeOfDay -> condition.slot == slot
        is Condition.Weekdays -> weekday in condition.days
        is Condition.Anniversary -> condition.month == month && condition.day == day
        Condition.Charging -> charging
        Condition.BatteryLow -> batteryLow
        Condition.LongAbsence -> longAbsence
    }

    companion object {
        // 現在の日時から状況を作る(電池の状態は context があれば読む)
        fun now(context: Context? = null, at: LocalDateTime = LocalDateTime.now()): Situation {
            val battery = context?.let { readBattery(it) }
            return Situation(
                slot = TimeSlot.of(at.hour),
                weekday = Weekday.of(at.dayOfWeek),
                month = at.monthValue,
                day = at.dayOfMonth,
                charging = battery?.charging ?: false,
                batteryLow = battery?.let { !it.charging && it.percent <= BATTERY_LOW_PERCENT } ?: false,
            )
        }

        private data class Battery(val percent: Int, val charging: Boolean)

        // 電池の状態は、最後に届いた通知(sticky broadcast)から読む
        private fun readBattery(context: Context): Battery? {
            val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return Battery(percent = level * 100 / scale, charging = charging)
        }
    }
}

// 最後にアプリを開いた日時を覚えておき、「久しぶり」かどうかを判定する
object Visits {
    private const val PREFS = "visits"
    private const val KEY_LAST = "last_open_millis"

    // アプリを開いたときに呼ぶ。前回から LONG_ABSENCE_DAYS 日以上たっていれば true
    fun checkIn(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST, 0L)
        prefs.edit().putLong(KEY_LAST, nowMillis).apply()
        if (last == 0L) return false  // 初めて開いたときは数えない
        return nowMillis - last >= LONG_ABSENCE_DAYS * 24L * 60 * 60 * 1000
    }
}
