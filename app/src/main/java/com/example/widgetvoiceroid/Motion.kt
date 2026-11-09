package com.example.widgetvoiceroid

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.widgetvoiceroid.ui.theme.rememberReduceMotion

// キャラの待機中の動き方
sealed interface IdleMotion {
    // 動かない
    data object None : IdleMotion

    // ふわふわ浮かぶ(2.8秒かけて12dp上下に往復)
    data class Floating(
        val height: Dp = 12.dp,         // 上下に揺れる幅
        val periodMillis: Int = 2800,   // 片道にかかる時間(ミリ秒)
    ) : IdleMotion
}

// 動き方に合わせて中身を動かす入れ物
@Composable
fun MotionBox(motion: IdleMotion, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    when {
        reduceMotion || motion == IdleMotion.None -> Box(modifier) { content() }
        motion is IdleMotion.Floating -> FloatingBox(motion, modifier, content)
    }
}

@Composable
private fun FloatingBox(motion: IdleMotion.Floating, modifier: Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "floating")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motion.periodMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "progress",
    )

    Box(modifier = modifier.graphicsLayer { translationY = -progress * motion.height.toPx() }) {
        content()
    }
}
