package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A practice of 930: the Law Speaker of Iceland recited the whole law in three summers, a third at each Althing, before it was written down. */
val db111Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.PRACTICE,
        section = "Iceland",
        title = "The Law Speaker's recital, a third of the law each summer over three years",
        originalTitle = "Logsogumadur recital",
        author = "The Law Speaker of the Althing",
        year = 930,
        summary = "At the Althing the Law Speaker recited a third of the law each summer, so that the whole code was spoken through once in three years.",
        details = "The recital kept the law in memory until the Althing resolved in 1117 to write it down, and the Icelandic law text Gragas describes the same three-year pattern. The year 930 is the traditional start of the assembly, and the practice is dated to that start rather than to a documented first recital.",
        rare = true,
    ),

)
