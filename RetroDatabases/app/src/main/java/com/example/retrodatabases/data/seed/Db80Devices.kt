package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1240 to 1250: the Tusi couple, a mechanism of rolling circles that turns a circle into a straight line. */
val db80Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.DEVICE,
        section = "Mechanisms",
        title = "The Tusi couple, two rolling circles that turn a circle into a line",
        originalTitle = "Tusi couple",
        author = "Nasir al-Din al-Tusi",
        year = 1247,
        summary = "Nasir al-Din al-Tusi described the Tusi couple, a small circle rolling inside a larger one so that a point on it moves along a straight line, in his 1247 work on Ptolemy's Almagest.",
        details = "The device first appears in the Tahrir al-Majisti of 1247, which is a revision of Ptolemy's Almagest, and Edward Stewart Kennedy gave it the name Tusi couple in 1966. Copernicus used the device in De revolutionibus of 1543, and how he learned of it is not known.",
        rare = true,
    ),

)
