package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 230 to 240: the shorthand writers whom Origen kept at Caesarea, placed at 231. */
val db181Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "Origen's shorthand writers at Caesarea, who took down his teaching in turns",
        originalTitle = "Tachygraphoi",
        author = "Origen and his patron Ambrose",
        year = 231,
        summary = "Eusebius reports that Origen had shorthand writers at Caesarea take down his teaching, a fast notation of spoken words.",
        details = "The sources do not date this practice; 231 is the year of the anchor, Origen's move to Caesarea, not of the writers' first use. Eusebius says Ambrose supplied seven writers, who took the dictation in turns, so the record is placed rather than dated.",
        rare = true,
    ),

)
