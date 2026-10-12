package com.example.timetable.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 主题背景层。画在内容之下，数据驱动，内部不按主题做分支判断。
 */
@Composable
fun ThemeBackground(
    spec: BackgroundSpec,
    period: DayPeriod,
    modifier: Modifier = Modifier,
) {
    when (spec) {
        BackgroundSpec.None -> Unit
        is BackgroundSpec.TimeOfDay -> {
            val stops = spec.stops[period] ?: return
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(stops)),
            )
        }
        is BackgroundSpec.LiquidGlass -> LiquidGlassBackground(spec, modifier)
    }
}

/**
 * 四层结构：
 * L1 基底垂直渐变（静态）
 * L2 两个圆形光斑，唯一使用 RenderEffect blur 的层，完全静态不参与动画
 * L3 内容由调用方渲染（本函数不画）
 * L4 顶部高光渐变
 *
 * blur 只打静态层：滚动内容在 L3，不受 RenderEffect 开销影响。
 */
@Composable
private fun LiquidGlassBackground(
    spec: BackgroundSpec.LiquidGlass,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(spec.base)),
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = (-40).dp)
                .size(280.dp)
                .blur(radius = BLOB_BLUR, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(spec.blobA, CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 120.dp)
                .size(320.dp)
                .blur(radius = BLOB_BLUR, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(spec.blobB, CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(listOf(spec.highlight, Color.Transparent)),
                ),
        )
    }
}

private val BLOB_BLUR = 90.dp
