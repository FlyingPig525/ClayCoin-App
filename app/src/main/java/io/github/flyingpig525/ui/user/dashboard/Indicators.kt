package io.github.flyingpig525.ui.user.dashboard

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInExpo
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.flyingpig525.data.user.CLAYCOIN_INCREMENT_MS
import io.github.flyingpig525.fontFamily
import io.github.flyingpig525.ui.theme.ClayCoinTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ClaycoinDisplay(
    coins: Long,
    coinIncreaseWaitMillis: Int = CLAYCOIN_INCREMENT_MS,
    startOffsetMs: Int = 0,
    increaseCoins: () -> Unit
) = Box(contentAlignment = Alignment.Center) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    val transition = rememberInfiniteTransition()
    // The current rotation around the circle, so we know where to start the rotation from
    val currentRotation by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(
            animation =
                tween(
                    durationMillis = coinIncreaseWaitMillis,
                    easing = LinearEasing
                ),
            initialStartOffset = StartOffset(startOffsetMs, StartOffsetType.FastForward)
        )
    )
    val color by transition.animateColor(
        ProgressIndicatorDefaults.circularColor,
        ProgressIndicatorDefaults.circularDeterminateTrackColor,
        infiniteRepeatable(
            animation =
                tween(
                    durationMillis = coinIncreaseWaitMillis,
                    easing = EaseInExpo
                ),
            initialStartOffset = StartOffset(startOffsetMs, StartOffsetType.FastForward)
        )
    )
    LaunchedEffect(currentRotation) {
//        var d = (coinIncreaseWaitMillis - startOffsetMs).toLong()
//        while (true) {
//            delay(d)
//            increaseCoins()
//            d = coinIncreaseWaitMillis.toLong()
//        }
        if (currentRotation < 0.002f) increaseCoins()
    }

    SemiCircularProgressIndicator(
        progress = { currentRotation },
        strokeWidth = 20.dp,
        modifier = Modifier
            .rotate(225f)
            .scale(0.75f),
        color = color
    )

    Text(
        text = "$coins",
        fontSize = 45.sp,
        fontFamily = fontFamily,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Claycoins",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = screenWidth / 2 + 10.dp),
        textAlign = TextAlign.Center,
        fontSize = 25.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun ShinerProgressIndicator(
    progress: Int,
    shiners: Double,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition()
        val shinerKerning by transition.animateFloat(
            initialValue = LocalTextStyle.current.letterSpacing.value,
            targetValue = LocalTextStyle.current.letterSpacing.value + 3,
            animationSpec = infiniteRepeatable<Float>(
                animation = tween(
                    durationMillis = 3000,
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shiner text kerning float animation"
        )
        Text(
            "$shiners Shiners",
            letterSpacing = if (shiners != 0.0) shinerKerning.sp else TextUnit.Unspecified
        )
        val color = ProgressIndicatorDefaults.linearColor
        val trackColor = ProgressIndicatorDefaults.linearTrackColor
        val diameter = 36
        val padding = 6
        DotProgressIndicator(
            progress,
            5,
            color,
            trackColor,
            modifier = Modifier
                .width(((diameter * 5) + padding * 5).dp)
                .height(diameter.dp)
        )
        Text("$progress/5")
    }
}

/**
 * @param [dots] - Amount of filled dots to draw
 * @param [maxDots] - Total amount of dots to draw
 * @param [filledColor] - The color a dot should be when filled, and the color of the small dot in
 * empty dots
 * @param [emptyColor] - The main color of an empty dot
 * @param [modifier] - Must be a modifier with the height and width set
 * @param [radius] - If null, set to the lowest value out of width and height
 * @para [flip] - Whether to flip the direction dots are drawn
 */
@Composable
fun DotProgressIndicator(
    dots: Int,
    maxDots: Int,
    filledColor: Color,
    emptyColor: Color,
    modifier: Modifier,
    radius: Float? = null,
    flip: Boolean = false,
) {
    Canvas(
        modifier = modifier
    ) {
        val rad = radius ?: (size.minDimension / 2)
        val pad = ((size.width - (maxDots * (rad * 2))) / maxDots) + 5
        var jump = if (flip) size.width else 0f
        repeat(dots) {
            drawCircle(
                color = filledColor,
                radius = rad,
                center = Offset(jump + if (flip) -rad else rad, rad)
            )
            if (flip)
                jump -= rad * 2 + pad
            else
                jump += rad * 2 + pad
        }
        repeat(maxDots - dots) {
            drawCircle(
                color = emptyColor,
                radius = rad,
                center = Offset(jump + if (flip) -rad else rad, rad)
            )
            drawCircle(
                color = filledColor,
                radius = max(rad / 5, 4f),
                center = Offset(jump + if (flip) -rad else rad, rad)
            )
            if (flip)
                jump -= rad * 2 + pad
            else
                jump += rad * 2 + pad
        }

    }
}


@Preview(device = "id:pixel_7", showSystemUi = true, showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(device = "id:pixel_7", showSystemUi = true, showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun ShinerProgressIndicatorPreview() {
    ClayCoinTheme {
        Scaffold { iP ->
            Row(modifier = Modifier.padding(iP).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ShinerProgressIndicator(3, 21.35, modifier = Modifier)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemiCircularProgressIndicator(
    progress: () -> Float,
    circlePercent: Float = 0.75f,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
    gapSize: Dp = ProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    radius: Dp = LocalConfiguration.current.screenWidthDp.dp
) {
    CircularProgressIndicator(
        progress = { circlePercent },
        strokeWidth = strokeWidth,
        modifier = Modifier
            .size(radius)
            .then(modifier),
        trackColor = Color(0x000000FF),
        color = trackColor,
        strokeCap = strokeCap,
        gapSize = gapSize
    )
    CircularProgressIndicator(
        progress = { circlePercent * progress() },
        strokeWidth = strokeWidth,
        modifier = Modifier
            .size(radius)
            .then(modifier),
        trackColor = Color(0x000000FF),
        color = color,
        strokeCap = strokeCap,
        gapSize = gapSize
    )
}
