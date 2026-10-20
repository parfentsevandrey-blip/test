package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 40 to 50: the scrinium, a box in which Roman clerks kept rolls and papers, placed at 41. */
val db200Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.TOOL,
        section = "Records",
        title = "The scrinium, a box for rolls and papers kept by Roman clerks, placed at 41",
        originalTitle = "Scrinium",
        author = "Roman clerks and secretaries",
        year = 41,
        summary = "The scrinium was the box in which Roman clerks kept rolls and papers, and it is placed here at 41, the year of Claudius's accession.",
        details = "Latin writers name the boxes, but the sources do not date any particular one, so the record gives no maker. The year 41 is the placed year of the anchor, not the year of the making, and the box is placed beside the imperial offices of Claudius's reign.",
        rare = true,
    ),

)
