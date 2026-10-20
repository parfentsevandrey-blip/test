package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 0 to 10: one placed tool, the Han scribe's knife for trimming and scraping bamboo slips, placed at 6 for the death of Emperor Ping. */
val db204Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.TOOL,
        section = "Writing",
        title = "The Han scribe's knife for trimming and scraping bamboo slips, undated in the sources and placed at 6",
        originalTitle = "Slip knife",
        author = "Han scribes and clerks",
        year = 6,
        summary = "The Han scribe's knife trimmed bamboo slips and scraped away a mistake, and the record is placed at 6, the year Emperor Ping died, not the year the knife was made.",
        details = "The sources do not date the knife, and its use is known from the slips themselves and from the form of the character shan, to delete, which combines the sign for a slip with the knife radical. The year 6 is the anchor, the death of Emperor Ping, and not the date of the making.",
        rare = true,
    ),

)
