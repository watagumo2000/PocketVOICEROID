package com.example.widgetvoiceroid

import androidx.compose.ui.graphics.Color

val akaneSeed = CharacterSeed(
    id = "akane",
    name = "茜",
    themeColor = Color(0xFFFFB3BE),  // pink
    faces = mapOf(
        Expression.NORMAL to R.drawable.akane_smile_mouth_closed,
        Expression.SMILE to R.drawable.akane_smile,
        Expression.NICE to R.drawable.akane_nice,
        Expression.SMUG to R.drawable.akane_smug,
        Expression.POUT to R.drawable.akane_pout,
        Expression.HMM to R.drawable.akane_hmm,
        Expression.RELUCTANT to R.drawable.akane_reluctant,
        Expression.RESIGNED to R.drawable.akane_resigned,
        Expression.CONFESSION to R.drawable.akane_confession,
        Expression.SISTER_TALK to R.drawable.akane_sister_talk,
        Expression.ANGRY to R.drawable.akane_angry,
        Expression.ANGRY_2 to R.drawable.akane_angry_2,
        Expression.SHY_1 to R.drawable.akane_shy_1,
        Expression.SHY_2 to R.drawable.akane_shy_2,
        Expression.IGNORE to R.drawable.akane_ignore,
        Expression.YUMMY to R.drawable.akane_yummy,
        Expression.PLEADING to R.drawable.akane_pleading,
    ),
    lines = listOf(
        // 普通
        SeedLine("琴葉茜や！よろしくやで！", Expression.NORMAL),
        SeedLine("なんや、呼んだ？", Expression.NORMAL),
        SeedLine("今日はなにしとったん？うちにも教えてぇな。", Expression.NORMAL),
        SeedLine("うち、ここでずーっと見とるからな！", Expression.NORMAL),
        // デフォルト(笑顔)
        SeedLine("今日もええ感じやね、その調子や！", Expression.SMILE),
        SeedLine("呼んでくれてうれしいわぁ！", Expression.SMILE),
        SeedLine("一緒におると楽しいなぁ！", Expression.SMILE),
        SeedLine("なんか楽しそうなことしてるなぁ", Expression.SMILE),
        // ええですなぁ
        SeedLine("ええなぁ、そのコードちゃんと動いとるやん！", Expression.NICE),
        SeedLine("ようできとるやん！さすがやな！", Expression.NICE),
        // ふふん
        SeedLine("ふふん、うちに任しとき！", Expression.SMUG),
        SeedLine("お姉ちゃんやからな、なんでも知っとるで！", Expression.SMUG),
        // むすっ
        SeedLine("…なんや、葵ばっかり構って。", Expression.POUT),
        SeedLine("うちのことも、もっと構ってぇや。", Expression.POUT),
        // むむっ
        SeedLine("むむっ、なんか怪しい匂いがするで…", Expression.HMM),
        SeedLine("エラー出とる？…うちにはさっぱりわからへん！", Expression.HMM),
        // やめてーや
        SeedLine("やめてーや、そういうのうち苦手やねん！", Expression.RELUCTANT),
        SeedLine("勉強？…あ、うちちょっと用事思い出したわ。", Expression.RELUCTANT),
        // しゃーないな
        SeedLine("しゃーないなぁ、ちょっとだけ手伝ったるわ。", Expression.RESIGNED),
        SeedLine("また葵に怒られてもうた…しゃーないな。", Expression.RESIGNED),
        // 告白
        SeedLine("実はな…葵のプリン食べたん、うちやねん。", Expression.CONFESSION),
        // 妹語り
        SeedLine("うちの妹はほんまにかわいいんやで！", Expression.SISTER_TALK),
        SeedLine("葵はしっかり者でな、うちの自慢の妹やねん。", Expression.SISTER_TALK),
        // 怒
        SeedLine("夜ふかしはあかんでー、ちゃんと寝えや！", Expression.ANGRY),
        SeedLine("ごはん抜いたらあかんで！", Expression.ANGRY),
        SeedLine("もー！何回言うたらわかるん！", Expression.ANGRY_2),
        SeedLine("無理しすぎたらあかんって言うとるやろ！", Expression.ANGRY_2),
        // 照れ
        SeedLine("な、なんやねん、そんな見つめんといてぇな…", Expression.SHY_1),
        SeedLine("う、うちにエビフライくれるんか...", Expression.SHY_1),
        SeedLine("べ、別に照れてへんし！", Expression.SHY_2),
        SeedLine("そ、そんな急に褒めんといてや…", Expression.SHY_1),
        // 知らんぷり
        SeedLine("…うちはなにも知らんで", Expression.IGNORE),
        SeedLine("～♪ なーんも聞こえへんなぁ。", Expression.IGNORE),
        // 美味しそう
        SeedLine("お腹すいたわぁ、なんか食べに行かへん？", Expression.YUMMY),
        SeedLine("エビフライ食べたいなぁ…", Expression.YUMMY),
        // ぴえん
        SeedLine("なあなあ、ちょっとだけ構ってぇな…", Expression.PLEADING),
        SeedLine("置いてかんといてぇな…", Expression.PLEADING),
    ),
    timeLines = mapOf(
        TimeSlot.MORNING to listOf(
            SeedLine("おはようさん！今日もええ天気やなぁ！", Expression.SMILE),
            SeedLine("ふあぁ…あと5分だけ寝かせてぇ…", Expression.PLEADING),
            SeedLine("朝ごはんはしっかり食べなあかんで！", Expression.SMILE),
            SeedLine("今日も葵に起こしてもろたわ…えへへ。", Expression.SISTER_TALK),
        ),
        TimeSlot.DAYTIME to listOf(
            SeedLine("お昼や！なに食べるん？", Expression.YUMMY),
            SeedLine("午後もその調子でいこか！", Expression.SMUG),
            SeedLine("ちゃんと水分とっとるか？", Expression.NORMAL),
            SeedLine("眠たなってきたなぁ…ちょっとお昼寝せぇへん？", Expression.PLEADING),
        ),
        TimeSlot.EVENING to listOf(
            SeedLine("おつかれさん！今日もようがんばったな！", Expression.SMILE),
            SeedLine("晩ごはんなにかなぁ、楽しみやわぁ。", Expression.YUMMY),
            SeedLine("今日はなんかええことあった？", Expression.NORMAL),
            SeedLine("お風呂入ってさっぱりしよか！", Expression.SMILE),
        ),
        TimeSlot.NIGHT to listOf(
            SeedLine("そろそろ寝よか。夜ふかしはあかんで？", Expression.NORMAL),
            SeedLine("葵もう寝てもうたかなぁ。", Expression.SISTER_TALK),
            SeedLine("明日の準備はできとるか？", Expression.HMM),
            SeedLine("今日もありがとうな。おやすみ！", Expression.SMILE),
        ),
        TimeSlot.LATE_NIGHT to listOf(
            SeedLine("こらー！こんな時間まで何しとるん！", Expression.ANGRY_2),
            SeedLine("…しゃーないなぁ、ちょっとだけ付き合ったるわ。", Expression.RESIGNED),
            SeedLine("スマホ置いて、はよ寝えや！", Expression.ANGRY),
            SeedLine("うちもう眠たいわ…ほな、先寝るで…", Expression.RESIGNED),
        ),
    ),
    conditionLines = listOf(
        Condition.Weekdays(setOf(Weekday.MON)) to SeedLine("月曜日かぁ…今週もいっしょにがんばろな！", Expression.RESIGNED),
        Condition.Weekdays(setOf(Weekday.FRI)) to SeedLine("金曜日や！あとちょっとでお休みやで！", Expression.NICE),
        Condition.Weekdays(setOf(Weekday.SAT, Weekday.SUN)) to SeedLine("週末や！どっか遊びに行かへん？", Expression.SMILE),
        Condition.Anniversary(1, 1, "お正月") to SeedLine("あけましておめでとう！今年もよろしくやで！", Expression.SMILE),
        Condition.Anniversary(12, 25, "クリスマス") to SeedLine("メリークリスマス！ケーキ食べよか！", Expression.YUMMY),
        Condition.Charging to SeedLine("充電中やな。その間にちょっと休憩しよか。", Expression.NORMAL),
        Condition.BatteryLow to SeedLine("電池もうあらへんで！はよ充電しいや！", Expression.ANGRY),
        Condition.LongAbsence to SeedLine("どこ行っとったん！うち、ずっと待っとったんやで…", Expression.PLEADING),
        Condition.LongAbsence to SeedLine("久しぶりやなぁ！元気にしとった？", Expression.SMILE),
    ),
    motion = IdleMotion.Floating(),
)