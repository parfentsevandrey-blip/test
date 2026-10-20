package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 630 to 640: the Chronicon Paschale, a Greek world chronicle written shortly after 628 and titled for its Easter reckoning, placed at 630. */
val db141Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.TOOL,
        section = "Byzantine",
        title = "The Chronicon Paschale, a Greek world chronicle titled for its Easter reckoning and written shortly after 628",
        originalTitle = "Chronicon Paschale",
        author = "An unidentified Byzantine author",
        year = 630,
        summary = "The Chronicon Paschale is a Greek world chronicle, written shortly after 628, whose title comes from its Easter reckoning.",
        details = "Its record runs to 628, and its author is not identified; the text is known from a damaged manuscript, so 630 is an approximate year for the first form. The Easter reckoning uses a nineteen-year lunar cycle, and the exact date of writing is not settled.",
        rare = true,
    ),

)
