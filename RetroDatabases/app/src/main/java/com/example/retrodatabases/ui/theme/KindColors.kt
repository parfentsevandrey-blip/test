package com.example.retrodatabases.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.retrodatabases.data.Kind

/** Colour of a record kind: the stripe on a card, the badge background and the section header. */
fun Kind.accent(): Color = when (this) {
    Kind.LANGUAGE -> Color(0xFF4DD0E1)
    Kind.SYSTEM -> Color(0xFF7986CB)
    Kind.DEVICE -> Color(0xFFFFB74D)
    Kind.TOOL -> Color(0xFF81C784)
    Kind.PRACTICE -> Color(0xFFBA68C8)
    Kind.GUIDE -> Color(0xFF64B5F6)
    Kind.EXAMPLE -> Color(0xFFFFF176)
    Kind.PROJECT -> Color(0xFFA1887F)
    Kind.FLEX -> Color(0xFFE57373)
    Kind.CROSS -> Color(0xFFF06292)
    Kind.MODEL -> Color(0xFFD4E157)
    Kind.COMPROMISE -> Color(0xFFB0BEC5)
}

/** Converts a #RRGGBB string from the database into a Compose colour. */
fun parseHexColor(hex: String): Color = Color(AndroidColor.parseColor(hex))
