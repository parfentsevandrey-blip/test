package app.rosa.weather.core.designsystem.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

private val numeralChars = "0123456789\u2212+-.,:°%′ "

/**
 * Sets a value like "5 м/с ЮЗ" as the number at full size and its unit (compass points, Cyrillic)
 * smaller and semibold, so readings scan as numbers first.
 */
fun numeralText(text: String, unitScale: Float = 0.72f): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val numeric = text[i] in numeralChars
        var j = i
        while (j < text.length && (text[j] in numeralChars) == numeric) j++
        val run = text.substring(i, j)
        if (numeric) {
            append(run)
        } else {
            withStyle(SpanStyle(fontFamily = RosaFonts.Manrope, fontWeight = FontWeight.SemiBold, fontSize = unitScale.em)) {
                append(run)
            }
        }
        i = j
    }
}
