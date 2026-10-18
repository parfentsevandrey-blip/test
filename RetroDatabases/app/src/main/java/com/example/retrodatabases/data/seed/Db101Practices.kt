package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1030: a prince's order that three hundred children of leading families learn to read and write. */
val db101Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.PRACTICE,
        section = "Schooling",
        title = "Yaroslav's school at Novgorod, where three hundred children were set to learn letters",
        originalTitle = "Yaroslav's school at Novgorod",
        author = "Yaroslav the Wise",
        year = 1030,
        summary = "In 1030 Yaroslav the Wise gathered three hundred children of leading families to be taught reading and writing, as the Primary Chronicle records under that year.",
        details = "It is one of the earliest records of schooling in the Rus lands. The entry does not say how long the school lasted.",
        rare = true,
    ),

)
