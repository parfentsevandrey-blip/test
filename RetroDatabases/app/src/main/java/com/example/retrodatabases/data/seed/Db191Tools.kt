package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 130 to 140: the Han writing brush and inkstone, placed at 132 for the first year of the Yangjia era. */
val db191Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.TOOL,
        section = "Writing",
        title = "The Han writing brush and inkstone, the pen and ink of scribes on bamboo and silk",
        originalTitle = "Maobi and yan",
        author = "Han scribes",
        year = 132,
        summary = "A Han scribe wrote with a hair-tipped brush dipped in ink ground on an inkstone, on bamboo slips and on silk.",
        details = "The sources do not date the brush or the inkstone, so the year 132 is placed: it is the first year of the Yangjia era, the anchor, and not the date of either tool. Han texts on bamboo slips and silk were written with brushes, and the record gives only what the tool was used for.",
        rare = true,
    ),

)
