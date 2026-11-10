package com.idealplayer.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object A2Shape {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(24.dp)
    val full = RoundedCornerShape(999.dp)
}

val IdealPlayerShapes = Shapes(
    extraSmall = A2Shape.small,
    small = A2Shape.medium,
    medium = A2Shape.large,
    large = A2Shape.extraLarge,
    extraLarge = A2Shape.extraLarge
)
