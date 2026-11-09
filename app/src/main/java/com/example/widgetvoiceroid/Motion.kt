package com.example.widgetvoiceroid

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.widgetvoiceroid.ui.theme.rememberReduceMotion
import kotlinx.serialization.Serializable

// キャラの待機中の動き方
@Serializable
enum class IdleMotion(val label: String, val description: String) {
    NONE("動かない", "じっと待っています"),
    FLOAT("ふわふわ", "ゆっくり上下に揺れます"),
    SWAY("ゆらゆら", "足元を軸に、左右にゆっくり傾きます"),
    HOP("ぴょこぴょこ", "ときどき小さく2回跳ねます"),
    BREATHE("すーはー", "息をするように、少しだけふくらみます"),
}

// 動き方に合わせて中身を動かす入れ物。「動きを減らす」設定のときは動かさない
@Composable
fun MotionBox(motion: IdleMotion, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    if (reduceMotion || motion == IdleMotion.NONE) {
        Box(modifier) { content() }
        return
    }
    val progress = rememberMotionProgress(motion)
    Box(modifier.graphicsLayer { applyMotion(motion, progress.value) }) { content() }
}

// 0〜1 を行き来する(HOP は 0 から跳ねて 0 に戻る)値
@Composable
private fun rememberMotionProgress(motion: IdleMotion): State<Float> {
    val transition = rememberInfiniteTransition(label = "idle-motion")
    val spec: InfiniteRepeatableSpec<Float> = when (motion) {
        IdleMotion.HOP -> infiniteRepeatable(
            // 選んですぐ跳ねて、そのあと休む(休みから始めると、動いていないように見える)
            animation = keyframes {
                durationMillis = 2800
                0f at 0 using FastOutSlowInEasing
                1f at 150 using FastOutLinearInEasing   // 1回目:上がって落ちる
                0f at 300 using FastOutSlowInEasing
                0.55f at 430 using FastOutLinearInEasing // 2回目は低め
                0f at 560
                0f at 2800                               // 休み
            },
            repeatMode = RepeatMode.Restart,
        )
        else -> infiniteRepeatable(
            animation = tween(
                durationMillis = when (motion) {
                    IdleMotion.SWAY -> 2400
                    IdleMotion.BREATHE -> 2000
                    else -> 2800
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        )
    }
    return transition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = spec, label = "progress")
}

// 画像は下揃えで置いているので、傾き・ふくらみは足元(下の真ん中)を軸にする
private fun GraphicsLayerScope.applyMotion(motion: IdleMotion, p: Float) {
    when (motion) {
        IdleMotion.NONE -> Unit
        IdleMotion.FLOAT -> translationY = -p * 12.dp.toPx()
        IdleMotion.SWAY -> {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = (p * 2 - 1) * 3f
        }
        IdleMotion.HOP -> translationY = -p * 18.dp.toPx()
        IdleMotion.BREATHE -> {
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleY = 1f + p * 0.025f
            scaleX = 1f + p * 0.01f
        }
    }
}
