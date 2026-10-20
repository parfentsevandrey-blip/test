package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 392: Jerome's On Illustrious Men, a Latin reference list of Christian writers from the apostles to his own day. */
val db165Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.TOOL,
        section = "Reference",
        title = "Jerome's On Illustrious Men, a Latin catalogue of Christian writers",
        originalTitle = "De viris illustribus",
        author = "Jerome",
        year = 392,
        summary = "Jerome wrote On Illustrious Men at Bethlehem in 392 as a short reference book, with a note on the life and works of each writer it lists.",
        details = "The list starts with the apostles and runs down to Jerome's own day, so it is a handbook of Christian authors rather than a history of events. The year 392 is the usual date for its completion.",
        rare = true,
    ),

)
