package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NanoCyan
import com.example.ui.theme.NanoPurple
import com.example.ui.theme.VoidDark
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    testTagStr: String = "joystick",
    accentColor: Color = NanoCyan,
    onMove: (dx: Float, dy: Float) -> Unit,
    onRelease: () -> Unit = {}
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(size)
            .testTag(testTagStr)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onRelease()
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onRelease()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val maxRadius = size.toPx() / 2f
                        val newOffset = knobOffset + dragAmount
                        val dist = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)

                        knobOffset = if (dist > maxRadius) {
                            val angle = atan2(newOffset.y, newOffset.x)
                            Offset(cos(angle) * maxRadius, sin(angle) * maxRadius)
                        } else {
                            newOffset
                        }

                        val normalizedX = knobOffset.x / maxRadius
                        val normalizedY = knobOffset.y / maxRadius
                        onMove(normalizedX, normalizedY)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.width / 2f

            // Outer Base Ring
            drawCircle(
                color = VoidDark.copy(alpha = 0.7f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.5f),
                radius = radius,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.15f),
                radius = radius * 0.6f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Inner Knob
            val knobCenter = center + knobOffset
            drawCircle(
                color = accentColor,
                radius = radius * 0.35f,
                center = knobCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = radius * 0.15f,
                center = knobCenter
            )
        }
    }
}
