package com.example.widgetvoiceroid

import androidx.compose.ui.graphics.Color

val aoiSeed = CharacterSeed(
    id = "aoi",
    name = "葵",
    themeColor = Color(0xFFB3E4FF),  // blue
    faces = mapOf(
        Expression.NORMAL to R.drawable.aoi_normal,
        Expression.CUTE to R.drawable.aoi_cute,
        Expression.NICE to R.drawable.aoi_nice,
        Expression.SMUG to R.drawable.aoi_smug,
        Expression.POUT to R.drawable.aoi_pout,
        Expression.HMM to R.drawable.aoi_hmm,
        Expression.RELUCTANT to R.drawable.aoi_reluctant,
        Expression.RESIGNED to R.drawable.aoi_resigned,
        Expression.CONFESSION to R.drawable.aoi_confession,
        Expression.SISTER_TALK to R.drawable.aoi_sister_talk,
        Expression.ANGRY to R.drawable.aoi_angry,
        Expression.SHY_1 to R.drawable.aoi_shy_1,
        Expression.SHY_2 to R.drawable.aoi_shy_2,
        Expression.IGNORE to R.drawable.aoi_ignore,
        Expression.YUMMY to R.drawable.aoi_yummy,
    ),
    lines = listOf(
        // 普通
        SeedLine("何か用ですか？…呼んでみただけ、ですか。", Expression.NORMAL),
        SeedLine("今日はどんなことをしてたんですか？", Expression.NORMAL),
        SeedLine("私、ここから見守ってますからね。", Expression.NORMAL),
        // かわいい
        SeedLine("こんにちは、琴葉葵です。", Expression.CUTE),
        SeedLine("今日も一日おつかれさまです。", Expression.CUTE),
        SeedLine("ふふ、呼んでくれてうれしいです。", Expression.CUTE),
        SeedLine("一緒にいると、なんだか落ち着きますね。", Expression.CUTE),
        SeedLine("", Expression.CUTE),
        // いいですね
        SeedLine("いいですねぇ、その調子です。", Expression.NICE),
        SeedLine("ちゃんとビルド通りましたか？えらいです。", Expression.NICE),
        // ふふん
        SeedLine("ふふん、今日の私はちょっと調子いいんです。", Expression.SMUG),
        SeedLine("お姉ちゃんより、私の方がしっかりしてますから。", Expression.SMUG),
        // むすっ
        SeedLine("…さっきから放っておかれてた気がするんですけど。", Expression.POUT),
        SeedLine("お姉ちゃんばっかり構って、ずるいです。", Expression.POUT),
        // むむっ
        SeedLine("むむっ、そのコード、どこかでエラー出てませんか？", Expression.HMM),
        SeedLine("その課題、締め切りはいつでしたっけ？", Expression.HMM),
        // やだなぁ
        SeedLine("元気ですか！？私は元気です！", Expression.RELUCTANT),
        SeedLine("人生楽しんでいきましょう！", Expression.RELUCTANT),
        // 仕方ないなぁ
        SeedLine("仕方ないですね、もう少しだけ付き合ってあげます。", Expression.RESIGNED),
        SeedLine("お姉ちゃん、また何かやらかしたみたいです…。", Expression.RESIGNED),
        // 告白
        SeedLine("実は…お姉ちゃんのプリン食べたの、私です。", Expression.CONFESSION),
        // 姉語り
        SeedLine("お姉ちゃんの話ですか？いくらでも話しますよ。", Expression.SISTER_TALK),
        SeedLine("お姉ちゃん、ああ見えて結構がんばり屋さんなんです。", Expression.SISTER_TALK),
        // 怒
        SeedLine("もう、ちゃんと休憩してくださいってば！", Expression.ANGRY),
        SeedLine("スマホばっかり見てないで、目を休めてください！", Expression.ANGRY),
        // 照れ
        SeedLine("…そんなにじっと見られると、恥ずかしいです。", Expression.SHY_1),
        SeedLine("な、なでても何も出ませんよ？", Expression.SHY_1),
        SeedLine("べ、別に嬉しくなんてないですから。", Expression.SHY_2),
        SeedLine("そ、そういうことは急に言わないでください…！", Expression.SHY_2),
        SeedLine("ｵﾈｰﾁｬｰﾝ！", Expression.SHY_2),
        // 知らんぷり
        SeedLine("…知りません。", Expression.IGNORE),
        SeedLine("聞こえなかったことにしておきますね。", Expression.IGNORE),

        // 美味しそう
        SeedLine("美味しそう…お腹すいてきちゃいました。", Expression.YUMMY),
        SeedLine("甘いもの、食べたくなってきました。", Expression.YUMMY),
        SeedLine("ﾁｮｺﾐﾝﾄｱｲｽ!!!", Expression.YUMMY),
    ),
    timeLines = mapOf(
        TimeSlot.MORNING to listOf(
            SeedLine("おはようございます。今日も一日がんばりましょうね。", Expression.CUTE),
            SeedLine("…まだちょっと眠いです。", Expression.NORMAL),
            SeedLine("朝ごはんは、ちゃんと食べてくださいね。", Expression.NORMAL),
            SeedLine("お姉ちゃん、まだ寝てるみたいです…。", Expression.RESIGNED),
        ),
        TimeSlot.DAYTIME to listOf(
            SeedLine("お昼ごはん、ちゃんと食べましたか？", Expression.NORMAL),
            SeedLine("午後もあと少し、がんばりましょう。", Expression.NICE),
            SeedLine("作業中ですか？無理しすぎないでくださいね。", Expression.NORMAL),
            SeedLine("眠くなる時間ですね…ちょっとだけ休憩しましょう。", Expression.RESIGNED),
        ),
        TimeSlot.EVENING to listOf(
            SeedLine("おつかれさまです。今日はどんな一日でしたか？", Expression.CUTE),
            SeedLine("晩ごはん、何にしましょうか。", Expression.YUMMY),
            SeedLine("今日もよくがんばりましたね。", Expression.NICE),
            SeedLine("お風呂、先に入っちゃってくださいね。", Expression.NORMAL),
        ),
        TimeSlot.NIGHT to listOf(
            SeedLine("そろそろ寝る準備しませんか？", Expression.NORMAL),
            SeedLine("明日も早いんじゃないですか…？", Expression.HMM),
            SeedLine("明日の準備、もう済ませましたか？", Expression.HMM),
            SeedLine("おやすみなさい…って言っても、まだ寝ないんですよね。", Expression.POUT),
        ),
        TimeSlot.LATE_NIGHT to listOf(
            SeedLine("えっ、まだ起きてるんですか…？", Expression.RELUCTANT),
            SeedLine("もう、早く寝てくださいってば！", Expression.ANGRY),
            SeedLine("お姉ちゃんは、とっくに寝てますよ？", Expression.SISTER_TALK),
            SeedLine("こんな時間まで…体を壊しても知りませんからね。", Expression.POUT),
        ),
    ),
    conditionLines = listOf(
        Condition.Weekdays(setOf(Weekday.MON)) to SeedLine("月曜日ですね…無理しない程度にがんばりましょう。", Expression.RESIGNED),
        Condition.Weekdays(setOf(Weekday.FRI)) to SeedLine("金曜日です。今週もおつかれさまでした。", Expression.NICE),
        Condition.Weekdays(setOf(Weekday.SAT, Weekday.SUN)) to SeedLine("お休みの日ですね。ゆっくりできていますか？", Expression.CUTE),
        Condition.Anniversary(1, 1, "お正月") to SeedLine("あけましておめでとうございます。今年もよろしくお願いします。", Expression.CUTE),
        Condition.Anniversary(12, 25, "クリスマス") to SeedLine("メリークリスマスです。…ケーキ、ありますか？", Expression.YUMMY),
        Condition.Charging to SeedLine("充電中ですね。その間に少し休憩しませんか？", Expression.NORMAL),
        Condition.BatteryLow to SeedLine("電池が残りわずかです。充電してくださいね。", Expression.HMM),
        Condition.LongAbsence to SeedLine("…ずいぶん久しぶりですね。心配したんですから。", Expression.POUT),
        Condition.LongAbsence to SeedLine("おかえりなさい。また会えてうれしいです。", Expression.CUTE),
    ),
    motion = IdleMotion.Floating(),
)